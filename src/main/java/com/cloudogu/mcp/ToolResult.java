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

import lombok.Getter;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Getter
public class ToolResult {
  private final boolean error;
  private final String message;
  private final List<String> content;
  private final Map<String, Object> structuredContent;

  public static ToolResult ok(String content) {
    return new ToolResult(false, null, List.of(content), emptyMap());
  }

  public static ToolResult ok(List<String> content, Map<String, Object> structuredContent) {
    return new ToolResult(false, null, content, structuredContent);
  }

  public static ToolResult error(String message) {
    return new ToolResult(true, message, emptyList(), emptyMap());
  }

  private ToolResult(boolean error, String message, List<String> content, Map<String, Object> structuredContent) {
    this.error = error;
    this.message = message;
    this.content = content;
    this.structuredContent = structuredContent;
  }
}
