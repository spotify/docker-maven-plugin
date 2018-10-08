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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerCertificatesStore;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.auth.ConfigFileRegistryAuthSupplier;
import com.spotify.docker.client.auth.FixedRegistryAuthSupplier;
import com.spotify.docker.client.auth.MultiRegistryAuthSupplier;
import com.spotify.docker.client.auth.RegistryAuthSupplier;
import com.spotify.docker.client.auth.gcr.ContainerRegistryAuthSupplier;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.messages.RegistryAuth;
import com.spotify.docker.client.messages.RegistryConfigs;
import com.spotify.docker.client.shaded.com.google.common.base.Optional;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

abstract class AbstractDockerMojo extends AbstractMojo {

  @Component(role = MavenSession.class)
  protected MavenSession session;

  @Component(role = MojoExecution.class)
  protected MojoExecution execution;

  /**
   * The system settings for Maven. This is the instance resulting from
   * merging global and user-level settings files.
   */
  @Component
  private Settings settings;

  /**
   * https://issues.apache.org/jira/browse/MNG-4384
   */
  @Component(role = SecDispatcher.class, hint = "mng-4384")
  private SecDispatcher secDispatcher;

  /**
   * URL of the docker host as specified in pom.xml.
   */
  @Parameter(property = "dockerHost")
  private String dockerHost;

  @Parameter(property = "dockerCertPath")
  private String dockerCertPath;

  @Parameter(property = "serverId")
  private String serverId;

  @Parameter(property = "registryUrl")
  private String registryUrl;

  /**
   * Number of retries for failing pushes, defaults to 5.
   */
  @Parameter(property = "retryPushCount", defaultValue = "5")
  private int retryPushCount;

  /**
   * Retry timeout for failing pushes, defaults to 10 seconds.
   */
  @Parameter(property = "retryPushTimeout", defaultValue = "10000")
  private int retryPushTimeout;

  /**
   * Flag to skip docker goal, making goal a no-op. This can be useful when docker goal
   * is bound to Maven phase, and you want to skip Docker command. Defaults to false.
   */
  @Parameter(property = "skipDocker", defaultValue = "false")
  private boolean skipDocker;

  /**
   * Flag to skip docker push, making push goal a no-op. This can be useful when docker:push
   * is bound to deploy goal, and you want to deploy a jar but not a container. Defaults to false.
   */
  @Parameter(property = "skipDockerPush", defaultValue = "false")
  private boolean skipDockerPush;

  public int getRetryPushTimeout() {
    return retryPushTimeout;
  }

  public int getRetryPushCount() {
    return retryPushCount;
  };

  public boolean isSkipDocker() {
    return skipDocker;
  }

  public boolean isSkipDockerPush() {
    return skipDockerPush;
  }

  @Override
  public void execute() throws MojoExecutionException {

    if (skipDocker) {
      getLog().info("Skipping docker goal");
      return;
    }

    try (DockerClient client = buildDockerClient()) {
      execute(client);
    } catch (Exception e) {
      throw new MojoExecutionException("Exception caught", e);
    }
  }

  protected DefaultDockerClient.Builder getBuilder() throws DockerCertificateException {
    return DefaultDockerClient.fromEnv()
      .readTimeoutMillis(0);
  }

  protected DockerClient buildDockerClient() throws MojoExecutionException {

    final DefaultDockerClient.Builder builder;
    try {
      builder = getBuilder();

      final String dockerHost = rawDockerHost();
      if (!isNullOrEmpty(dockerHost)) {
        builder.uri(dockerHost);
      }
      final Optional<DockerCertificatesStore> certs = dockerCertificates();
      if (certs.isPresent()) {
        builder.dockerCertificates(certs.get());
      }
    } catch (DockerCertificateException ex) {
      throw new MojoExecutionException("Cannot build DockerClient due to certificate problem", ex);
    }

    builder.registryAuthSupplier(authSupplier());

    return builder.build();
  }

  protected abstract void execute(final DockerClient dockerClient) throws Exception;

  protected String rawDockerHost() {
    return dockerHost;
  }

  protected Optional<DockerCertificatesStore> dockerCertificates()
      throws DockerCertificateException {
    if (!isNullOrEmpty(dockerCertPath)) {
      return DockerCertificates.builder()
        .dockerCertPath(Paths.get(dockerCertPath)).build();
    } else {
      return Optional.absent();
    }
  }

  /**
   * Get the email from the server configuration in <code>~/.m2/settings.xml</code>.
   *
   * <pre>{@code
   * <servers>
   *   <server>
   *     <id>my-private-docker-registry</id>
   *     [...]
   *     <configuration>
   *       <email>foo@bar.com</email>
   *     </configuration>
   *   </server>
   * </servers>
   * }</pre>
   *
   * The above <code>settings.xml</code> would return "foo@bar.com".
   * @param server {@link Server}
   * @return email, or {@code null} if not set
   */
  private String getEmail(final Server server) {
    String email = null;

    final Xpp3Dom configuration = (Xpp3Dom) server.getConfiguration();

    if (configuration != null) {
      final Xpp3Dom emailNode = configuration.getChild("email");

      if (emailNode != null) {
        email = emailNode.getValue();
      }
    }

    return email;
  }

