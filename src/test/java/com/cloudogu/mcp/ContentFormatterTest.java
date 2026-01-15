/*
 * Copyright (c) 2020 - present Cloudogu GmbH
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */

package com.cloudogu.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentFormatterTest {

  public static final List<String> CONTENT = List.of(
    "# Heart of Gold",
    "",
    "A spacecraft equipped with",
    "Infinite Improbability Drive."
  );

  @Test
  void shouldFormatCode() {
    ContentFormatter formatter = new ContentFormatter("README.md");

    OkResultRenderer resultRenderer = formatter.writeComplete(CONTENT, "all ok");

    assertThat(resultRenderer).asString()
      .isEqualTo("""
        STATUS: [COMPLETE] Showing all lines 1-4 of `README.md`.
        INFO: all ok
        ---------------------------------------------------------
        ```
        1 | # Heart of Gold
        2 |\s
        3 | A spacecraft equipped with
        4 | Infinite Improbability Drive.
        ```
        """);
  }

  @Test
  void shouldFormatCodeWithStart() {
    ContentFormatter formatter = new ContentFormatter("README.md");

    OkResultRenderer resultRenderer = formatter.write(ContentFormatter.Status.COMPLETE, CONTENT, 8);

    assertThat(resultRenderer).asString()
      .isEqualTo("""
        STATUS: [COMPLETE] Showing all lines 8-11 of `README.md`.
        ---------------------------------------------------------
        ```
         8 | # Heart of Gold
         9 |\s
        10 | A spacecraft equipped with
        11 | Infinite Improbability Drive.
        ```
        """);
  }

  @Test
  void shouldFormatInfo() {
    ContentFormatter formatter = new ContentFormatter("README.md");

    OkResultRenderer resultRenderer = formatter.writeComplete(CONTENT, "This is just a test.");

    assertThat(resultRenderer).asString()
      .startsWith("""
        STATUS: [COMPLETE] Showing all lines 1-4 of `README.md`.
        INFO: This is just a test.
        """);
  }
}
