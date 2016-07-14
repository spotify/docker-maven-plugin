/*
 * Copyright (c) 2016 Spotify AB.
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

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.*;

public class CompositeImageNameTest {

  @Test
  public void testStandardCompositeNameWithoutImageTags() throws MojoExecutionException {
    final CompositeImageName
        compositeImageName = CompositeImageName.create("imageName:tagName", null);
    assertEquals("imageName", compositeImageName.getName());
    assertEquals("tagName", compositeImageName.getImageTags().get(0));
  }

  @Test
  public void testStandardCompositeNameWithImageTags() throws MojoExecutionException {
    final List<String> imageTags = Arrays.asList("tag1", "tag2");
    final CompositeImageName
        compositeImageName = CompositeImageName.create("imageName:tagName", imageTags);
    assertEquals("imageName", compositeImageName.getName());
    assertEquals("tagName", compositeImageName.getImageTags().get(0));
    assertEquals("tag1", compositeImageName.getImageTags().get(1));
    assertEquals("tag2", compositeImageName.getImageTags().get(2));
  }

  @Test
  public void testCompositeNameWithoutTag() throws MojoExecutionException {
    final CompositeImageName
        compositeImageName = CompositeImageName.create("imageName", null);
    assertEquals("imageName", compositeImageName.getName());
    assertEquals(1, compositeImageName.getImageTags().size());
    assertEquals("", compositeImageName.getImageTags().get(0));
  }

  @Test
  public void testCompositeNameWithoutTagWithImageTags() throws MojoExecutionException {
    final List<String> imageTags = Arrays.asList("tag1", "tag2");
    final CompositeImageName
        compositeImageName = CompositeImageName.create("imageName", imageTags);
    assertEquals("imageName", compositeImageName.getName());
    assertEquals(3, compositeImageName.getImageTags().size());
    assertEquals("", compositeImageName.getImageTags().get(0));
    assertEquals("tag1", compositeImageName.getImageTags().get(1));
    assertEquals("tag2", compositeImageName.getImageTags().get(2));
  }

  @Test
  public void testInvalidCompositeImageName() throws MojoExecutionException {
    compositeImageNameExpectsException(null);
    compositeImageNameExpectsException("");
    compositeImageNameExpectsException(":tagname");
  }

  private void compositeImageNameExpectsException(final String imageName) {
    try {
      CompositeImageName.create(imageName, null);
      fail("Should have thrown exception because ${imageName} is not defined");
    } catch (MojoExecutionException ex) {
      final String message = "imageName not set";
      assertTrue(String.format("Exception message should have contained '%s'", message),
                 ex.getMessage().contains(message));
    }
  }

}
