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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.auth.RegistryAuthSupplier;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.messages.RegistryAuth;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class AbstractDockerMojoTest {

  private static final String DOCKER_HOST = "testhost";
  private static final String DOCKER_CERT_PATH = "src/test/resources/certs";
  private static final String SERVER_ID = "testId";
  private static final String REGISTRY_URL = "my.docker.reg";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String CONFIGURATION_PROPERTY = "configuration";
  private static final String EMAIL_PROPERTY = "email";
  private static final String EMAIL = "user@host.domain";

  @Mock
  private Settings settings;

  @Mock
  private DefaultDockerClient.Builder builder;

  @Captor
  private ArgumentCaptor<RegistryAuthSupplier> authSupplierCaptor;

  @InjectMocks
  private AbstractDockerMojo sut = new AbstractDockerMojo() {
    @Override
    protected void execute(DockerClient dockerClient) throws Exception {

    }

    @Override
    protected DefaultDockerClient.Builder getBuilder() throws DockerCertificateException {
      return builder;
    }
  };

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testDockerHostSet() throws Exception {
    ReflectionTestUtils.setField(sut, "dockerHost", DOCKER_HOST);
    ReflectionTestUtils.setField(sut, "dockerCertPath", DOCKER_CERT_PATH);

    sut.execute();

    verify(builder).uri(DOCKER_HOST);
    verify(builder).dockerCertificates(any(DockerCertificates.class));

  }

  @Test
  public void testAuthorizationConfiguration() throws Exception {
    ReflectionTestUtils.setField(sut, "serverId", SERVER_ID);

    when(settings.getServer(SERVER_ID)).thenReturn(mockServer());
    when(builder.registryAuthSupplier(authSupplierCaptor.capture())).thenReturn(builder);

    sut.execute();

    final RegistryAuthSupplier supplier = authSupplierCaptor.getValue();

    // we can't inspect the supplier details itself, but we can test that the instance passed
    // to the constructor returns our static RegistryAuth when asked for it
    final RegistryAuth authConfig = supplier.authForBuild().configs().get(SERVER_ID);

    assertThat(authConfig).isNotNull();
    assertThat(authConfig.email()).isEqualTo(EMAIL);
    assertThat(authConfig.password()).isEqualTo(PASSWORD);
    assertThat(authConfig.username()).isEqualTo(USERNAME);
    assertThat(authConfig.serverAddress()).isEqualTo(SERVER_ID);
  }

  @Test
  public void testAuthorizationConfigurationWithServerAddress() throws Exception {
    ReflectionTestUtils.setField(sut, "serverId", SERVER_ID);
    ReflectionTestUtils.setField(sut, "registryUrl", REGISTRY_URL);

    when(settings.getServer(SERVER_ID)).thenReturn(mockServer());
    when(builder.registryAuthSupplier(authSupplierCaptor.capture())).thenReturn(builder);

    sut.execute();

    final RegistryAuthSupplier supplier = authSupplierCaptor.getValue();

    final String image = REGISTRY_URL + "/foo/bar:blah";
    final RegistryAuth authConfig = supplier.authFor(image);

    assertThat(authConfig).isNotNull();
    assertThat(authConfig.email()).isEqualTo(EMAIL);
    assertThat(authConfig.password()).isEqualTo(PASSWORD);
    assertThat(authConfig.username()).isEqualTo(USERNAME);
    assertThat(authConfig.serverAddress()).isEqualTo(REGISTRY_URL);
  }

  private Server mockServer() {
    final Server server = new Server();
    server.setUsername(USERNAME);
    server.setPassword(PASSWORD);

    final Xpp3Dom email = new Xpp3Dom(EMAIL_PROPERTY);
    email.setValue(EMAIL);

    final Xpp3Dom configuration = new Xpp3Dom(CONFIGURATION_PROPERTY);
    configuration.addChild(email);

    server.setConfiguration(configuration);

    return server;
  }
}
