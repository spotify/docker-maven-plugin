package com.spotify.manymodules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class ImageDependencyTest {

  public static final String IMAGE_INFO_LOCATION =
      "/META-INF/docker/com.spotify.docker.it/with-many-modules-a/image-info.json";

  @Test
  public void testImageAvailable() throws Exception {
    try (InputStream is = ImageDependencyTest.class.getResource(IMAGE_INFO_LOCATION).openStream()) {
      JsonNode jsonNode = new ObjectMapper().readTree(is);
      assertEquals(jsonNode.get("image").asText(), "with-many-modules-a");
    }
  }
}
