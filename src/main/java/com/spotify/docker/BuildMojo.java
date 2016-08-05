/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.docker;

import com.spotify.docker.client.AnsiProgressHandler;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static com.spotify.docker.Utils.parseImageName;
import static com.spotify.docker.Utils.pushImage;
import static com.spotify.docker.Utils.pushImageTag;
import static com.spotify.docker.Utils.writeImageInfoFile;

import static com.google.common.base.CharMatcher.WHITESPACE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Ordering.natural;
import static com.typesafe.config.ConfigRenderOptions.concise;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

/**
 * Used to build docker images.
 */
@Mojo(name = "build")
public class BuildMojo extends AbstractDockerMojo {

  /**
   * Default copy includes.
   */
  private static final String[] DEFAULT_INCLUDES = {"**/**"};

  /**
   * The Unix separator character.
   */
  private static final char UNIX_SEPARATOR = '/';

  /**
   * The Windows separator character.
   */
  private static final char WINDOWS_SEPARATOR = '\\';

  /**
   * Json Object Mapper to encode arguments map 
   */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Check if the resource path is relative.
   *
   * @param resource the resource
   * @return true if the resource target path is relative, false otherwise
   */
  private static boolean isAbsolute(Resource resource) {
    return resource.getTargetPath() != null && Paths.get(resource.getTargetPath()).isAbsolute();
  }

  /**
   * Convert target {@link Path} relative to root.
   *
   * @param target the target path
   * @return if {@code target} is relative then {@code target} otherwise the relative path
   */
  private static Path relativeToRoot(Path target) {
    return target.isAbsolute() ? target.getRoot().relativize(target) : target;
  }

  /**
   * Convert {@link Resource} target path to {@link Path} relative to root.
   *
   * @param resource the resource
   * @return the relative path
   */
  private static Path relativeToRoot(Resource resource) {
    return isAbsolute(resource) ? relativeToRoot(Paths.get(targetPath(resource)))
                                : Paths.get(targetPath(resource));
  }

  /**
   * Read the target path from the resource.
   *
   * @param resource the resource
   * @return empty string if target path is null, otherwise the target path is returned
   */
  private static String targetPath(Resource resource) {
    return resource.getTargetPath() == null ? "" : resource.getTargetPath();
  }

  /**
   * Ordering utility class pertaining to path strings.
   */
  private static final class PathOrdering {

    private static final CharMatcher PATH_MATCHER = CharMatcher.is(File.separatorChar);

    /**
     * An {@link Ordering} based on {@link #depthFirstNaturalOrder()} where paths are ordered
     * alphabetically but with the deepest ordered first.
     *
     * @return the ordering implementation
     */
    static Ordering<String> depthFirstNatural() {
      return Ordering.from(depthFirstNaturalOrder());
    }

