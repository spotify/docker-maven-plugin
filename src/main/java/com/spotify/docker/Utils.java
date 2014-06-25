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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;

public class Utils {

  public static String[] parseImageName(String imageName) {
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

  public static String getGitCommitId()
      throws GitAPIException, DockerException, IOException, MojoExecutionException {

    final FileRepositoryBuilder builder = new FileRepositoryBuilder();
    builder.readEnvironment(); // scan environment GIT_* variables
    builder.findGitDir(); // scan up the file system tree

    if (builder.getGitDir() == null) {
      throw new MojoExecutionException(
          "Cannot tag with git commit ID because directory not a git repo");
    }

    final StringBuilder result = new StringBuilder();
    final Repository repo = builder.build();

    try {
      // get the first 7 characters of the latest commit
      final ObjectId head = repo.resolve("HEAD");
      result.append(head.getName().substring(0, 7));
      final Git git = new Git(repo);

      // append first git tag we find
      for (Ref gitTag : git.tagList().call()) {
        if (gitTag.getObjectId().equals(head)) {
          // name is refs/tag/name, so get substring after last slash
          final String name = gitTag.getName();
          result.append(".");
          result.append(name.substring(name.lastIndexOf('/') + 1));
          break;
        }
      }

      // append '.DIRTY' if any files have been modified
      final Status status = git.status().call();
      if (status.hasUncommittedChanges()) {
        result.append(".DIRTY");
      }
    } finally {
      repo.close();
    }

    return result.length() == 0 ? null : result.toString();
  }

}
