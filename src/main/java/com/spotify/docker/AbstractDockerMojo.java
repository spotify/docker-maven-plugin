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
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.AuthConfig;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.spotify.docker.client.DefaultDockerClient.NO_TIMEOUT;

abstract class AbstractDockerMojo extends AbstractMojo {

  @Component(role = MavenSession.class)
  protected MavenSession session;

  @Component(role = MojoExecution.class)
  protected MojoExecution execution;

  /**
   * URL of the docker host as specified in pom.xml.
   */
  @Parameter(property = "dockerHost")
  private String dockerHost;

  @Parameter(property = "docker.registry.username")
  private String dockerRegistryUsername;

  @Parameter(property = "docker.registry.email")
  private String dockerRegistryEmail;

  @Parameter(property = "docker.registry.password")
  private String dockerRegistryPassword;

  @Parameter(property = "docker.registry.server-address")
  private String dockerRegistryServerAddress;

  public void execute() throws MojoExecutionException {
    DockerClient client = null;
    try {
       final DefaultDockerClient.Builder builder = DefaultDockerClient.fromEnv()
           .readTimeoutMillis(NO_TIMEOUT);

      final String dockerHost = rawDockerHost();
      if (!isNullOrEmpty(dockerHost)) {
        builder.uri(dockerHost);
      }

      final AuthConfig.Builder authConfigBuilder = AuthConfig.builder();
      if (!isNullOrEmpty(dockerRegistryUsername)) {
        authConfigBuilder.username(dockerRegistryUsername);
      }
      if (!isNullOrEmpty(dockerRegistryEmail)) {
        authConfigBuilder.email(dockerRegistryEmail);
      }
      if (!isNullOrEmpty(dockerRegistryPassword)) {
        authConfigBuilder.password(dockerRegistryPassword);
      }
      if (!isNullOrEmpty(dockerRegistryServerAddress)) {
        authConfigBuilder.serverAddress(dockerRegistryServerAddress);
      }

      builder.authConfig(authConfigBuilder.build());

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

  protected abstract void execute(final DockerClient dockerClient) throws Exception;

  protected String rawDockerHost() {
    return dockerHost;
  }
}