    /**
     * A {@link Comparator} where paths are ordered alphabetically but with the deepest ordered
     * first.
     *
     * @return the comparator implementation
     */
    static Comparator<String> depthFirstNaturalOrder() {
      return new Comparator<String>() {
        // cache segment count for efficiency
        private final LoadingCache<String, Integer> segmentCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<String, Integer>() {
              @Override
              public Integer load(@Nonnull String key) throws Exception {
                return PATH_MATCHER.countIn(key);
              }
            });

        @Override
        public int compare(String o1, String o2) {
          final int depthFirst = Ints.compare(
              segmentCache.getUnchecked(o2), segmentCache.getUnchecked(o1)
          );
          return depthFirst == 0 ? String.CASE_INSENSITIVE_ORDER.compare(o1, o2)
                                 : depthFirst;
        }
      };
    }
  }

  /**
   * Predicate that matches if the supplied input is relative to the root path.
   */
  private static class RelativeToRootPredicate implements Predicate<String> {
    private final Path rootPath;

    /**
     * Constructs a new predicate with the provided root path.
     *
     * @param rootPath the root path
     */
    RelativeToRootPredicate(Path rootPath) {
      this.rootPath = checkNotNull(rootPath, "root path must not be null");
    }

    @Override
    public boolean apply(String input) {
      return Paths.get(input).startsWith(rootPath);
    }
  }

  /**
   * Map input path to unix path format.
   */
  private static class SeparatorsToUnixTransform implements Function<String, String> {
    @Override
    public String apply(String input) {
      return separatorsToUnix(input);
    }
  }

  /**
   * The maven resources filtering component, to handle copying of files to docker directory with
   * filtering.
   */
  @Component(role = MavenResourcesFiltering.class, hint = "default")
  protected MavenResourcesFiltering mavenResourcesFiltering;

  /**
   * Directory containing the Dockerfile. If the value is not set, the plugin will generate a
   * Dockerfile using the required baseImage value, plus the optional entryPoint, cmd and maintainer
   * values. If this value is set the plugin will use the Dockerfile in the specified folder.
   */
  @Parameter(property = "dockerDirectory")
  private String dockerDirectory;

  /**
   * The character encoding scheme to be applied when filtering resources.
   */
  @Parameter(defaultValue = "${project.build.sourceEncoding}", readonly = true)
  protected String encoding;

  /**
   * Flag to skip docker build, making build goal a no-op. This can be useful when docker:build
   * is bound to package goal, and you want to build a jar but not a container. Defaults to false.
   */
  @Parameter(property = "skipDockerBuild", defaultValue = "false")
  private boolean skipDockerBuild;

  /**
   * Flag to attempt to pull base images even if older images exists locally. Sends the equivalent
   * of `--pull=true` to Docker daemon when building the image.
   */
  @Parameter(property = "pullOnBuild", defaultValue = "false")
  private boolean pullOnBuild;

  /** Set to true to pass the `--no-cache` flag to the Docker daemon when building an image. */
  @Parameter(property = "noCache", defaultValue = "false")
  private boolean noCache;

  /** Flag to push image after it is built. Defaults to false. */
  @Parameter(property = "pushImage", defaultValue = "false")
  private boolean pushImage;

  /** Flag to push image using their tags after it is built. Defaults to false. */
  @Parameter(property = "pushImageTag", defaultValue = "false")
  private boolean pushImageTag;

  /** Flag to use force option while tagging. Defaults to false. */
  @Parameter(property = "forceTags", defaultValue = "false")
  private boolean forceTags;

  /** The maintainer of the image. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerMaintainer")
  private String maintainer;

  /** The base image to use. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerBaseImage")
  private String baseImage;

  /** The entry point of the image. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerEntryPoint")
  private String entryPoint;

  /** The volumes for the image */
  @Parameter(property = "dockerVolumes")
  private String[] volumes;

  /** The labels for the image */
  @Parameter(property = "dockerLabels")
  private String[] labels;

  /** The cmd command for the image. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerCmd")
  private String cmd;

  /** The workdir for the image. Ignored if dockerDirectory is set */
  @Parameter(property = "workdir")
  private String workdir;

  /** The user for the image. Ignored if dockerDirectory is set */
  @Parameter(property = "user")
  private String user;

  /**
   * The run commands for the image.
   */
  @Parameter(property = "dockerRuns")
  private List<String> runs;

  private List<String> runList;

  /** Flag to squash all run commands into one layer. Defaults to false. */
  @Parameter(property = "squashRunCommands", defaultValue = "false")
  private boolean squashRunCommands;

  /** All resources will be copied to this directory before building the image. */
  @Parameter(property = "project.build.directory")
  protected String buildDirectory;

  @Parameter(property = "dockerBuildProfile")
  private String profile;

  /**
   * Path to JSON file to write when tagging images.
   * Default is ${project.build.testOutputDirectory}/image_info.json
   */
  @Parameter(property = "tagInfoFile",
      defaultValue = "${project.build.testOutputDirectory}/image_info.json")
  protected String tagInfoFile;

  /**
   * If specified as true, a tag will be generated consisting of the first 7 characters of the most
   * recent git commit ID, resulting in something like <tt>image:df8e8e6</tt>. If there are any
   * changes not yet committed, the string '.DIRTY' will be appended to the end. Note, if a tag is
   * explicitly specified in the <tt>newName</tt> parameter, this flag will be ignored.
   */
  @Parameter(property = "useGitCommitId", defaultValue = "false")
  private boolean useGitCommitId;

  /**
   * Resources to include in the build. Specify resources by using the standard resource elements as
   * defined in the <a href="http://maven.apache.org/pom.html#Resources">resources</a> section in
   * the pom reference. If dockerDirectory is not set, the <tt>targetPath</tt> value is the location
   * in the container where the resource should be copied to. The value is relative to '<tt>/</tt>'
   * in the container, and defaults to '<tt>.</tt>'. If dockerDirectory is set, <tt>targetPath</tt>
   * is relative to the dockerDirectory, and defaults to '<tt>.</tt>'. In that case, the Dockerfile
   * can copy the resources into the container using the ADD instruction.
   */
  @Parameter(property = "dockerResources")
  private List<Resource> resources;

  /** Built image will be given this name. */
  @Parameter(property = "dockerImageName")
  private String imageName;

  /** Additional tags to tag the image with. */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @Parameter(property = "dockerImageTags")
  private List<String> imageTags;

  @Parameter(property = "dockerDefaultBuildProfile")
  private String defaultProfile;

  @Parameter(property = "dockerEnv")
  private Map<String, String> env;

  @Parameter(property = "dockerExposes")
  private List<String> exposes;

  private Set<String> exposesSet;

  @Parameter(defaultValue = "${project}")
  private MavenProject mavenProject;

  @Parameter(property = "dockerBuildArgs")
  private Map<String, String> buildArgs;  
  
  private PluginParameterExpressionEvaluator expressionEvaluator;

  public BuildMojo() {
    this(null);
  }

  public BuildMojo(final String defaultProfile) {
    this.defaultProfile = defaultProfile;
  }

  public String getImageName() {
    return imageName;
  }

  public boolean getPushImage() {
    return pushImage;
  }

  public boolean getPushImageTag() {
    return pushImageTag;
  }

  public boolean getForceTags() {
    return forceTags;
  }

  @Override
  protected void execute(final DockerClient docker)
      throws MojoExecutionException, GitAPIException, IOException, DockerException,
             InterruptedException {

    if (skipDockerBuild) {
      getLog().info("Skipping docker build");
      return;
    }

    // Put the list of exposed ports into a TreeSet which will remove duplicates and keep them
    // in a sorted order. This is useful when we merge with ports defined in the profile.
    exposesSet = Sets.newTreeSet(exposes);
    if (runs != null) {
      runList = Lists.newArrayList(runs);
    }
    expressionEvaluator = new PluginParameterExpressionEvaluator(session, execution);

    final Git git = new Git();
    final String commitId = git.isRepository() ? git.getCommitId() : null;

    if (commitId == null) {
      final String errorMessage =
          "Not a git repository, cannot get commit ID. Make sure git repository is initialized.";
      if (useGitCommitId || ((imageName != null) && imageName.contains("${gitShortCommitId}"))) {
        throw new MojoExecutionException(errorMessage);
      } else {
        getLog().debug(errorMessage);
      }
    } else {
      // Put the git commit id in the project properties. Image names may contain
      // ${gitShortCommitId} in which case we want to fill in the actual value using the
      // expression evaluator. We will do that once here for image names loaded from the pom,
      // and again in the loadProfile method when we load values from the profile.
      mavenProject.getProperties().put("gitShortCommitId", commitId);
      if (imageName != null) {
        imageName = expand(imageName);
      }
      if (baseImage != null) {
        baseImage = expand(baseImage);
      }
    }

    loadProfile();
    validateParameters();

    final String[] repoTag = parseImageName(imageName);
    final String repo = repoTag[0];
    final String tag = repoTag[1];

    if (useGitCommitId) {
      if (tag != null) {
        getLog().warn("Ignoring useGitCommitId flag because tag is explicitly set in image name ");
      } else if (commitId == null) {
        throw new MojoExecutionException(
            "Cannot tag with git commit ID because directory not a git repo");
      } else {
        imageName = repo + ":" + commitId;
      }
    }
    mavenProject.getProperties().put("imageName", imageName);

    final String destination = getDestination();

    if (dockerDirectory == null) {
      final List<String> copiedPaths = copyResources(destination);
      createDockerFile(destination, copiedPaths);
    } else {
      final Resource resource = new Resource();
      resource.setDirectory(dockerDirectory);
      resources.add(resource);
      copyResources(destination);
    }

    buildImage(docker, destination, buildParams());
    tagImage(docker, forceTags);

    final DockerBuildInformation buildInfo = new DockerBuildInformation(imageName, getLog());

    if ("docker".equals(mavenProject.getPackaging())) {
      final File imageArtifact = createImageArtifact(mavenProject.getArtifact(), buildInfo);
      mavenProject.getArtifact().setFile(imageArtifact);
    }

    // Push specific tags specified in pom rather than all images
    if (pushImageTag) {
      pushImageTag(docker, imageName, imageTags, getLog());
    }

    if (pushImage) {
      pushImage(docker, imageName, getLog(), buildInfo, getRetryPushCount(), getRetryPushTimeout());
    }

    // Write image info file
    writeImageInfoFile(buildInfo, tagInfoFile);
  }

  private String getDestination() {
    return Paths.get(buildDirectory, "docker").toString();
  }

  private File createImageArtifact(final Artifact mainArtifact,
                                   final DockerBuildInformation buildInfo) throws IOException {
    final String fileName = MessageFormat.format(
        "{0}-{1}-docker.jar", mainArtifact.getArtifactId(), mainArtifact.getVersion());

    final File f = Paths.get(buildDirectory, fileName).toFile();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(f))) {
      final JarEntry entry = new JarEntry(
          MessageFormat.format("META-INF/docker/{0}/{1}/image-info.json",
                               mainArtifact.getGroupId(), mainArtifact.getArtifactId()));
      out.putNextEntry(entry);
      out.write(buildInfo.toJsonBytes());
    }

    return f;
  }

  private void loadProfile() throws MojoExecutionException {

    final Config config = ConfigFactory.load();

    defaultProfile = get(defaultProfile, config, "docker.build.defaultProfile");

    if (profile == null) {
      if (defaultProfile == null) {
        getLog().debug("Not using any build profile");
        return;
      } else {
        getLog().info("Using default build profile: " + defaultProfile);
        profile = defaultProfile;
      }
    } else {
      getLog().info("Using build profile: " + profile);
    }

    Config profiles;
    try {
      profiles = config.getConfig("docker.build.profiles");
    } catch (ConfigException.Missing e) {
      profiles = ConfigFactory.empty();
    }

    // Profile selection
    final Config profileConfig;
    try {
      profileConfig = profiles.getConfig(profile);
    } catch (ConfigException.Missing e) {
      getLog().error("Docker build profile not found: " + profile);
      getLog().error("Docker build profiles available:");
      for (final String name : natural().sortedCopy(profiles.root().keySet())) {
        getLog().error(name);
      }
      throw new MojoExecutionException("Docker build profile not found: " + profile);
    }

    getLog().info("Build profile: " + profile);
    getLog().info(profileConfig.root().render(concise().setJson(true).setFormatted(true)));

    // Resources
    List<? extends Config> resourceConfigs = emptyList();
    try {
      resourceConfigs = profileConfig.getConfigList("resources");
    } catch (ConfigException.Missing ignore) {
    }
    for (final Config resourceConfig : resourceConfigs) {
      final Resource resource = new Resource();
      try {
        resource.setDirectory(expand(resourceConfig.getString("directory")));
      } catch (ConfigException.Missing e) {
        throw new MojoExecutionException("Invalid resource config, missing directory.", e);
      }
      try {
        resource.setTargetPath(expand(resourceConfig.getString("targetPath")));
      } catch (ConfigException.Missing ignore) {
      }
      try {
        final List<String> includes = resourceConfig.getStringList("includes");
        final List<String> expanded = newArrayList();
        for (final String raw : includes) {
          expanded.add(expand(raw));
        }
        resource.setIncludes(expanded);
      } catch (ConfigException.Missing ignore) {
      }
      resources.add(resource);
    }

    // Environment variables
    Config envConfig = ConfigFactory.empty();
    try {
      envConfig = profileConfig.getConfig("env");
    } catch (ConfigException.Missing ignore) {
    }
    if (env == null) {
      env = Maps.newHashMap();
    }
    for (final Map.Entry<String, ConfigValue> entry : envConfig.root().entrySet()) {
      final String key = expand(entry.getKey());
      if (!env.containsKey(key)) {
        env.put(key, expand(entry.getValue().unwrapped().toString()));
      }
    }

    // Exposed ports
    List<String> exposesList = emptyList();
    try {
      exposesList = profileConfig.getStringList("exposes");
    } catch (ConfigException.Missing ignore) {
    }
    for (final String raw : exposesList) {
      exposesSet.add(expand(raw));
    }

    try {
      runList.addAll(profileConfig.getStringList("runs"));
    } catch (ConfigException.Missing ignore) {
    }

    // Simple properties
    imageName = get(imageName, profileConfig, "imageName");
    baseImage = get(baseImage, profileConfig, "baseImage");
    entryPoint = get(entryPoint, profileConfig, "entryPoint");
    cmd = get(cmd, profileConfig, "cmd");
    workdir = get(workdir, profileConfig, "workdir");
    user = get(user, profileConfig, "user");
  }

  private String get(final String override, final Config config, final String path)
      throws MojoExecutionException {
    if (override != null) {
      return override;
    }
    try {
      return expand(config.getString(path));
    } catch (ConfigException.Missing e) {
      return null;
    }
  }

  private String expand(final String raw) throws MojoExecutionException {
    final Object value;
    try {
      value = expressionEvaluator.evaluate(raw);
    } catch (ExpressionEvaluationException e) {
      throw new MojoExecutionException("Expression evaluation failed: " + raw, e);
    }

    if (value == null) {
      throw new MojoExecutionException("Undefined expression: " + raw);
    }

    return value.toString();
  }

  private void validateParameters() throws MojoExecutionException {
    if (dockerDirectory == null) {
      if (baseImage == null) {
        throw new MojoExecutionException("Must specify baseImage if dockerDirectory is null");
      }
    } else {
      if (baseImage != null) {
        getLog().warn("Ignoring baseImage because dockerDirectory is set");
      }
      if (maintainer != null) {
        getLog().warn("Ignoring maintainer because dockerDirectory is set");
      }
      if (entryPoint != null) {
        getLog().warn("Ignoring entryPoint because dockerDirectory is set");
      }
      if (cmd != null) {
        getLog().warn("Ignoring cmd because dockerDirectory is set");
      }
      if (runList != null && !runList.isEmpty()) {
        getLog().warn("Ignoring run because dockerDirectory is set");
      }
      if (workdir != null) {
        getLog().warn("Ignoring workdir because dockerDirectory is set");
      }
      if (user != null) {
        getLog().warn("Ignoring user because dockerDirectory is set");
      }
    }
  }

  private void buildImage(final DockerClient docker, final String buildDir,
                          final DockerClient.BuildParam... buildParams)
      throws MojoExecutionException, DockerException, IOException, InterruptedException {
    getLog().info("Building image " + imageName);
    docker.build(Paths.get(buildDir), imageName, new AnsiProgressHandler(), buildParams);
    getLog().info("Built " + imageName);
  }

  private void tagImage(final DockerClient docker, boolean forceTags)
      throws DockerException, InterruptedException, MojoExecutionException {
    final String imageNameWithoutTag = parseImageName(imageName)[0];
    for (final String imageTag : imageTags) {
      if (!isNullOrEmpty(imageTag)){
        getLog().info("Tagging " + imageName + " with " + imageTag);
        docker.tag(imageName, imageNameWithoutTag + ":" + imageTag, forceTags);
      }
    }
  }

  private void createDockerFile(final String directory, final List<String> filesToAdd)
      throws IOException {

    final List<String> commands = newArrayList();
    if (baseImage != null) {
      commands.add("FROM " + baseImage);
    }
    if (maintainer != null) {
      commands.add("MAINTAINER " + maintainer);
    }

    if (env != null) {
      final List<String> sortedKeys = Ordering.natural().sortedCopy(env.keySet());
      for (final String key : sortedKeys) {
        final String value = env.get(key);
        commands.add(String.format("ENV %s %s", key, value));
      }
    }

    if (workdir != null) {
      commands.add("WORKDIR " + workdir);
    }

    for (final String file : filesToAdd) {
      // The dollar sign in files has to be escaped because docker interprets it as variable
      commands.add(
              String.format("ADD %s %s", file.replaceAll("\\$", "\\\\\\$"), normalizeDest(file)));
    }

    if (runList != null && !runList.isEmpty()) {
      if (squashRunCommands) {
        commands.add("RUN " + Joiner.on(" &&\\\n\t").join(runList));
      } else {
        for (final String run : runList) {
          commands.add("RUN " + run);
        }
      }
    }

    if (exposesSet.size() > 0) {
      // The values will be sorted with no duplicated since exposesSet is a TreeSet
      commands.add("EXPOSE " + Joiner.on(" ").join(exposesSet));
    }

    if (user != null) {
      commands.add("USER " + user);
    }

    if (entryPoint != null) {
      commands.add("ENTRYPOINT " + entryPoint);
    }
    if (cmd != null) {
      // TODO(dano): we actually need to check whether the base image has an entrypoint
      if (entryPoint != null) {
        // CMD needs to be a list of arguments if ENTRYPOINT is set.
        if (cmd.startsWith("[") && cmd.endsWith("]")) {
          // cmd seems to be an argument list, so we're good
          commands.add("CMD " + cmd);
        } else {
          // cmd does not seem to be an argument list, so try to generate one.
          final List<String> args = ImmutableList.copyOf(
              Splitter.on(WHITESPACE).omitEmptyStrings().split(cmd));
          final StringBuilder cmdBuilder = new StringBuilder("[");
          for (final String arg : args) {
            cmdBuilder.append('"').append(arg).append('"');
          }
          cmdBuilder.append(']');
          final String cmdString = cmdBuilder.toString();
          commands.add("CMD " + cmdString);
          getLog().warn("Entrypoint provided but cmd is not an explicit list. Attempting to " +
                        "generate CMD string in the form of an argument list.");
          getLog().warn("CMD " + cmdString);
        }
      } else {
        // no ENTRYPOINT set so use cmd verbatim
        commands.add("CMD " + cmd);
      }
    }

    // Add VOLUME's to dockerfile
    if (volumes != null) {
      for (final String volume : volumes) {
        commands.add("VOLUME " + volume);
      }
    }

    // Add LABEL's to dockerfile
    if (labels != null) {
      for (final String label : labels) {
        commands.add("LABEL " + label);
      }
    }

    getLog().debug("Writing Dockerfile:" + System.lineSeparator() +
                   Joiner.on(System.lineSeparator()).join(commands));

    // this will overwrite an existing file
    Files.createDirectories(Paths.get(directory));
    Files.write(Paths.get(directory, "Dockerfile"), commands, UTF_8);
  }

  private String normalizeDest(final String filePath) {
    // if the path is a file (i.e. not a directory), remove the last part of the path so that we
    // end up with:
    //   ADD foo/bar.txt foo/
    // instead of
    //   ADD foo/bar.txt foo/bar.txt
    // This is to prevent issues when adding tar.gz or other archives where Docker will
    // automatically expand the archive into the "dest", so
    //  ADD foo/x.tar.gz foo/x.tar.gz
    // results in x.tar.gz being expanded *under* the path foo/x.tar.gz/stuff...
    final File file = new File(filePath);

    String dest;
    // need to know the path relative to destination to test if it is a file or directory,
    // but only remove the last part of the path if there is a parent (i.e. don't remove a
    // parent path segment from "file.txt")
    if (new File(getDestination(), filePath).isFile()) {
      if (file.getParent() != null) {
        // remove file part of path
        dest = separatorsToUnix(file.getParent());
        if (!dest.endsWith("/")) {
          dest = dest + "/";
        }
      } else {
        // working with a simple "ADD file.txt"
        dest = ".";
      }
    } else {
      dest = separatorsToUnix(file.getPath());
    }

    return dest;
  }

  private List<String> copyResources(String destination)
      throws IOException, MojoExecutionException {
    final LinkedList<Resource> copies = Lists.newLinkedList();
    for (final Resource resource : resources) {
      copies.add(resource.clone());
    }
    final ImmutableList<Resource> relativeResources = ImmutableList.copyOf(copies);

    // convert resources relative
    for (final Resource resource : relativeResources) {
      final String targetPath = resource.getTargetPath();
      if (targetPath != null) {
        resource.setTargetPath(
            Paths.get(destination).toAbsolutePath().resolve(relativeToRoot(resource)).toString()
        );
      }
    }

    final List<String> combinedFilters = ImmutableList.of();
    final List<String> nonFilteredFileExtensions = ImmutableList.of();
    final MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
        relativeResources, new File(destination), mavenProject, encoding, combinedFilters,
        Collections.<String>emptyList(), session
    );

    mavenResourcesExecution.setInjectProjectBuildFilters(false);
    mavenResourcesExecution.setFilterFilenames(true);

    // Note, these are properties that the maven resources plugin configures that may be useful to
    // implement as properties on this mojo at some point.

    // mavenResourcesExecution.setEscapeString(escapeString);
    // mavenResourcesExecution.setOverwrite(overwrite);
    // mavenResourcesExecution.setIncludeEmptyDirs(includeEmptyDirs);
    // mavenResourcesExecution.setSupportMultiLineFiltering( supportMultiLineFiltering );
    // mavenResourcesExecution.setAddDefaultExcludes( addDefaultExcludes );

    // Handle subject of MRESOURCES-99
    // Properties additionalProperties = addSeveralSpecialProperties();
    // mavenResourcesExecution.setAdditionalProperties( additionalProperties );

    // if these are NOT set, just use the defaults, which are '${*}' and '@'.
    // mavenResourcesExecution.setDelimiters( delimiters, useDefaultDelimiters );

    if (nonFilteredFileExtensions != null) {
      mavenResourcesExecution.setNonFilteredFileExtensions(nonFilteredFileExtensions);
    }

    Files.createDirectories(Paths.get(destination));
    try {
      mavenResourcesFiltering.filterResources(mavenResourcesExecution);
    } catch (MavenFilteringException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }

    final DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(destination);
    scanner.scan();
    final String[] filesAdded = scanner.getIncludedFiles();

    final List<String> possibleFileMatches = newArrayList(filesAdded);
    final List<String> addedPaths = Lists.newLinkedList();

    // copied whole directories
    final List<Resource> partialResources = Lists.newLinkedList();
    for (final Resource resource : resources) {
      final boolean copyWholeDir =
          resource.getIncludes().isEmpty()
          && resource.getExcludes().isEmpty()
          && resource.getTargetPath() != null;

      if (copyWholeDir) {
        final Path relativeToRoot = relativeToRoot(resource);

        final List<String> matchesWholeDir = FluentIterable.from(possibleFileMatches).filter(
            new RelativeToRootPredicate(relativeToRoot)
        ).toList();

        if (!possibleFileMatches.isEmpty()) {
          addedPaths.add(resource.getTargetPath());
          for (final String match : matchesWholeDir) {
            possibleFileMatches.remove(match);
          }
        }
      } else {
        partialResources.add(resource);
      }
    }

    // copied filtered set of files
    for (final Resource resource : partialResources) {
      final Path relativeToRoot = relativeToRoot(resource);

      // setup scanner
      final DirectoryScanner scan = new DirectoryScanner();
      scan.setBasedir(Paths.get(destination).resolve(relativeToRoot).toString());
      if (!resource.getIncludes().isEmpty()) {
        scan.setIncludes(resource.getIncludes().toArray(new String[resource.getIncludes().size()]));
      } else {
        scan.setIncludes(DEFAULT_INCLUDES);
      }

      if (!resource.getExcludes().isEmpty()) {
        scan.setExcludes(resource.getExcludes().toArray(new String[resource.getIncludes().size()]));
      }

      if (mavenResourcesExecution.isAddDefaultExcludes()) {
        scan.addDefaultExcludes();
      }

      scan.scan();

      for (final String file : scan.getIncludedFiles()) {
        final String addedFile = Paths.get(targetPath(resource), file).toString();
        final String relativeAddedFile = relativeToRoot.resolve(file).toString();
        if (possibleFileMatches.contains(relativeAddedFile) && !addedPaths.contains(addedFile)) {
          addedPaths.add(addedFile);
          possibleFileMatches.remove(addedFile);
        }
      }
    }

    // The list of included files returned from DirectoryScanner can be in a different order
    // each time. This causes the ADD statements in the generated Dockerfile to appear in a
    // different order. We want to avoid this so each run of the plugin always generates the same
    // Dockerfile, which also makes testing easier. Sort the list of paths for each resource
    // before adding it to the allCopiedPaths list. This way we follow the ordering of the
    // resources in the pom, while making sure all the paths of each resource are always in the
    // same order.
    return FluentIterable.from(addedPaths)
        .transform(new SeparatorsToUnixTransform())
        .toSortedList(PathOrdering.depthFirstNatural());
  }

  /**
   * Converts all separators to the Unix separator of forward slash.
   *
   * @param path  the path to be changed, null ignored
   * @return the updated path
   */
  public static String separatorsToUnix(final String path) {
    if (path == null || path.indexOf(WINDOWS_SEPARATOR) == -1) {
      return path;
    }
    return path.replace(WINDOWS_SEPARATOR, UNIX_SEPARATOR);
  }

  private DockerClient.BuildParam[] buildParams() 
    throws UnsupportedEncodingException, JsonProcessingException {
    final List<DockerClient.BuildParam> buildParams = Lists.newArrayList();
    if (pullOnBuild) {
      buildParams.add(DockerClient.BuildParam.pullNewerImage());
    }
    if (noCache) {
      buildParams.add(DockerClient.BuildParam.noCache());
    }
    if (!buildArgs.isEmpty()) {
      buildParams.add(DockerClient.BuildParam.create("buildargs", 
        URLEncoder.encode(OBJECT_MAPPER.writeValueAsString(buildArgs), "UTF-8")));
    }
    return buildParams.toArray(new DockerClient.BuildParam[buildParams.size()]);
  }
}
