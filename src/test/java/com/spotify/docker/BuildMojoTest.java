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

import com.google.common.collect.ImmutableList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.docker.client.AnsiProgressHandler;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.BuildParam;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.messages.ProgressMessage;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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

import static com.spotify.docker.TestUtils.getPom;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;
import static org.assertj.core.api.Assertions.assertThat;

public class BuildMojoTest extends AbstractMojoTestCase {

  private static final List<String> GENERATED_DOCKERFILE = Arrays.asList(
      "FROM busybox",
      "MAINTAINER user",
      "ENV FOO BAR",
      "WORKDIR /opt/app",
      "ADD resources/parent/child/child.xml resources/parent/child/",
      "ADD resources/parent/escapedollar\\$sign.xml resources/parent/",
      "ADD resources/parent/parent.xml resources/parent/",
      "ADD copy2.json .",
      "RUN ln -s /a /b",
      "RUN wget 127.0.0.1:8080",
      "HEALTHCHECK --interval=30s CMD curl --fail http://localhost:8080/ || exit 1",
      "EXPOSE 8080 8081",
      "USER app",
      "ENTRYPOINT date",
      "CMD [\"-u\"]"
  );

  private static final List<String> GENERATED_DOCKERFILE_WITH_VOLUMES = Arrays.asList(
      "FROM busybox",
      "MAINTAINER user",
      "ENV FOO BAR",
      "WORKDIR /opt/app",
      "ADD resources/parent/child/child.xml resources/parent/child/",
      "ADD resources/parent/escapedollar\\$sign.xml resources/parent/",
      "ADD resources/parent/parent.xml resources/parent/",
      "ADD copy2.json .",
      "RUN ln -s /a /b",
      "RUN wget 127.0.0.1:8080",
      "EXPOSE 8080 8081",
      "USER app",
      "ENTRYPOINT date",
      "CMD [\"-u\"]",
      "VOLUME /example0",
      "VOLUME /example1",
      "VOLUME /example2"
  );

  private static final List<String> GENERATED_DOCKERFILE_WITH_LABELS = Arrays.asList(
      "FROM busybox",
      "MAINTAINER user",
      "ENV FOO BAR",
      "WORKDIR /opt/app",
      "ADD resources/parent/child/child.xml resources/parent/child/",
      "ADD resources/parent/escapedollar\\$sign.xml resources/parent/",
      "ADD resources/parent/parent.xml resources/parent/",
      "ADD copy2.json .",
      "RUN ln -s /a /b",
      "RUN wget 127.0.0.1:8080",
      "EXPOSE 8080 8081",
      "USER app",
      "ENTRYPOINT date",
      "CMD [\"-u\"]",
      "LABEL a=b",
      "LABEL x=\"y\""
  );

