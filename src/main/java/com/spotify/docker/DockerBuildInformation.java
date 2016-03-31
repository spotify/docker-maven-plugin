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

import com.google.common.base.Throwables;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;
import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;
import static com.google.common.base.Strings.isNullOrEmpty;

//* Might be overkill to do it this way, but should simplify things when if/when we add more to this
public class DockerBuildInformation {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(SORT_PROPERTIES_ALPHABETICALLY, true)
      .configure(ORDER_MAP_ENTRIES_BY_KEYS, true)
      .setSerializationInclusion(NON_NULL);

  @JsonProperty("image")
  private final String image;

  @JsonProperty("repo")
  private String repo;

  @JsonProperty("commit")
  private String commit;

  @JsonProperty("digest")
  private String digest;

  public DockerBuildInformation(final String image, final Log log) {
    this.image = image;
    updateGitInformation(log);
  }

  public DockerBuildInformation setDigest(final String digest) {
    this.digest = digest;
    return this;
  }

  private void updateGitInformation(Log log) {
    try {
      final Repository repo = new Git().getRepo();
      if (repo != null) {
        this.repo   = repo.getConfig().getString("remote", "origin", "url");
        final ObjectId head = repo.resolve("HEAD");
        if (head != null && !isNullOrEmpty(head.getName())) {
          this.commit = head.getName();
        }
      }
    } catch (IOException e) {
      log.error("Failed to read Git information", e);
    }
  }


  public byte[] toJsonBytes() {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(this);
    } catch (JsonProcessingException e) {
      throw Throwables.propagate(e);
    }
  }

  public String getImage() {
    return image;
  }

  public String getRepo(){
    return repo;
  }

  public String getCommit(){
    return commit;
  }

  public String getDigest() {
    return digest;
  }
}
