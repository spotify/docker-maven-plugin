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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

import com.spotify.docker.client.AnsiProgressHandler;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static com.google.common.base.CharMatcher.WHITESPACE;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Ordering.natural;
import static com.spotify.docker.Utils.parseImageName;
import static com.spotify.docker.Utils.pushImage;
import static com.typesafe.config.ConfigRenderOptions.concise;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

/**
 * Used to build docker images.
 */
@Mojo(name = "build")
public class BuildMojo extends AbstractDockerMojo {

  /**
   * Directory containing the Dockerfile. If the value is not set, the plugin will generate a
   * Dockerfile using the required baseImage value, plus the optional entryPoint, cmd and maintainer
   * values. If this value is set the plugin will use the Dockerfile in the specified folder.
   */
  @Parameter(property = "dockerDirectory")
  private String dockerDirectory;

  /**
   * Flag to skip docker build, making build goal a no-op. This can be useful when docker:build
   * is bound to package goal, and you want to build a jar but not a container. Defaults to false.
   */
  @Parameter(property = "skipDockerBuild", defaultValue = "false")
  private boolean skipDockerBuild;

  /** Flag to push image after it is built. Defaults to false. */
  @Parameter(property = "pushImage", defaultValue = "false")
  private boolean pushImage;

  /** The maintainer of the image. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerMaintainer")
  private String maintainer;

  /** The base image to use. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerBaseImage")
  private String baseImage;

  /** The entry point of the image. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerEntryPoint")
  private String entryPoint;

  /** The cmd command for the image. Ignored if dockerDirectory is set. */
  @Parameter(property = "dockerCmd")
  private String cmd;

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

  @Override
  protected void execute(DockerClient docker)
      throws MojoExecutionException, GitAPIException,
             IOException, DockerException, InterruptedException {

    if (skipDockerBuild) {
      getLog().info("Skipping docker build");
      return;
    }

    // Put the list of exposed ports into a TreeSet which will remove duplicates and keep them
    // in a sorted order. This is useful when we merge with ports defined in the profile.
    exposesSet = new TreeSet<String>(exposes);
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

    final String destination = Paths.get(buildDirectory, "docker").toString();
    if (dockerDirectory == null) {
      final List<String> copiedPaths = copyResources(destination);
      createDockerFile(destination, copiedPaths);
    } else {
      final Resource resource = new Resource();
      resource.setDirectory(dockerDirectory);
      resources.add(resource);
      copyResources(destination);
    }

    buildImage(docker, destination);
    tagImage(docker);

    DockerBuildInformation buildInfo = new DockerBuildInformation(imageName, getLog());

    // Write image info file
    final Path imageInfoPath = Paths.get(tagInfoFile);
    Files.createDirectories(imageInfoPath.getParent());
    Files.write(imageInfoPath, buildInfo.toJsonBytes());

    if ("docker".equals(mavenProject.getPackaging())) {
      File imageArtifact = createImageArtifact(mavenProject.getArtifact(), buildInfo);
      mavenProject.getArtifact().setFile(imageArtifact);
    }

    if (pushImage) {
      pushImage(docker, imageName, getLog());
    }
  }

