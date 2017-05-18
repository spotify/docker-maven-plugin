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

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerCertificatesStore;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.messages.RegistryAuth;

import com.google.common.base.Optional;

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

import java.io.IOException;
import java.nio.file.Paths;

import static com.google.common.base.Strings.isNullOrEmpty;

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

  @Parameter(property = "useConfigFile")
  private Boolean useConfigFile;

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

  protected DockerClient buildDockerClient()
      throws DockerCertificateException, SecDispatcherException, MojoExecutionException {

    final DefaultDockerClient.Builder builder = getBuilder();

    final String dockerHost = rawDockerHost();
    if (!isNullOrEmpty(dockerHost)) {
      builder.uri(dockerHost);
    }
    final Optional<DockerCertificatesStore> certs = dockerCertificates();
    if (certs.isPresent()) {
      builder.dockerCertificates(certs.get());
    }

    final RegistryAuth registryAuth = registryAuth();
    if (registryAuth != null) {
      builder.registryAuth(registryAuth);
    }

    return builder.build();
  }

  protected abstract void execute(final DockerClient dockerClient) throws Exception;

  protected String rawDockerHost() {
    return dockerHost;
  }

  protected Optional<DockerCertificatesStore> dockerCertificates()
      throws DockerCertificateException
  {
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
   * <pre>
   * <servers>
   *   <server>
   *     <id>my-private-docker-registry</id>
   *     [...]
   *     <configuration>
   *       <email>foo@bar.com</email>
   *     </configuration>
   *   </server>
   * </servers>
   * </pre>
   *
   * The above <code>settings.xml</code> would return "foo@bar.com".
   *
   * @param server {@link org.apache.maven.settings.Server}
   * @return email string.
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
   * Checks for incomplete private Docker registry authorization settings.
   * @param username Auth username.
   * @param password Auth password.
   * @param email    Auth email.
   * @return boolean true if any of the three credentials are present but not all. False otherwise.
   */
  private boolean incompleteAuthSettings(final String username, final String password,
                                         final String email) {
    return (!isNullOrEmpty(username) || !isNullOrEmpty(password) || !isNullOrEmpty(email))
           && (isNullOrEmpty(username) || isNullOrEmpty(password) || isNullOrEmpty(email));

  }

  /**
   * Builds the registryAuth object from server details.
   * @return registryAuth
   * @throws MojoExecutionException
   * @throws SecDispatcherException
   */
  protected RegistryAuth registryAuth() throws MojoExecutionException, SecDispatcherException {
    if (settings != null) {
      final Server server = settings.getServer(serverId);
      if (server != null) {
        final RegistryAuth.Builder RegistryAuthBuilder = RegistryAuth.builder();

        final String username = server.getUsername();
        String password = server.getPassword();
        if (secDispatcher != null) {
          password = secDispatcher.decrypt(password);
        }
        final String email = getEmail(server);

        if (incompleteAuthSettings(username, password, email)) {
          throw new MojoExecutionException(
                  "Incomplete Docker registry authorization credentials. "
                          + "Please provide all of username, password, and email or none.");
        }

        if (!isNullOrEmpty(username)) {
          RegistryAuthBuilder.username(username);
        }
        if (!isNullOrEmpty(email)) {
          RegistryAuthBuilder.email(email);
        }
        if (!isNullOrEmpty(password)) {
          RegistryAuthBuilder.password(password);
        }
        // registryUrl is optional.
        // Spotify's docker-client defaults to 'https://index.docker.io/v1/'.
        if (!isNullOrEmpty(registryUrl)) {
          RegistryAuthBuilder.serverAddress(registryUrl);
        }

        return RegistryAuthBuilder.build();
      } else if (useConfigFile != null && useConfigFile){

          final RegistryAuth.Builder RegistryAuthBuilder;
          try {
            if (!isNullOrEmpty(registryUrl)) {
              RegistryAuthBuilder = RegistryAuth.fromDockerConfig(registryUrl);
            } else {
              RegistryAuthBuilder = RegistryAuth.fromDockerConfig();
            }
          } catch (IOException ex){
            throw new MojoExecutionException(
                      "Docker config file could not be read",
                      ex
            );
          }

          return RegistryAuthBuilder.build();
      }
    }
    return null;
  }
}
