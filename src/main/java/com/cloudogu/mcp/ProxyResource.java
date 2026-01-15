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
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson.JacksonJsonSchemaValidatorSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
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
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.EagerSingleton;
import sonia.scm.SCMContextProvider;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Path("mcp")
@EagerSingleton
public class ProxyResource {

  private final Map<Tool, String> tools;
  private final HttpServletStreamableServerTransportProvider transportProvider;
  private final ExceptionHandlingToolExecutorFactory executorFactory;

  @Inject
  public ProxyResource(Set<Tool> tools, ObjectMapper objectMapper, SCMContextProvider scmContextProvider, ExceptionHandlingToolExecutorFactory executorFactory) {
    this.tools = tools.stream()
      .collect(Collectors.toMap(
        tool -> tool,
        Tool::getInputSchema
      ));
    this.executorFactory = executorFactory;
    this.transportProvider = initMcp(objectMapper, scmContextProvider.getVersion());
  }

  @GET
  @Path("")
  public Response handleGet(@Context HttpServletRequest request,
                            @Context HttpServletResponse response) throws ServletException, IOException {
    forwardRequest(request, response);
    return Response.ok().build();
  }

  @POST
  @Path("")
  public Response handlePost(@Context HttpServletRequest request,
                             @Context HttpServletResponse response) throws ServletException, IOException {
    forwardRequest(request, response);
    return Response.ok().build();
  }

  private void forwardRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    log.trace("forward request");
    transportProvider.service(request, response);
    log.trace("returning response with status: {}", response.getStatus());
  }

  private HttpServletStreamableServerTransportProvider initMcp(ObjectMapper objectMapper, String version) {
    McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
    var transportProvider = HttpServletStreamableServerTransportProvider.builder()
      .jsonMapper(jsonMapper)
      .mcpEndpoint("/api/mcp")
      .build();

    McpSyncServer server = buildMcpServer(transportProvider, jsonMapper, version);

    tools.forEach(
      (tool, schema) -> registerTool(tool, schema, server, jsonMapper)
    );

    return transportProvider;
  }

  private static McpSyncServer buildMcpServer(HttpServletStreamableServerTransportProvider transportProvider, McpJsonMapper jsonMapper, String version) {
    return McpServer.sync(transportProvider)
      .serverInfo("scm-manager", version)
      .jsonSchemaValidator(new JacksonJsonSchemaValidatorSupplier().get())
      .jsonMapper(jsonMapper)
      .immediateExecution(true)
      .capabilities(McpSchema.ServerCapabilities.builder()
        .resources(true, false)
        .tools(true)
        .prompts(false)
        .logging()
        .build())
      .build();
  }

  private void registerTool(Tool tool, String schema, McpSyncServer server, McpJsonMapper jsonMapper) {
    log.debug("registering tool {}", tool);
    server.addTool(
      McpServerFeatures.SyncToolSpecification.builder()
        .tool(
          McpSchema.Tool.builder()
            .name(tool.getName())
            .description(tool.getDescription())
            .inputSchema(jsonMapper, schema)
            .build()
        )
        .callHandler(executorFactory.executor(tool))
        .build()
    );
  }
}
