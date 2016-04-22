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

import com.spotify.docker.client.exceptions.DockerException;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;

public class Git {

  private Repository repo;

  public Git() throws IOException {
    final FileRepositoryBuilder builder = new FileRepositoryBuilder();
    // scan environment GIT_* variables
    builder.readEnvironment();
    // scan up the file system tree
    builder.findGitDir();
    // if getGitDir is null, then we are not in a git repository
    repo = builder.getGitDir() == null ? null : builder.build();
  }

  public boolean isRepository() {
    return repo != null;
  }

  public Repository getRepo() {
    return repo;
  }

  void setRepo(final Repository repo) {
    this.repo = repo;
  }

  public String getCommitId()
      throws GitAPIException, DockerException, IOException, MojoExecutionException {

    if (repo == null) {
      throw new MojoExecutionException(
          "Cannot tag with git commit ID because directory not a git repo");
    }

    final StringBuilder result = new StringBuilder();

    try {
      // get the first 7 characters of the latest commit
      final ObjectId head = repo.resolve("HEAD");
      if (head == null || isNullOrEmpty(head.getName())) {
        return null;
      }

      result.append(head.getName().substring(0, 7));
      final org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(repo);

      // append first git tag we find
      for (final Ref gitTag : git.tagList().call()) {
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
