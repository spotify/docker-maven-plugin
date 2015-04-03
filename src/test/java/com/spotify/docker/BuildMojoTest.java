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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;

import java.io.File;
import java.io.IOException;
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

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BuildMojoTest extends AbstractMojoTestCase {

  private static final List<String> GENERATED_DOCKERFILE = Arrays.asList(
      "FROM busybox",
      "MAINTAINER user",
      "ENV FOO BAR",
      "WORKDIR /opt/app",
      "ADD resources/parent/child/child.xml resources/parent/child/child.xml",
      "ADD resources/parent/parent.xml resources/parent/parent.xml",
      "ADD copy2.json copy2.json",
      "RUN ln -s /a /b",
      "RUN wget 127.0.0.1:8080",
      "EXPOSE 8080 8081",
      "USER app",
      "ENTRYPOINT date",
      "CMD [\"-u\"]"
  );

  private static final List<String> PROFILE_GENERATED_DOCKERFILE = Arrays.asList(
      "FROM busybox",
      "ENV APP_NAME FOOBAR",
      "ENV ARTIFACT_ID docker-maven-plugin-test",
      "ENV FOO BAR",
      "ENV FOOZ BARZ",
      "ENV PROPERTY_HELLO HELLO_VALUE",
      "ADD /xml/pom-build-with-profile.xml /xml/pom-build-with-profile.xml",
      "EXPOSE 8080 8081 8082",
      "ENTRYPOINT date",
      "CMD [\"-u\"]"
  );

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    deleteDirectory("target/docker");
  }

  public void testBuildWithDockerDirectory() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build-docker-directory.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertFilesCopied();
  }

  public void testBuildWithPush() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build-push.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox"), any(AnsiProgressHandler.class));
  }

  public void testBuildWithGeneratedDockerfile() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build-generated-dockerfile.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertFilesCopied();
    assertEquals("wrong dockerfile contents", GENERATED_DOCKERFILE,
                 Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));
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
    assertFileExists("target/docker/xml/pom-build-with-profile.xml");
    assertFileExists("target/docker/Dockerfile");
    assertEquals("wrong dockerfile contents", PROFILE_GENERATED_DOCKERFILE,
                 Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));
  }

  public void testBuildWithInvalidProfile() throws Exception {
    final File pom = getTestFile("src/test/resources/pom-build-with-invalid-profile.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    try {
      mojo.execute(docker);
      fail("mojo should have thrown exception because ${appName} is not defined in pom");
    } catch (MojoExecutionException e) {
      final String message = "Undefined expression";
      assertTrue(format("Exception message should have contained '%s'", message),
                 e.getMessage().contains(message));
    }
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

  private static void assertFilesCopied() {
    // the Dockerfile should have been copied, or generated if no docker directory was specified
    assertFileExists("target/docker/Dockerfile");

    // files from resources/copy1
    assertFileExists("target/docker/resources/parent/parent.xml");
    assertFileExists("target/docker/resources/parent/child/child.xml");
    assertFileDoesNotExist("target/docker/resources/parent/parent.json");
    assertFileDoesNotExist("target/docker/resources/parent/child/child-exclude.xml");

    // file from resources/copy2
    assertFileExists("target/docker/copy2.json");
  }

  private static void assertFileExists(final String path) {
    assertTrue(path + " does not exist", new File(path).exists());
  }

  private static void assertFileDoesNotExist(final String path) {
    assertFalse(path + "exists but should not", new File(path).exists());
  }

}
