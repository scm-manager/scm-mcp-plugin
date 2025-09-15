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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import sonia.scm.plugin.Extension;

@Extension
public class McpModule extends ServletModule {

  @Override
  protected void configureServlets() {
    System.out.println("configure McpModule");

    // configure transport provider
    var transportProvider = HttpServletStreamableServerTransportProvider.builder()
      .objectMapper(new ObjectMapper())
      .mcpEndpoint("/api/mcp")
      .build();

    // configure mcp server
    var server = McpServer.sync(transportProvider)
      .serverInfo("scm-manager", "0.0.1")
      .capabilities(McpSchema.ServerCapabilities.builder()
        .resources(true, false)
        .tools(true)
        .prompts(false)
        .logging()
        .build())
      .build();

    var schema = """
            {
              "type" : "object",
              "id" : "urn:jsonschema:Operation",
              "properties" : {
                "operation" : {
                  "type" : "string"
                },
                "a" : {
                  "type" : "number"
                },
                "b" : {
                  "type" : "number"
                }
              }
            }
            """;

    var syncToolSpecification = new McpServerFeatures.SyncToolSpecification(
      new McpSchema.Tool("calculator", "Basic calculator", schema),
      (exchange, arguments) -> {

        System.out.println(arguments);
        return new McpSchema.CallToolResult("42", false);
      }
    );

    server.addTool(syncToolSpecification);

    bind(HttpServletStreamableServerTransportProvider.class).toInstance(transportProvider);
  }
}
