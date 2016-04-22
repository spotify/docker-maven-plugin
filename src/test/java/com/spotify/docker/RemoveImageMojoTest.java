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
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.messages.RemovedImage;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RemoveImageMojoTest extends AbstractMojoTestCase {

  public void testRemoveImage() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-removeImage.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final RemoveImageMojo mojo = (RemoveImageMojo) lookupMojo("removeImage", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);
    verify(docker).removeImage("imageToRemove", true, false);
  }

  public void testRemoveMissingImage() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-removeImage.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final RemoveImageMojo mojo = (RemoveImageMojo) lookupMojo("removeImage", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    Mockito.when(docker.removeImage("imageToRemove", true, false))
        .thenThrow(new ImageNotFoundException("imageToRemove"));
    try {
      mojo.execute(docker);
      verify(docker).removeImage("imageToRemove", true, false);
    } catch (DockerException e){
      assertFalse("image to remove was missing", e instanceof ImageNotFoundException);
    }
  }

  public void testRemoveImageWithTags() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-removeMultipleImages.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final RemoveImageMojo mojo = (RemoveImageMojo) lookupMojo("removeImage", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    Mockito.when(docker.removeImage("imageToRemove", true, false))
        .thenThrow(new ImageNotFoundException("imageToRemove"));
    Mockito.when(docker.removeImage("imageToRemove:123456", true, false))
        .thenThrow(new ImageNotFoundException("imageToRemove:123456"));
    Mockito.when(docker.removeImage("imageToRemove:bbbbbbb", true, false))
        .thenReturn(new ArrayList<RemovedImage>());
    try {
      mojo.execute(docker);
    } catch (DockerException e){
      assertFalse("image to remove was missing", e instanceof ImageNotFoundException);
    }
    verify(docker).removeImage("imageToRemove:123456", true, false);
    verify(docker).removeImage("imageToRemove:bbbbbbb", true, false);
  }
}
