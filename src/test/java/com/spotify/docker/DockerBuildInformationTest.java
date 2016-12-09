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

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

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

public class DockerBuildInformationTest extends AbstractMojoTestCase {

  private static final List<String> GENERATED_DOCKERFILE = Arrays.asList(
      "FROM busybox",
      "MAINTAINER user",
      "ENTRYPOINT date",
      "CMD [\"-u\"]",
      "ADD resources/parent/child/child.xml resources/parent/child/child.xml",
      "ADD resources/parent/parent.xml resources/parent/parent.xml",
      "ADD copy2.json copy2.json",
      "ENV FOO BAR",
      "EXPOSE 8080 8081"
  );

  private static final List<String> PROFILE_GENERATED_DOCKERFILE = Arrays.asList(
      "FROM busybox",
      "ENTRYPOINT date",
      "CMD [\"-u\"]",
      "ADD /xml/pom-build-with-profile.xml /xml/pom-build-with-profile.xml",
      "ENV APP_NAME FOOBAR",
      "ENV ARTIFACT_ID docker-maven-plugin-test",
      "ENV FOO BAR",
      "ENV FOOZ BARZ",
      "ENV PROPERTY_HELLO HELLO_VALUE",
      "EXPOSE 8080 8081 8082"
  );

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    deleteDirectory("target/image_info.json");
  }

  public void testBuildWithDockerDirectory() throws Exception {
    final String imageName = "my-image";
    final DockerBuildInformation buildInfo =
        new DockerBuildInformation(imageName, new SystemStreamLog());
    assertEquals(imageName, buildInfo.getImage());
    assertEquals("git@github.com:spotify/docker-maven-plugin.git", buildInfo.getRepo());
    assertNotNull(buildInfo.getCommit());
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
