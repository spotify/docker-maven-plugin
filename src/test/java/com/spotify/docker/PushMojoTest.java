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
import com.spotify.docker.client.messages.RegistryAuth;
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

public class PushMojoTest extends AbstractMojoTestCase {

  public void testPush() throws Exception {
    final File pom = getPom("/pom-push.xml");

    final PushMojo mojo = (PushMojo) lookupMojo("push", pom);
    assertNotNull(mojo);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);
    verify(docker).push(eq("busybox"), any(AnsiProgressHandler.class));
  }

  public void testFailingPushWithRetries() throws Exception {
    final File pom = getPom("/pom-push.xml");

    final PushMojo mojo = (PushMojo) lookupMojo("push", pom);
    assertNotNull(mojo);

    final DockerClient docker = mock(DockerClient.class);
    doThrow(new DockerException("Expected")).when(docker).push(any(String.class),
                                                               any(AnsiProgressHandler.class));
    verifyException(mojo, DockerException.class).execute(docker);
    verify(docker, times(4)).push(eq("busybox"), any(AnsiProgressHandler.class));
  }

  public void testPushPrivateRepo() throws Exception {
    final File pom = getPom("/pom-push-private-repo.xml");

    final PushMojo mojo = (PushMojo) lookupMojo("push", pom);
    assertNotNull(mojo);

    final RegistryAuth authConfig = mojo.registryAuth();
    assertEquals("dxia3", authConfig.username());
    assertEquals("SxpxdUQA2mvX7oj", authConfig.password()); // verify decryption
    assertEquals("dxia+3@spotify.com", authConfig.email());

    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker)
        .push(eq("dxia3/docker-maven-plugin-auth"), any(AnsiProgressHandler.class));
  }

  public void testPushSkipPush() throws Exception {

    final PushMojo mojo = (PushMojo) lookupMojo("push",
        getPom("/pom-push-skip-push.xml"));
    assertThat(mojo).isNotNull();
    assertThat(mojo.isSkipDockerPush()).isTrue();

   final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker, never())
        .push(anyString(), any(AnsiProgressHandler.class));
  }

  public void testPushSkipDocker() throws Exception {
    final PushMojo mojo = (PushMojo) lookupMojo("push",
        getPom("/pom-push-skip-docker.xml"));
    assertThat(mojo).isNotNull();
    assertThat(mojo.isSkipDocker()).isTrue();

    final PushMojo mojoSpy = spy(mojo);
    mojo.execute();

    verify(mojoSpy, never()).execute(any(DockerClient.class));
  }

}
