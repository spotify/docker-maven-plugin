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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

import java.net.URI;
import java.net.URISyntaxException;

import static com.spotify.docker.client.DefaultDockerClient.NO_TIMEOUT;

abstract class AbstractDockerMojo extends AbstractMojo {

  private static final String DEFAULT_DOCKER_HOST = "tcp://localhost:2375";

  @Component(role = MavenSession.class)
  protected MavenSession session;

  @Component(role = MojoExecution.class)
  protected MojoExecution execution;

  /**
   * URL of the docker host. Defaults to DOCKER_HOST env variable or DEFAULT_DOCKER_HOST constant.
   */
  @Parameter(property = "dockerHost")
  private String dockerHost;

  public void execute() throws MojoExecutionException {
    final DefaultDockerClient client = DefaultDockerClient.builder()
        .uri(dockerHost())
        .readTimeoutMillis(NO_TIMEOUT)
        .build();
    try {
      execute(client);
    } catch (Exception e) {
      throw new MojoExecutionException("Exception caught", e);
    } finally {
      client.close();
    }
  }

  protected abstract void execute(final DockerClient dockerClient) throws Exception;

  protected String dockerHost() {
    return normalize(rawDockerHost());
  }

  private String normalize(final String raw) {
    final String withSchema = raw.contains("://") ? raw : "tcp://" + raw;
    final URI uri = URI.create(withSchema);
    final URI normalized;
    try {
      normalized = new URI("http", uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(),
                           uri.getQuery(), uri.getFragment());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return normalized.toString();
  }

  protected String rawDockerHost() {
    if (dockerHost != null) {
      return dockerHost;
    }
    final String dockerHostEnv = System.getenv("DOCKER_HOST");
    if (dockerHostEnv != null) {
      return dockerHostEnv;
    }
    return DEFAULT_DOCKER_HOST;
  }
}
