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

import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;

import java.io.IOException;

/**
 * A JAX-RS resource that acts as a proxy, forwarding requests
 * to another servlet.
 */
@Path("mcp")
public class ProxyResource {

  // The path to the target servlet within the web application.
  private static final String TARGET_SERVLET_PATH = "/internal/legacy-servlet";

  private HttpServletStreamableServerTransportProvider sampleServlet;

  @Inject
  public ProxyResource(HttpServletStreamableServerTransportProvider sampleServlet) {
    this.sampleServlet = sampleServlet;
  }

  /**
   * Handles GET requests and forwards them.
   * The "{subpath:.*}" captures the entire path after /my-proxy/
   */
  @GET
  @Path("")
  public void handleGet(
    @Context HttpServletRequest request,
    @Context HttpServletResponse response) throws ServletException, IOException {

    forwardRequest(request, response);
  }

  @POST
  @Path("")
  public void handlePost(
    @Context HttpServletRequest request,
    @Context HttpServletResponse response) throws ServletException, IOException {

    forwardRequest(request, response);
  }

  /**
   * The core forwarding logic.
   */
  private void forwardRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    sampleServlet.service(request, response);
  }
}
