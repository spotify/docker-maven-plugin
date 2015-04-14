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

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.mockito.ArgumentCaptor;

import java.io.File;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TagMojoTest extends AbstractMojoTestCase {

  public void testTag1() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-tag1.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final TagMojo mojo = (TagMojo) lookupMojo("tag", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);
    verify(docker).tag("imageToTag", "newRepo:newTag", false);
    verify(docker).push(eq("newRepo:newTag"), any(AnsiProgressHandler.class));
  }

  public void testTag2() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-tag2.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final TagMojo mojo = (TagMojo) lookupMojo("tag", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    final ArgumentCaptor<String> image = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
    mojo.execute(docker);
    verify(docker).tag(image.capture(), name.capture(), eq(false));
    assertEquals("wrong image", "imageToTag", image.getValue());
    final String[] split = name.getValue().split(":");
    assertEquals("wrong name", "newRepo", split[0]);
    assertTrue(String.format("tag '%s' should be git commit ID at least 7 characters long ",
                             split[1]),
               split[1].length() >= 7);
  }

  public void testTag3() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-tag3.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final TagMojo mojo = (TagMojo) lookupMojo("tag", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);
    verify(docker).tag("imageToTag", "newRepo:newTag", false);
  }

}