  /**
   * Builds the registryAuth object from server details.
   * @return {@link RegistryAuth}
   * @throws MojoExecutionException
   */
  protected RegistryAuth registryAuth() throws MojoExecutionException {
    if (settings != null && serverId != null) {
      final Server server = settings.getServer(serverId);
      if (server != null) {
        final RegistryAuth.Builder registryAuthBuilder = RegistryAuth.builder();

        final String username = server.getUsername();
        String password = server.getPassword();
        if (secDispatcher != null) {
          try {
            password = secDispatcher.decrypt(password);
          } catch (SecDispatcherException ex) {
            throw new MojoExecutionException("Cannot decrypt password from settings", ex);
          }
        }
        final String email = getEmail(server);

        if (!isNullOrEmpty(username)) {
          registryAuthBuilder.username(username);
        }
        if (!isNullOrEmpty(email)) {
          registryAuthBuilder.email(email);
        }
        if (!isNullOrEmpty(password)) {
          registryAuthBuilder.password(password);
        }
        if (!isNullOrEmpty(registryUrl)) {
          registryAuthBuilder.serverAddress(registryUrl);
        }

        return registryAuthBuilder.build();
      } else {
        // settings.xml has no entry for the configured serverId, warn the user
        getLog().warn("No entry found in settings.xml for serverId=" + serverId
                      + ", cannot configure authentication for that registry");
      }
    }
    return null;
  }

  private RegistryAuthSupplier authSupplier() throws MojoExecutionException {

    final List<RegistryAuthSupplier> suppliers = new ArrayList<>();

    // prioritize the docker config file
    suppliers.add(new ConfigFileRegistryAuthSupplier());

    // then Google Container Registry support
    final RegistryAuthSupplier googleRegistrySupplier = googleContainerRegistryAuthSupplier();
    if (googleRegistrySupplier != null) {
      suppliers.add(googleRegistrySupplier);
    }

    // lastly, use any explicitly configured RegistryAuth as a catch-all
    final RegistryAuth registryAuth = registryAuth();
    if (registryAuth != null) {
      final RegistryConfigs configsForBuild = RegistryConfigs.create(ImmutableMap.of(
          serverIdFor(registryAuth), registryAuth
      ));
      suppliers.add(new FixedRegistryAuthSupplier(registryAuth, configsForBuild));
    }

    getLog().info("Using authentication suppliers: " +
                  Lists.transform(suppliers, new SupplierToClassNameFunction()));

    return new MultiRegistryAuthSupplier(suppliers);
  }

  private String serverIdFor(RegistryAuth registryAuth) {
    if (serverId != null) {
      return serverId;
    }
    if (registryAuth.serverAddress() != null) {
      return registryAuth.serverAddress();
    }
    return "index.docker.io";
  }

  /**
   * Attempt to load a GCR compatible RegistryAuthSupplier based on a few conditions:
   * <ol>
   * <li>First check to see if the environemnt variable DOCKER_GOOGLE_CREDENTIALS is set and points
   * to a readable file</li>
   * <li>Otherwise check if the Google Application Default Credentials can be loaded</li>
   * </ol>
   * Note that we use a special environment variable of our own in addition to any environment
   * variable that the ADC loading uses (GOOGLE_APPLICATION_CREDENTIALS) in case there is a need for
   * the user to use the latter env var for some other purpose in their build.
   *
   * @return a GCR RegistryAuthSupplier, or null
   * @throws MojoExecutionException if an IOException occurs while loading the explicitly-requested
   *                                credentials
   */
  private RegistryAuthSupplier googleContainerRegistryAuthSupplier() throws MojoExecutionException {
    GoogleCredentials credentials = null;

    final String googleCredentialsPath = System.getenv("DOCKER_GOOGLE_CREDENTIALS");
    if (googleCredentialsPath != null) {
      final File file = new File(googleCredentialsPath);
      if (file.exists()) {
        try {
          try (FileInputStream inputStream = new FileInputStream(file)) {
            credentials = GoogleCredentials.fromStream(inputStream);
            getLog().info("Using Google credentials from file: " + file.getAbsolutePath());
          }
        } catch (IOException ex) {
          throw new MojoExecutionException("Cannot load credentials referenced by "
                                           + "DOCKER_GOOGLE_CREDENTIALS environment variable", ex);
        }
      }
    }

    // use the ADC last
    if (credentials == null) {
      try {
        credentials = GoogleCredentials.getApplicationDefault();
        getLog().info("Using Google application default credentials");
      } catch (IOException ex) {
        // No GCP default credentials available
        getLog().debug("Failed to load Google application default credentials", ex);
      }
    }

    if (credentials == null) {
      return null;
    }

    return ContainerRegistryAuthSupplier.forCredentials(credentials).build();
  }

  private static class SupplierToClassNameFunction
      implements Function<RegistryAuthSupplier, String> {

    @Override
    @Nonnull
    public String apply(@Nonnull final RegistryAuthSupplier input) {
      return input.getClass().getSimpleName();
    }
  }
}
