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

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.codehaus.plexus.util.ReaderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom stub implementation of {@link org.apache.maven.project.MavenProject}.
 * <p>
 * Originally taken from <a href="http://bit.ly/1RNnShQ">
 *   Maven Plugin Testing Mechanism</a> guide but adapted for our use case.</p>
 */
public class ProjectStub extends MavenProjectStub {

  public ProjectStub(File pom) {
    final MavenXpp3Reader pomReader = new MavenXpp3Reader();
    Model model;
    try {
      model = pomReader.read(ReaderFactory.newXmlReader(pom));
      setModel(model);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    setGroupId(model.getGroupId());
    setArtifactId(model.getArtifactId());
    setVersion(model.getVersion());
    setName(model.getName());
    setUrl(model.getUrl());
    setPackaging(model.getPackaging());
    setBuild(model.getBuild());

    final List<String> compileSourceRoots = new ArrayList<>();
    compileSourceRoots.add(getBasedir() + "/src/main/java");
    setCompileSourceRoots(compileSourceRoots);

    final List<String> testCompileSourceRoots = new ArrayList<>();
    testCompileSourceRoots.add(getBasedir() + "/src/test/java");
    setTestCompileSourceRoots(testCompileSourceRoots);

    // normalize some expressions
    getBuild().setDirectory("${project.basedir}/target");
    getBuild().setTestOutputDirectory(new File(getBasedir(), "target/classes").getAbsolutePath());
  }
}
