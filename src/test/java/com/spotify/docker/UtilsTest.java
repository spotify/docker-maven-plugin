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

import junit.framework.TestCase;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.spotify.docker.Utils.parseImageName;

public class UtilsTest extends TestCase {

  public void testParseImageName() throws MojoExecutionException {
    assertParsedCorrectly("registry.spotify.net/spotify/image", "tag",
                          parseImageName("registry.spotify.net/spotify/image:tag"));

    assertParsedCorrectly("registry:80/spotify/image", "tag",
                          parseImageName("registry:80/spotify/image:tag"));

    assertParsedCorrectly("spotify/image", "tag", parseImageName("spotify/image:tag"));

    assertParsedCorrectly("image", "tag", parseImageName("image:tag"));

    assertParsedCorrectly("image", null, parseImageName("image:"));

    assertParsedCorrectly("image", null, parseImageName("image"));
  }

  private void assertParsedCorrectly(String expectedRepo,
                                     String expectedTag,
                                     String[] actualRepoTag) {
    assertEquals("wrong repo", expectedRepo, actualRepoTag[0]);
    assertEquals("wrong tag", expectedTag, actualRepoTag[1]);
  }

  public synchronized static ByteArrayInputStream getMockInputStream(String filename) {
    try {
      return new ByteArrayInputStream(Files.readAllBytes(Paths.get(filename)));
    } catch (IOException e) {
      throw new RuntimeException("Exception reading json stream from file", e);
    }
  }

}
