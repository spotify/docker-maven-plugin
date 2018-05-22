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

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class UtilsTest {

  private static final String TAG = "tag";
  private static final String IMAGE = "image";
  private static final String IMAGE_WITH_SEPERATOR = "image:";
  private static final String IMAGE_WITH_LIBRARY = "library/image";
  private static final String IMAGE_FROM_REGISTRY = "registry:80/library/image";
  private static final String IMAGE_WITH_TAG = "image:tag";
  private static final String IMAGE_FROM_LIB_WITH_TAG = "library/image:tag";
  private static final String IMAGE_FROM_REG_WITH_TAG = "registry:80/library/image:tag";


  @Test
  public void testParseImageName() throws MojoExecutionException {
    final String[] result = Utils.parseImageName(IMAGE);
    assertThat(result).containsExactly(IMAGE, null);
  }

  @Test
  public void testParseImageNameWithSeperator() throws MojoExecutionException {
    final String[] result = Utils.parseImageName(IMAGE_WITH_SEPERATOR);
    assertThat(result).containsExactly(IMAGE, null);
  }

  @Test
  public void testParseImageNameWithTag() throws MojoExecutionException {
    final String[] result = Utils.parseImageName(IMAGE_WITH_TAG);
    assertThat(result).containsExactly(IMAGE, TAG);
  }

  @Test
  public void testParseImageNameWithLibrary() throws MojoExecutionException {
    final String[] result = Utils.parseImageName(IMAGE_WITH_LIBRARY);
    assertThat(result).containsExactly(IMAGE_WITH_LIBRARY, null);
  }

  @Test
  public void testParseImageNameWithLibraryAndTag() throws MojoExecutionException {
    final String[] result = Utils.parseImageName(IMAGE_FROM_LIB_WITH_TAG);
    assertThat(result).containsExactly(IMAGE_WITH_LIBRARY, TAG);
  }

  @Test
  public void testParseImageNameFromRegistryAndTag() throws MojoExecutionException {
    final String[] result = Utils.parseImageName(IMAGE_FROM_REG_WITH_TAG);
    assertThat(result).containsExactly(IMAGE_FROM_REGISTRY, TAG);
  }

  @Test
  public void testPushImage() throws Exception {
    final DockerClient dockerClient = mock(DockerClient.class);
    final Log log = mock(Log.class);
    final DockerBuildInformation buildInfo = mock(DockerBuildInformation.class);
    Utils.pushImage(dockerClient, IMAGE, null, log, buildInfo, 0, 1, false);

    verify(dockerClient).push(eq(IMAGE), any(AnsiProgressHandler.class));
  }

  @Test
  public void testSaveImage() throws Exception {
    final DockerClient dockerClient = mock(DockerClient.class);
    final Log log = mock(Log.class);
    final Path path = Files.createTempFile(IMAGE, ".tgz");
    final String imageDataLine = "TestDataForDockerImage";

    Mockito.doAnswer(new Answer<InputStream>() {
        @Override
        public InputStream answer(InvocationOnMock invocation) throws Throwable {
            return new ReaderInputStream(new StringReader(imageDataLine));
        }
    }).when(dockerClient).save(IMAGE);

    try {
        Utils.saveImage(dockerClient, IMAGE, path, log);
        verify(dockerClient).save(eq(IMAGE));
    } finally {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }
  }
}
