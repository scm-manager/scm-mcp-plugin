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

import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

public record FrontendLinkResult(String targetType, String url, String markdown, Map<String, Object> metadata) {

  public FrontendLinkResult {
    metadata = metadata == null ? emptyMap() : metadata;
  }

  public static FrontendLinkResult of(String targetType, String label, String url, Map<String, Object> metadata) {
    return new FrontendLinkResult(
      targetType,
      url,
      "[" + escapeMarkdownLinkText(label) + "](" + url + ")",
      metadata
    );
  }

  public Map<String, Object> toStructuredContent() {
    Map<String, Object> structuredContent = new LinkedHashMap<>();
    structuredContent.put("targetType", targetType);
    structuredContent.put("url", url);
    structuredContent.put("markdown", markdown);
    structuredContent.put("metadata", metadata);
    return structuredContent;
  }

  private static String escapeMarkdownLinkText(String label) {
    return label.replace("\\", "\\\\").replace("]", "\\]");
  }
}
