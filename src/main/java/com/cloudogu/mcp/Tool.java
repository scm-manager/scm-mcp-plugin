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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import sonia.scm.plugin.ExtensionPoint;

/**
 * An implementation for an MCP tool. Normally, use {@link TypedTool} instead for convenience.
 */
@ExtensionPoint
public interface Tool {

  /**
   * The name of the tool. This has to be a string without spaces.
   */
  String getName();

  /**
   * The description of the tool that should be used by the AI.
   */
  String getDescription();

  /**
   * The core function for this tool that will be executed on each call creating a result that will be returned to the
   * AI.
   */
  McpSchema.CallToolResult execute(McpSyncServerExchange exchange, McpSchema.CallToolRequest request);

  /**
   * The JSON schema for this tool.
   */
  String getInputSchema();
}
