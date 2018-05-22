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

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;

import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;

import static com.spotify.docker.Utils.pushImage;

/**
 * Pushes a docker image repository to the specified docker registry.
 */
@Mojo(name = "push")
public class PushMojo extends AbstractDockerMojo {

  /** Name of image to push. */
  @Parameter(property = "imageName", required = true)
  private String imageName;

  /** Additional tags to tag the image with. */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @Parameter(property = "dockerImageTags")
  private List<String> imageTags;

  @Override
  protected void execute(DockerClient docker)
      throws MojoExecutionException, DockerException, IOException, InterruptedException {

    pushImage(docker, imageName, imageTags, getLog(), null, getRetryPushCount(),
        getRetryPushTimeout(), isSkipDockerPush());
  }

}
