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

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.ArrayList;
import java.util.List;

/**
 * Container class for an image name and its desired tags.
 */
class CompositeImageName {

  private final List<String> imageTags;
  private final String name;

  private CompositeImageName(final String name, final List<String> imageTags) {
    this.name = name;
    this.imageTags = imageTags;
  }

  /**
   * An image name can be a plain image name or in the composite format &lt;name&gt;:&lt;tag&gt; and
   * this factory method makes sure that we get the plain image name as well as all the desired tags
   * for an image, including any composite tag.
   *
   * @param imageName Image name.
   * @param imageTags List of image tags.
   * @return {@link CompositeImageName}
   * @throws MojoExecutionException
   */
  static CompositeImageName create(final String imageName, final List<String> imageTags)
      throws MojoExecutionException {

    final boolean containsTag = containsTag(imageName);

    final String name = containsTag ? StringUtils.substringBeforeLast(imageName, ":") : imageName;
    if (StringUtils.isBlank(name)) {
      throw new MojoExecutionException("imageName not set!");
    }

    final List<String> tags = new ArrayList<>();
    final String tag = containsTag ? StringUtils.substringAfterLast(imageName, ":") : "";
    if (StringUtils.isNotBlank(tag)) {
      tags.add(tag);
    }
    if (imageTags != null) {
      tags.addAll(imageTags);
    }
    if (tags.size() == 0) {
      throw new MojoExecutionException("No tag included in imageName and no imageTags set!");
    }
    return new CompositeImageName(name, tags);
  }

  public String getName() {
    return name;
  }

  public List<String> getImageTags() {
    return imageTags;
  }

  static boolean containsTag(String imageName) {
    if (StringUtils.contains(imageName, ":")) {
      if (StringUtils.contains(imageName, "/")) {
        final String registryPart = StringUtils.substringBeforeLast(imageName, "/");
        final String imageNamePart = StringUtils.substring(imageName, registryPart.length() + 1);

        return StringUtils.contains(imageNamePart, ":");
      } else {
        return true;
      }
    }

    return false;
  }
}
