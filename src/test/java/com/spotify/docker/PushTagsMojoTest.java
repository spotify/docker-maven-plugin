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
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.AuthConfig;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;

import static com.googlecode.catchexception.CatchException.verifyException;
import static com.spotify.docker.TestUtils.getPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class PushTagsMojoTest extends AbstractMojoTestCase {

  public void testPushTags() throws Exception {
    final File pom = getPom("/pom-push-tags.xml");

    final PushTagsMojo mojo = (PushTagsMojo) lookupMojo("push-tags", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);
    verify(docker).push(eq("busybox:latest"), any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:0.0.1-SNAPSHOT"), any(AnsiProgressHandler.class));
    verifyNoMoreInteractions(docker);
  }

  public void testPushTagsButNoTagsProvided() throws Exception {
    final File pom = getPom("/pom-push-tags-no-tags-provided.xml");

    final PushTagsMojo mojo = (PushTagsMojo) lookupMojo("push-tags", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    try {
      mojo.execute(docker);
      fail("mojo should have thrown exception because imageTags are not defined in pom");
    } catch (MojoExecutionException e) {
      final String message = "You have used option \"pushImageTag\" or goal \"push-tags\" but have"
                             + " not specified an \"imageTag\" in your"
                             + " docker-maven-client's plugin configuration";
      assertTrue(String.format("Exception message should have contained '%s'", message),
                 e.getMessage().contains(message));
    }
    verifyNoMoreInteractions(docker);
  }

}