  private File createImageArtifact(Artifact mainArtifact, DockerBuildInformation buildInfo)
      throws IOException {
    String fileName =
        MessageFormat.format(
            "{0}-{1}-docker.jar", mainArtifact.getArtifactId(),
            mainArtifact.getVersion());

    File f = Paths.get(buildDirectory, fileName).toFile();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(f))) {
      JarEntry entry = new JarEntry(
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
    for (Map.Entry<String, ConfigValue> entry : envConfig.root().entrySet()) {
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

    // Simple properties
    imageName = get(imageName, profileConfig, "imageName");
    baseImage = get(baseImage, profileConfig, "baseImage");
    entryPoint = get(entryPoint, profileConfig, "entryPoint");
    cmd = get(cmd, profileConfig, "cmd");
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
    }
  }

  private void buildImage(DockerClient docker, String buildDir)
      throws MojoExecutionException, DockerException, IOException, InterruptedException {
    getLog().info("Building image " + imageName);
    docker.build(Paths.get(buildDir), imageName, new AnsiProgressHandler());
    getLog().info("Built " + imageName);
  }

  private void tagImage(final DockerClient docker)
      throws DockerException, InterruptedException {
    final String imageNameWithoutTag = parseImageName(imageName)[0];
    for (final String imageTag : imageTags) {
      if (!isNullOrEmpty(imageTag)){
        getLog().info("Tagging " + imageName + " with " + imageTag);
        docker.tag(imageName, imageNameWithoutTag + ":" + imageTag);
      }
    }
  }

  private void createDockerFile(String directory, List<String> filesToAdd) throws IOException {

    final List<String> commands = newArrayList();
    if (baseImage != null) {
      commands.add("FROM " + baseImage);
    }
    if (maintainer != null) {
      commands.add("MAINTAINER " + maintainer);
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
          for (String arg : args) {
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
    } else {
      commands.add("CMD []");
    }

    for (String file : filesToAdd) {
      commands.add(String.format("ADD %s %s", file, file));
    }

    if (env != null) {
      final List<String> sortedKeys = Ordering.natural().sortedCopy(env.keySet());
      for (String key : sortedKeys) {
        final String value = env.get(key);
        commands.add(String.format("ENV %s %s", key, value));
      }
    }

    if (exposesSet.size() > 0) {
      // The values will be sorted with no duplicated since exposesSet is a TreeSet
      commands.add("EXPOSE " + Joiner.on(" ").join(exposesSet));
    }

    // this will overwrite an existing file
    Files.createDirectories(Paths.get(directory));
    Files.write(Paths.get(directory, "Dockerfile"), commands, UTF_8);
  }

  private List<String> copyResources(String destination) throws IOException {

    final List<String> allCopiedPaths = newArrayList();

    for (Resource resource : resources) {
      final File source = new File(resource.getDirectory());
      final List<String> includes = resource.getIncludes();
      final List<String> excludes = resource.getExcludes();
      final DirectoryScanner scanner = new DirectoryScanner();
      scanner.setBasedir(source);
      // must pass null if includes/excludes is empty to get default filters.
      // passing zero length array forces it to have no filters at all.
      scanner.setIncludes(includes.isEmpty() ? null
                                             : includes.toArray(new String[includes.size()]));
      scanner.setExcludes(excludes.isEmpty() ? null
                                             : excludes.toArray(new String[excludes.size()]));
      scanner.scan();

      final String[] includedFiles = scanner.getIncludedFiles();
      if (includedFiles.length == 0) {
        getLog().info("No resources will be copied, no files match specified patterns");
      }

      final List<String> copiedPaths = newArrayList();

      for (String included : scanner.getIncludedFiles()) {
        final Path sourcePath = Paths.get(resource.getDirectory(), included);
        final String targetPath = resource.getTargetPath() == null ? "" : resource.getTargetPath();
        final Path destPath = Paths.get(destination, targetPath, included);
        getLog().info(String.format("Copying %s -> %s", sourcePath, destPath));
        // ensure all directories exist because copy operation will fail if they don't
        Files.createDirectories(destPath.getParent());
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(destPath, FileTime.fromMillis(0));
        // file location relative to docker directory, used later to generate Dockerfile
        final Path relativePath = Paths.get(targetPath, included);
        copiedPaths.add(relativePath.toString());
      }

      // The list of included files returned from DirectoryScanner can be in a different order
      // each time. This causes the ADD statements in the generated Dockerfile to appear in a
      // different order. We want to avoid this so each run of the plugin always generates the same
      // Dockerfile, which also makes testing easier. Sort the list of paths for each resource
      // before adding it to the allCopiedPaths list. This way we follow the ordering of the
      // resources in the pom, while making sure all the paths of each resource are always in the
      // same order.
      Collections.sort(copiedPaths);
      allCopiedPaths.addAll(copiedPaths);
    }

    return allCopiedPaths;
  }
}
