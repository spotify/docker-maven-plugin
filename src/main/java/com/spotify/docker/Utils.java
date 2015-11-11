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

import com.spotify.docker.client.AnsiProgressHandler;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.messages.ProgressMessage;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Strings.isNullOrEmpty;

public class Utils {

  public static String[] parseImageName(String imageName) throws MojoExecutionException {
    if (isNullOrEmpty(imageName)) {
      throw new MojoExecutionException("You must specify an \"imageName\" in your "
                                       + "docker-maven-client's plugin configuration");
    }
    final int lastSlashIndex = imageName.lastIndexOf('/');
    final int lastColonIndex = imageName.lastIndexOf(':');

    // assume name doesn't contain tag by default
    String repo = imageName;
    String tag = null;

    // the name contains a tag if lastColonIndex > lastSlashIndex
    if (lastColonIndex > lastSlashIndex) {
      repo = imageName.substring(0, lastColonIndex);
      tag = imageName.substring(lastColonIndex + 1);
      // handle case where tag is empty string (e.g. 'repo:')
      if (tag.isEmpty()) {
        tag = null;
      }
    }

    return new String[] { repo, tag };
  }

  public static void pushImage(DockerClient docker, String imageName, Log log,
                               final DockerBuildInformation buildInfo)
      throws MojoExecutionException, DockerException, IOException, InterruptedException {
      log.info("Pushing " + imageName);

    final AnsiProgressHandler ansiProgressHandler = new AnsiProgressHandler();
    final DigestExtractingProgressHandler handler = new DigestExtractingProgressHandler(
        ansiProgressHandler);

    docker.push(imageName, handler);

    if (buildInfo != null) {
      final String imageNameWithoutTag = parseImageName(imageName)[0];
      buildInfo.setDigest(imageNameWithoutTag + "@" + handler.digest());
    }
  }

  // push just the tags listed in the pom rather than all images using imageName
  public static void pushImageTag(DockerClient docker, String imageName,
                                List<String> imageTags, Log log)
      throws MojoExecutionException, DockerException, IOException, InterruptedException {
      // tags should not be empty if you have specified the option to push tags
      if (imageTags.isEmpty()) {
        throw new MojoExecutionException("You have used option \"pushImageTag\" but have"
                                         + " not specified an \"imageTag\" in your"
                                         + " docker-maven-client's plugin configuration");
      }
      for (final String imageTag : imageTags) {
       final String imageNameWithTag = imageName + ":" + imageTag;
       log.info("Pushing " + imageName + ":" + imageTag);
       docker.push(imageNameWithTag, new AnsiProgressHandler());
      }
  }
  
  public static void writeImageInfoFile(final DockerBuildInformation buildInfo,
                                        final String tagInfoFile) throws IOException {
    final Path imageInfoPath = Paths.get(tagInfoFile);
    if (imageInfoPath.getParent() != null) {
      Files.createDirectories(imageInfoPath.getParent());
    }
    Files.write(imageInfoPath, buildInfo.toJsonBytes());
  }

  private static class DigestExtractingProgressHandler implements ProgressHandler {

    private final ProgressHandler delegate;
    private String digest;

    DigestExtractingProgressHandler(final ProgressHandler delegate) {
      this.delegate = delegate;
    }

    @Override
    public void progress(final ProgressMessage message) throws DockerException {
      if (message.digest() != null) {
        digest = message.digest();
      }

      delegate.progress(message);
    }

    public String digest() {
      return digest;
    }
  }
}
