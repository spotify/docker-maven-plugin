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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.spotify.docker.Utils.parseImageName;
import static com.spotify.docker.Utils.pushImage;
import static com.spotify.docker.Utils.writeImageInfoFile;

/**
 * Applies a tag to a docker image. Optionally, {@code useGitCommitId} can be used to generate a
 * tag consisting of the first 7 characters of the most recent git commit ID.
 */
@Mojo(name = "tag")
public class TagMojo extends AbstractDockerMojo {

  /**
   * Can be either an image ID (e.g. 8dbd9e392a96), or an image name with an optional tag. If no
   * tag is specified, the docker daemon will automatically try to use the tag 'latest'.
   */
  @Parameter(property = "image", required = true)
  private String image;

  /**
   * Flag to skip tagging, making goal a no-op. This can be useful when docker:tag is bound to
   * the package goal, and you want to mvn package without tagging the image. Defaults to false.
   */
  @Parameter(property = "skipDockerTag", defaultValue = "false")
  private boolean skipDockerTag;

  /**
   * The new name that will be applied to the source image. If a tag is not specified, the docker
   * daemon will automatically apply the tag 'latest' to the specified repo. Only a repo without a
   * tag should be specified if {@code useGitCommitId} is set to true.
   */
  @Parameter(property = "newName", required = true)
  private String newName;

  /** Flag to push image after it is tagged. */
  @Parameter(property = "pushImage", defaultValue = "false")
  private boolean pushImage;

  /** Flag to use force option while tagging. Defaults to false. */
  @Parameter(property = "forceTags", defaultValue = "false")
  private boolean forceTags;

  /** Path to JSON file to write when tagging images */
  @Parameter(property = "tagInfoFile")
  private String tagInfoFile = "target/image_info.json";

  /**
   * If specified as true, a tag will be generated consisting of the first 7 characters of the most
   * recent git commit ID, resulting in something like {@code image:df8e8e6}. If there are any
   * changes not yet committed, the string '.DIRTY' will be appended to the end. Note, if a tag is
   * explicitly specified in the {@code newName} parameter, this flag will be ignored.
   */
  @Parameter(property = "useGitCommitId", defaultValue = "false")
  private boolean useGitCommitId;

  public boolean isSkipDockerTag() {
    return skipDockerTag;
  }

  @Override
  protected void execute(DockerClient docker)
      throws MojoExecutionException, DockerException,
             IOException, InterruptedException, GitAPIException {

    if (skipDockerTag) {
      getLog().info("Skipping docker tag");
      return;
    }

    final String[] repoTag = parseImageName(newName);
    final String repo = repoTag[0];
    String tag = repoTag[1];

    if (useGitCommitId) {
      if (tag != null) {
        getLog().warn("Ignoring useGitCommitId flag because tag is explicitly set in image name ");
      } else {
        tag = new Git().getCommitId();
      }
    }

    final String normalizedName = isNullOrEmpty(tag) ? repo : String.format("%s:%s", repo, tag);
    getLog().info(String.format("Creating tag %s from %s", normalizedName, image));
    docker.tag(image, normalizedName, forceTags);

    final DockerBuildInformation buildInfo = new DockerBuildInformation(normalizedName, getLog());

    if (pushImage) {
      pushImage(docker, newName, null, getLog(), buildInfo, getRetryPushCount(),
          getRetryPushTimeout(), isSkipDockerPush());
    }

    writeImageInfoFile(buildInfo, tagInfoFile);
  }

}
