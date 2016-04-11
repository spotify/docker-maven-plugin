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
import com.spotify.docker.client.DockerException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.util.List;

import static com.spotify.docker.Utils.parseImageName;
import static com.spotify.docker.Utils.pushImage;
import static com.spotify.docker.Utils.pushImageTag;

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

  protected void execute(DockerClient docker)
      throws MojoExecutionException, DockerException, IOException, InterruptedException {
    // Push specific tags specified in pom rather than all images
    if (imageTags != null) {
      final String imageNameWithoutTag = parseImageName(imageName)[0];
      pushImageTag(docker, imageNameWithoutTag, imageTags, getLog());
    }

    pushImage(docker, imageName, getLog(), null, getRetryPushCount(), getRetryPushTimeout());
  }

}
