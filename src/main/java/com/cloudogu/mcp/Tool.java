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

import java.util.function.BiFunction;

@ExtensionPoint
public interface Tool {

  String getName();

  String getDescription();

  String getInputSchema();

  BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> getCallHandler();

  default Object getRequiredArgument(McpSchema.CallToolRequest request, String argumentName) {
    if (!request.arguments().containsKey(argumentName)) {
      throw new IllegalArgumentException(String.format("The required argument '%s' is missing.", argumentName));
    }
    return request.arguments().get(argumentName);
  }
}
