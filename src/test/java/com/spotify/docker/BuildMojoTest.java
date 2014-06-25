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

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BuildMojoTest extends AbstractMojoTestCase {

  private static final List<String> GENERATED_DOCKERFILE = Arrays.asList(
      "FROM busybox",
      "MAINTAINER user",
      "ENTRYPOINT date",
      "CMD [\"-u\"]",
      "ADD /xml/pom-build3.xml /xml/pom-build3.xml",
      "ADD copy-test.json copy-test.json",
      "ENV FOO BAR"
  );

  private static final List<String> PROFILE_GENERATED_DOCKERFILE = Arrays.asList(
      "FROM busybox",
      "ENTRYPOINT date",
      "CMD [\"-u\"]",
      "ADD /xml/pom-build-with-profile.xml /xml/pom-build-with-profile.xml",
      "ENV ARTIFACT_ID docker-maven-plugin-test",
      "ENV FOO BAR",
      "ENV FOOZ BARZ",
      "ENV PROPERTY_HELLO HELLO_VALUE"
  );

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    deleteDirectory("target/docker");
  }

  public void testBuild1() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build1.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertTrue("missing target/docker/Dockerfile", new File("target/docker/Dockerfile").exists());
    assertTrue("missing target/docker/testFile", new File("target/docker/testFile").exists());
    assertTrue("missing target/docker/resources/copy-test.xml",
               new File("target/docker/resources/copy-test.xml").exists());
    assertTrue("missing target/docker/resources/pom-build1.xml",
               new File("target/docker/resources/pom-build1.xml").exists());
    assertTrue("missing target/docker/file", new File("target/docker/file").exists());
  }

  public void testBuild2() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build2.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox"), any(AnsiProgressHandler.class));
  }

  public void testBuild3() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build3.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertTrue("missing target/docker/Dockerfile", new File("target/docker/Dockerfile").exists());
    assertEquals("wrong dockerfile contents", GENERATED_DOCKERFILE,
                 Files.readAllLines(Paths.get("target/docker/Dockerfile"), StandardCharsets.UTF_8));
    assertTrue("missing target/docker/xml/pom-build3.xml",
               new File("target/docker/xml/pom-build3.xml").exists());
    assertTrue("missing target/docker/copy-test.json",
               new File("target/docker/copy-test.json").exists());
  }

  public void testBuildWithProfile() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build-with-profile.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")),
                         eq("docker-maven-plugin-test"),
                         any(AnsiProgressHandler.class));
    assertTrue("missing target/docker/Dockerfile", new File("target/docker/Dockerfile").exists());
    assertEquals("wrong dockerfile contents", PROFILE_GENERATED_DOCKERFILE,
                 Files.readAllLines(Paths.get("target/docker/Dockerfile"), StandardCharsets.UTF_8));
    assertTrue("missing target/docker/xml/pom-build-with-profile.xml",
               new File("target/docker/xml/pom-build-with-profile.xml").exists());
  }

  private BuildMojo setupMojo(final File pom) throws Exception {
    final MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
    final ProjectBuildingRequest buildingRequest = executionRequest.getProjectBuildingRequest();
    final ProjectBuilder projectBuilder = this.lookup(ProjectBuilder.class);
    final MavenProject project = projectBuilder.build(pom, buildingRequest).getProject();
    final MavenSession session = newMavenSession(project);
    final MojoExecution execution = newMojoExecution("build");
    final BuildMojo mojo = (BuildMojo) this.lookupConfiguredMojo(session, execution);
    mojo.buildDirectory = "target";
    // Because test poms are loaded from test/resources, tagInfoFile will default to
    // test/resources/target/image_info.json. Writing the json file to that location will fail
    // because target doesn't exist. So force it to use project's target directory.
    mojo.tagInfoFile = "target/image_info.json";
    mojo.session = session;
    mojo.execution = execution;
    return mojo;
  }

  private void deleteDirectory(String directory) throws IOException {
    final Path path = Paths.get(directory);
    if (Files.exists(path)) {
      Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                         Integer.MAX_VALUE, new FileDeleter());
    }
  }

  private static class FileDeleter extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      Objects.requireNonNull(file);
      Files.delete(file);
      return FileVisitResult.CONTINUE;
    }
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      if (exc == null) {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
      // directory iteration failed, propagate exception
      throw exc;
    }
  }

}
