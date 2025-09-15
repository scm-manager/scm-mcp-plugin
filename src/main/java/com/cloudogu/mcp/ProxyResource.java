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
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.EagerSingleton;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Path("mcp")
@EagerSingleton
public class ProxyResource {

  private final Set<Tool> tools;
  private final HttpServletStreamableServerTransportProvider transportProvider;

  @Inject
  public ProxyResource(Set<Tool> tools, ObjectMapper objectMapper) {
    this.tools = tools;
    this.transportProvider = initMcp(objectMapper);
  }

  private HttpServletStreamableServerTransportProvider initMcp(ObjectMapper objectMapper) {
    var transportProvider = HttpServletStreamableServerTransportProvider.builder()
      .objectMapper(objectMapper)
      .mcpEndpoint("/api/mcp")
      .build();

    var server = McpServer.sync(transportProvider)
      .serverInfo("scm-manager", "0.0.1")
      .immediateExecution(true)
      .capabilities(McpSchema.ServerCapabilities.builder()
        .resources(true, false)
        .tools(true)
        .prompts(false)
        .logging()
        .build())
      .build();

    tools.forEach(
      tool -> server.addTool(
        McpServerFeatures.SyncToolSpecification.builder()
          .tool(
            McpSchema.Tool.builder()
              .name(tool.getName())
              .description(tool.getDescription())
              .inputSchema(tool.getInputSchema())
              .build()
          )
          .callHandler(tool.getCallHandler())
          .build()
      )
    );

    return transportProvider;
  }

  @GET
  @Path("")
  public void handleGet(@Context HttpServletRequest request,
                        @Context HttpServletResponse response) throws ServletException, IOException {
    forwardRequest(request, response);
  }

  @POST
  @Path("")
  public void handlePost(@Context HttpServletRequest request,
                         @Context HttpServletResponse response) throws ServletException, IOException {
    forwardRequest(request, response);
  }

  private void forwardRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      transportProvider.service(request, response);
  }
}