  private static final List<String> GENERATED_DOCKERFILE_WITH_SQUASH_COMMANDS = Arrays.asList(
          "FROM busybox",
          "MAINTAINER user",
          "ENV FOO BAR",
          "WORKDIR /opt/app",
          "ADD resources/parent/child/child.xml resources/parent/child/",
          "ADD resources/parent/escapedollar\\$sign.xml resources/parent/",
          "ADD resources/parent/parent.xml resources/parent/",
          "ADD copy2.json .",
          "RUN ln -s /a /b &&\\",
          "\twget 127.0.0.1:8080",
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
      "ADD /xml/pom-build-with-profile.xml /xml/",
      "EXPOSE 8080 8081 8082",
      "ENTRYPOINT date",
      "CMD [\"-u\"]"
  );

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    deleteDirectory("target/docker");
  }

  //tests the docker volumes feature
  public void testBuildWithDockerVolumes() throws Exception {
    final File pom = getPom("/pom-build-docker-volumes.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertFilesCopied();

    assertEquals("wrong dockerfile contents", GENERATED_DOCKERFILE_WITH_VOLUMES,
                 Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));
  }

  public void testBuildWithDockerLabels() throws Exception {
    final File pom = getPom("/pom-build-docker-labels.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertFilesCopied();

    assertEquals("wrong dockerfile contents", GENERATED_DOCKERFILE_WITH_LABELS,
                 Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));
  }

  public void testBuildWithDockerDirectory() throws Exception {
    final File pom = getPom("/pom-build-docker-directory.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertFilesCopied();
  }

  public void testBuildWithDockerDirectoryWithArgs() throws Exception {
    final File pom = getPom("/pom-build-docker-directory-args.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);
    verify(docker).build(
        eq(Paths.get("target/docker")), eq("busybox"),
        any(AnsiProgressHandler.class),
        eq(DockerClient.BuildParam.create("buildargs", "%7B%22VERSION%22%3A%220.1%22%7D")));
    assertFilesCopied();
  }

  public void testBuildWithPush() throws Exception {
    final File pom = getPom("/pom-build-push.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox"), any(AnsiProgressHandler.class));
  }

  public void testBuildWithPushCompositeImageNameNoTag() throws Exception {
    final File pom = getPom("/pom-build-push-composite.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox:compositeNameTag"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:compositeNameTag"), any(AnsiProgressHandler.class));
  }

  public void testDigestWrittenOnBuildWithPush() throws Exception {
    final File pom = getPom("/pom-build-push.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    final String digest =
        "sha256:ebd39c3e3962f804787f6b0520f8f1e35fbd5a01ab778ac14c8d6c37978e8445";
    final ProgressMessage digestProgressMessage = ProgressMessage.builder().status(
        "Digest: " + digest
    ).build();

    doAnswer(new Answer() {
      @Override
      public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
        final ProgressHandler handler = (ProgressHandler) invocationOnMock.getArguments()[1];
        handler.progress(digestProgressMessage);
        return null;
      }
    }).when(docker).push(anyString(), any(ProgressHandler.class));

    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox"), any(AnsiProgressHandler.class));

    assertFileExists(mojo.tagInfoFile);

    final ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode node = objectMapper.readTree(new File(mojo.tagInfoFile));

    assertEquals("busybox@" + digest, node.get("digest").asText());
  }

  public void testDigestWrittenOnBuildWithPushAndExplicitTag() throws Exception {
    final File pom = getPom("/pom-build-push-with-tag.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    final String digest =
        "sha256:ebd39c3e3962f804787f6b0520f8f1e35fbd5a01ab778ac14c8d6c37978e8445";
    final ProgressMessage digestProgressMessage = ProgressMessage.builder().status(
        "Digest: " + digest
    ).build();

    doAnswer(new Answer() {
      @Override
      public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
        final ProgressHandler handler = (ProgressHandler) invocationOnMock.getArguments()[1];
        handler.progress(digestProgressMessage);
        return null;
      }
    }).when(docker).push(anyString(), any(ProgressHandler.class));

    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox:sometag"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:sometag"), any(AnsiProgressHandler.class));

    assertFileExists(mojo.tagInfoFile);

    final ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode node = objectMapper.readTree(new File(mojo.tagInfoFile));

    assertEquals("busybox@" + digest, node.get("digest").asText());
  }

  public void testBuildWithPull() throws Exception {
    final File pom = getPom("/pom-build-pull.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class), any(BuildParam.class));
  }

  public void testBuildWithPushTag() throws Exception {
    final File pom = getPom("/pom-build-push-tag.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:latest"), any(AnsiProgressHandler.class));
  }

  public void testBuildWithMultiplePushTag() throws Exception {
    final File pom = getPom("/pom-build-push-tags.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:late"), any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:later"), any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:latest"), any(AnsiProgressHandler.class));
  }

  public void testBuildWithMultiplePushTagButNoTagsSpecified() throws Exception {
    final File pom = getPom("/pom-build-push-tags-no-tags-provided.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    try {
      mojo.execute(docker);
      fail("mojo should have thrown exception because imageTags are not defined in pom");
    } catch (MojoExecutionException e) {
      final String message = "You have used option \"pushImageTag\" but have"
                             + " not specified an \"imageTag\" in your"
                             + " docker-maven-client's plugin configuration";
      assertTrue(String.format("Exception message should have contained '%s'", message),
                 e.getMessage().contains(message));
    }
  }

  public void testBuildWithMultipleCompositePushTag() throws Exception {
    final File pom = getPom("/pom-build-push-tags-composite.xml");
    assertNotNull("Null pom.xml", pom);
    assertTrue("pom.xml does not exist", pom.exists());

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox:compositeTag"),
                         any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:compositeTag"), any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:late"), any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:later"), any(AnsiProgressHandler.class));
    verify(docker).push(eq("busybox:latest"), any(AnsiProgressHandler.class));
  }

  public void testBuildWithInvalidPushTag() throws Exception {
    final File pom = getPom("/pom-build-missing-push-tags.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    try {
      mojo.execute(docker);
      fail("mojo should have thrown exception because imageTag is not defined in pom");
    } catch (MojoExecutionException e) {
      final String message = "You have used option \"pushImageTag\" but have"
                              + " not specified an \"imageTag\" in your"
                              + " docker-maven-client's plugin configuration";
      assertTrue(String.format("Exception message should have contained '%s'", message),
                 e.getMessage().contains(message));
    }
  }

  public void testBuildWithGeneratedDockerfile() throws Exception {
    final File pom = getPom("/pom-build-generated-dockerfile.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                         any(AnsiProgressHandler.class));
    assertFilesCopied();
    assertEquals("wrong dockerfile contents", GENERATED_DOCKERFILE,
                 Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));
  }

  public void testBuildWithGeneratedDockerfileWithSquashCommands() throws Exception {
      final File pom = getPom(
          "/pom-build-generated-dockerfile-with-squash-commands.xml");

      final BuildMojo mojo = setupMojo(pom);
      final DockerClient docker = mock(DockerClient.class);
      mojo.execute(docker);

      verify(docker).build(eq(Paths.get("target/docker")), eq("busybox"),
                           any(AnsiProgressHandler.class));
      assertFilesCopied();
      assertEquals("wrong dockerfile contents", GENERATED_DOCKERFILE_WITH_SQUASH_COMMANDS,
                   Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));
    }

  public void testBuildGeneratedDockerFile_CopiesEntireDirectory() throws Exception {
    final File pom = getPom("/pom-build-copy-entire-directory.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker).build(eq(Paths.get("target/docker")), eq("test-copied-directory"),
        any(AnsiProgressHandler.class));

    final List<String> expectedDockerFileContents = ImmutableList.of(
        "FROM busybox",
        "ADD /data /data",
        "ENTRYPOINT echo"
    );

    assertEquals("wrong dockerfile contents", expectedDockerFileContents,
        Files.readAllLines(Paths.get("target/docker/Dockerfile"), UTF_8));

    assertFileExists("target/docker/data/file.txt");
    assertFileExists("target/docker/data/nested/file2");
  }

  public void testBuildWithProfile() throws Exception {
    final File pom = getPom("/pom-build-with-profile.xml");

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
    final File pom = getPom("/pom-build-with-invalid-profile.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    try {
      mojo.execute(docker);
      fail("mojo should have thrown exception because ${appName} is not defined in pom");
    } catch (MojoExecutionException e) {
      final String message = "Undefined expression";
      assertTrue(String.format("Exception message should have contained '%s'", message),
                 e.getMessage().contains(message));
    }
  }

  /**
   * Test what happens if tagInfoFile does not contain a path, i.e. the value is simply
   * "image_info.json".
   */
  public void testBuildWithTagInfoFileInSameDirectory() throws Exception {
    final File pom = getPom("/pom-build-with-tagInfoFile.xml");

    final BuildMojo mojo = setupMojo(pom);
    final DockerClient docker = mock(DockerClient.class);

    // test is good if this does not blow up
    mojo.execute(docker);

    final String filePath = mojo.tagInfoFile;
    assertFileExists(filePath);

    new File(filePath).deleteOnExit();
  }

  public void testPullOnBuild() throws Exception {
    final BuildMojo mojo = setupMojo(getPom("/pom-build-pull-on-build.xml"));
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);

    verify(docker).build(any(Path.class),
        anyString(),
        any(ProgressHandler.class),
        eq(BuildParam.pullNewerImage()));
  }

  public void testNoCache() throws Exception {
    final BuildMojo mojo = setupMojo(getPom("/pom-build-no-cache.xml"));
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);

    verify(docker).build(any(Path.class),
        anyString(),
        any(ProgressHandler.class),
        eq(BuildParam.noCache()));
  }

  public void testRmFalse() throws Exception {
    final BuildMojo mojo = setupMojo(getPom("/pom-build-rm-false.xml"));
    final DockerClient docker = mock(DockerClient.class);

    mojo.execute(docker);

    verify(docker).build(any(Path.class),
        anyString(),
        any(ProgressHandler.class),
        eq(BuildParam.rm(false)));
  }

  public void testBuildWithSkipDockerBuild() throws Exception {
    final BuildMojo mojo = setupMojo(getPom("/pom-build-skip-build.xml"));
    assertThat(mojo.isSkipDockerBuild()).isTrue();

    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker, never())
        .build(any(Path.class), anyString(), any(AnsiProgressHandler.class));
    verify(docker, never())
        .push(anyString(), any(AnsiProgressHandler.class));
  }

  public void testBuildWithSkipDocker() throws Exception {
    final BuildMojo mojo = setupMojo(getPom("/pom-build-skip-docker.xml"));
    assertThat(mojo.isSkipDocker()).isTrue();

    final BuildMojo mojoSpy = spy(mojo);
    mojo.execute();
    verify(mojoSpy, never()).execute(any(DockerClient.class));
  }

  public void testBuildWithPushTagAndSkipDockerPush() throws Exception {
    final BuildMojo mojo = setupMojo(getPom("/pom-build-skip-push.xml"));
    assertThat(mojo.isSkipDockerPush()).isTrue();

    final DockerClient docker = mock(DockerClient.class);
    mojo.execute(docker);

    verify(docker)
        .build(eq(Paths.get("target/docker")), eq("busybox"), any(AnsiProgressHandler.class));
    verify(docker, never())
        .push(anyString(), any(AnsiProgressHandler.class));
  }

  private BuildMojo setupMojo(final File pom) throws Exception {
    final MavenProject project = new ProjectStub(pom);
    final MavenSession session = newMavenSession(project);
    // for some reason the superclass method newMavenSession() does not copy properties from the
    // project model to the session. This is needed for the use of ExpressionEvaluator in BuildMojo.
    session.getRequest().setUserProperties(project.getModel().getProperties());

    final MojoExecution execution = newMojoExecution("build");
    final BuildMojo mojo = (BuildMojo) this.lookupConfiguredMojo(session, execution);
    mojo.buildDirectory = "target";
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
    // files including a file with dollar sign because it has to be escaped inside the dockerfile
    assertFileExists("target/docker/resources/parent/escapedollar$sign.xml");
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
