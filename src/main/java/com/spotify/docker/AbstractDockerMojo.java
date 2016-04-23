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

import com.google.common.base.Optional;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.messages.AuthConfig;
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
import static com.spotify.docker.client.DefaultDockerClient.NO_TIMEOUT;

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
  private String useConfigFile;

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

  public int getRetryPushTimeout() {
    return retryPushTimeout;
  }

  public int getRetryPushCount() {
    return retryPushCount;
  };

  public void execute() throws MojoExecutionException {
    DockerClient client = null;
    try {
      final DefaultDockerClient.Builder builder = getBuilder();

      final String dockerHost = rawDockerHost();
      if (!isNullOrEmpty(dockerHost)) {
        builder.uri(dockerHost);
      }
      final Optional<DockerCertificates> certs = dockerCertificates();
      if (certs.isPresent()) {
        builder.dockerCertificates(certs.get());
      }

      final AuthConfig authConfig = authConfig();
      if (authConfig != null) {
        builder.authConfig(authConfig);
      }

      client = builder.build();
      execute(client);
    } catch (Exception e) {
      throw new MojoExecutionException("Exception caught", e);
    } finally {
      if (client != null) {
        client.close();
      }
    }
  }

  protected DefaultDockerClient.Builder getBuilder() throws DockerCertificateException {
    return DefaultDockerClient.fromEnv()
      .readTimeoutMillis(NO_TIMEOUT);
  }

  protected abstract void execute(final DockerClient dockerClient) throws Exception;

  protected String rawDockerHost() {
    return dockerHost;
  }

  protected Optional<DockerCertificates> dockerCertificates() throws DockerCertificateException {
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
   * Builds the AuthConfig object from server details.
   * @return AuthConfig
   * @throws MojoExecutionException
   * @throws SecDispatcherException
   */
  protected AuthConfig authConfig() throws MojoExecutionException, SecDispatcherException {
    if (settings != null) {
      final Server server = settings.getServer(serverId);
      if (server != null) {
        final AuthConfig.Builder authConfigBuilder = AuthConfig.builder();

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
          authConfigBuilder.username(username);
        }
        if (!isNullOrEmpty(email)) {
          authConfigBuilder.email(email);
        }
        if (!isNullOrEmpty(password)) {
          authConfigBuilder.password(password);
        }
        // registryUrl is optional.
        // Spotify's docker-client defaults to 'https://index.docker.io/v1/'.
        if (!isNullOrEmpty(registryUrl)) {
          authConfigBuilder.serverAddress(registryUrl);
        }

        return authConfigBuilder.build();
      } else if (!isNullOrEmpty(useConfigFile) && Boolean.TRUE.toString().equals(useConfigFile)){

          final AuthConfig.Builder authConfigBuilder;
          try {
            if (!isNullOrEmpty(registryUrl)) {
              authConfigBuilder = AuthConfig.fromDockerConfig(registryUrl);
            } else {
              authConfigBuilder = AuthConfig.fromDockerConfig();
            }
          } catch (IOException ex){
            throw new MojoExecutionException(
                      "Docker config file could not be read",
                      ex
            );
          }

          return authConfigBuilder.build();
      }
    }
    return null;
  }
}
