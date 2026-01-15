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

import java.util.List;
import java.util.Map;

public class OkResultRenderer {

  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String DIVIDER = "---------------------------------------------------------\n";

  private final StringBuilder result = new StringBuilder();
  private boolean infoSet = false;
  private boolean resultStarted = false;

  public static OkResultRenderer success(String statusText) {
    return new OkResultRenderer(STATUS_SUCCESS, statusText);
  }

  public static PostponedResultRenderer postponedStatus() {
    OkResultRenderer resultRenderer = new OkResultRenderer(null, null);
    return resultRenderer.new PostponedResultRenderer();
  }

  public static OkResultRenderer ok(String status, String statusText) {
    return new OkResultRenderer(status, statusText);
  }

  private OkResultRenderer(String status, String statusText) {
    if (status != null) {
      result.append("STATUS: [").append(status).append("] ").append(statusText).append("\n");
    }
  }

  public OkResultRenderer withInfoText(String infoText) {
    if (infoSet) {
      throw new IllegalStateException("Info is already set");
    }
    if (resultStarted) {
      throw new IllegalStateException("Result is already set");
    }
    this.infoSet = true;
    result.append("INFO: ").append(infoText).append('\n');
    return this;
  }

  public OkResultRenderer appendLine(String line) {
    append(line).append("\n");
    return this;
  }

  public OkResultRenderer append(Object part) {
    if (!resultStarted) {
      result.append(DIVIDER);
      resultStarted = true;
    }
    result.append(part);
    return this;
  }

  public ToolResult render() {
    return ToolResult.ok(result.toString());
  }

  public ToolResult render(Map<String, Object> structuredContent) {
    return ToolResult.ok(
      List.of(result.toString()),
      structuredContent
    );
  }

  @Override
  public String toString() {
    return result.toString();
  }

  public class PostponedResultRenderer {

    public PostponedResultRenderer withInfoText(String infoText) {
      OkResultRenderer.this.withInfoText(infoText);
      return this;
    }

    public PostponedResultRenderer appendLine(String line) {
      OkResultRenderer.this.appendLine(line);
      return this;
    }

    public PostponedResultRenderer append(Object part) {
      OkResultRenderer.this.append(part);
      return this;
    }

    public OkResultRenderer withSuccess(String statusText) {
      return withStatus(STATUS_SUCCESS, statusText);
    }

    public OkResultRenderer withStatus(String status, String statusText) {
      result.insert(0, "STATUS: [" + status + "] " + statusText + "\n");
      return OkResultRenderer.this;
    }
  }
}
