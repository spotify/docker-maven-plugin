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
import static com.spotify.docker.Utils.parseImageName;

import com.google.common.base.Objects;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.RemovedImage;
import com.spotify.docker.client.shaded.javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Removes a docker image.
 */
@Mojo(name = "removeImage")
public class RemoveImageMojo extends AbstractDockerMojo {

  /**
   * Name of image to remove.
   */
  @Parameter(property = "imageName", required = true)
  private String imageName;

  /**
   * Additional tags to remove.
   */
  @Parameter(property = "dockerImageTags")
  private List<String> imageTags;

  /**
   * Additional tags to tag the image with.
   */
  @Parameter(property = "removeAllTags", defaultValue = "false")
  private boolean removeAllTags;

  @Override
  protected void execute(final DockerClient docker)
      throws MojoExecutionException, DockerException, InterruptedException {
    final String[] imageNameParts = parseImageName(imageName);
    if (imageTags == null) {
      imageTags = new ArrayList<>(1);
      imageTags.add(imageNameParts[1]);
    } else if (removeAllTags) {
      getLog().info("Removal of all tags requested, searching for tags");
      // removal of all tags requested, loop over all images to find tags
      for (final Image currImage : docker.listImages()) {
        getLog().debug("Found image: " + currImage.toString());
        String[] parsedRepoTag;
        if (currImage.repoTags() != null) {
          for (final String repoTag : currImage.repoTags()) {
            parsedRepoTag = parseImageName(repoTag);
            // if repo name matches imageName then save the tag for deletion
            if (Objects.equal(parsedRepoTag[0], imageNameParts[0])) {
              imageTags.add(parsedRepoTag[1]);
              getLog().info("Adding tag for removal: " + parsedRepoTag[1]);
            }
          }
        }
      }
    }
    imageTags.add(imageNameParts[1]);

    final Set<String> uniqueImageTags = new HashSet<>(imageTags);
    for (final String imageTag : uniqueImageTags) {
      final String currImageName =
          imageNameParts[0] + ((isNullOrEmpty(imageTag)) ? "" : (":" + imageTag));
      getLog().info("Removing -f " + currImageName);

      try {
        // force the image to be removed but don't remove untagged parents
        for (final RemovedImage removedImage : docker.removeImage(currImageName, true, false)) {
          getLog().info("Removed: " + removedImage.imageId());
        }
      } catch (ImageNotFoundException | NotFoundException e) {
        // ignoring 404 errors only
        getLog().warn("Image " + imageName + " doesn't exist and cannot be deleted - ignoring");
      }
    }
  }
}
