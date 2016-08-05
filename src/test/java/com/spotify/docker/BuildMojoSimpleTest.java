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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tests for {@link BuildMojo} pertaining to the simplest of resources which adds the build
 * artifact from the project as defined by the maven property {@code project.build.finalName}.
 */
public class BuildMojoSimpleTest extends AbstractBuildMojoTest {

  private static final List<String> GENERATED_DOCKERFILE_WITH_VOLUMES = Arrays.asList(
      "FROM busybox",
      "ADD /docker-artifact.jar /"
  );

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    deleteDirectory("target/docker");
    deleteDirectory("target/docker-temp");

    final File dockerTemp = new File("target/docker-temp");
    assertTrue("failed to create directory", dockerTemp.mkdirs());
    final File file = new File(dockerTemp, "docker-artifact.jar");
    assertTrue("failed to create dummy maven artifact", file.createNewFile());
  }

  //tests the docker volumes feature
  public void testBuildWithDockerSimple() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build-docker-simple.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
        any(AnsiProgressHandler.class));

    assertFilesCopied(
        "target/docker/Dockerfile",
        "target/docker/docker-artifact.jar"
    );

    assertEquals("wrong dockerfile contents", GENERATED_DOCKERFILE_WITH_VOLUMES,
        Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));
  }

}
