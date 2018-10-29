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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;

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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for testing {@link BuildMojo}.
 */
public abstract class AbstractBuildMojoTest extends AbstractMojoTestCase {
  /**
   * Assert that the file exists.
   *
   * @param path the file path
   */
  protected static void assertFileExists(String path) {
    assertTrue(path + " does not exist", new File(path).exists());
  }

  /**
   * Assert that the files exist.
   *
   * @param files the files
   */
  protected static void assertFilesCopied(String... files) {
    for (final String file : files) {
      assertFileExists(file);
    }
    assertNoOtherFilesExist(files);
  }

  private static void assertNoOtherFilesExist(String[] files) {
    final DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(".");
    scanner.setIncludes("target/docker/*", "target/docker/**/*");
    scanner.scan();

    final String[] filesAdded = scanner.getIncludedFiles();
    Arrays.sort(files);
    Arrays.sort(filesAdded);

    assertThat(files)
        .isEqualTo(filesAdded);
  }

  /**
   * Deleter of files.
   */
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

  protected BuildMojo setupMojo(File pom) throws Exception {
    final MavenProject project = new ProjectStub(pom);
    final MavenSession session = newMavenSession(project);
    // for some reason the superclass method newMavenSession() does not copy properties from the
    // project model to the session. This is needed for the use of ExpressionEvaluator in BuildMojo.
    session.getRequest().setUserProperties(project.getModel().getProperties());

    final MojoExecution execution = newMojoExecution("build");
    final BuildMojo mojo = (BuildMojo) this.lookupConfiguredMojo(session, execution);
    mojo.buildDirectory = "target";
    mojo.encoding = project.getModel().getProperties().getProperty("project.build.sourceEncoding");
    // Because test poms are loaded from test/resources, tagInfoFile will default to
    // test/resources/target/image_info.json. Writing the json file to that location will fail
    // because target doesn't exist. So force it to use project's target directory.
    // But don't overwrite it if a test sets a non-default value.
    if (mojo.tagInfoFile.contains("src/test/resources")) {
      mojo.tagInfoFile = "target/image_info.json";
    }
    mojo.session = session;
    mojo.execution = execution;
    return mojo;
  }

  protected void deleteDirectory(String directory) throws IOException {
    final Path path = Paths.get(directory);
    if (Files.exists(path)) {
      Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
          Integer.MAX_VALUE, new FileDeleter());
    }
  }
}
