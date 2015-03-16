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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;

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

  public static void pushImage(DockerClient docker, String imageName, Log log)
      throws MojoExecutionException, DockerException, IOException, InterruptedException {
      log.info("Pushing " + imageName);
      docker.push(imageName, new AnsiProgressHandler());
  }

}
