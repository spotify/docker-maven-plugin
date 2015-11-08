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

import com.google.common.annotations.VisibleForTesting;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerClient;
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

  @Parameter(property = "serverId")
  private String serverId;

  @Parameter(property = "registryUrl")
  private String registryUrl;

  protected abstract void execute(final DockerClient dockerClient) throws Exception;

  @Override
  public void execute() throws MojoExecutionException {
    try (DockerClient client = getDockerClient()) {
      execute(client);
    } catch (Exception e) {
      throw new MojoExecutionException("Exception caught", e);
    }
  }

  private DockerClient getDockerClient()
      throws DockerCertificateException, MojoExecutionException, SecDispatcherException {

    final DefaultDockerClient.Builder builder = getBuilder();

    final String dockerHost = rawDockerHost();
    if (!isNullOrEmpty(dockerHost)) {
      builder.uri(dockerHost);
    }

    final AuthConfig authConfig = authConfig();
    if (authConfig != null) {
      builder.authConfig(authConfig);
    }

    return builder.build();
  }

  @VisibleForTesting
  protected DefaultDockerClient.Builder getBuilder() throws DockerCertificateException {
    return DefaultDockerClient.fromEnv()
        .readTimeoutMillis(NO_TIMEOUT);
  }

  protected String rawDockerHost() {
    return dockerHost;
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
    // first try to construct the authentication config from the user's settings based on the
    // <server> configured in this mojo
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
      }
    }

    // fall back to using the .docker configuration file
    try {
      return AuthConfig.fromDockerConfig().build();
    } catch (IOException e) {
      getLog()
          .warn("IOException while reading authentication configuration from .docker directory", e);
    }
    return null;
  }
}
