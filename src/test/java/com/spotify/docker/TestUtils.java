package com.spotify.docker;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class TestUtils {

  private TestUtils() {
  }

  public static File getPomAndAssertExists(String filename) throws Exception {
    File pom = new File(TestUtils.class.getResource(filename).toURI());
    assertThat(pom).isNotNull().exists();
    return pom;
  }
}
