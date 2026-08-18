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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.SCMContextProvider;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
  class ProxyResourceTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse servletResponse;
    @Mock
    private SCMContextProvider scmContextProvider;
    @Mock
    private ExceptionHandlingToolExecutorFactory executorFactory;

    private ProxyResource resource;

    @BeforeEach
    void setUpResource() {
      when(scmContextProvider.getVersion()).thenReturn("1.0.0");
      resource = new ProxyResource(
        Set.of(),
        new ObjectMapper(),
        scmContextProvider,
        executorFactory
      );
    }

    @Test
    void shouldReturnStatusFromGetResponse() throws Exception {
      mockRequestWithStatus("GET", HttpServletResponse.SC_NOT_FOUND);

      Response response = resource.handleGet(request, servletResponse);

      verify(servletResponse).sendError(HttpServletResponse.SC_NOT_FOUND);
      assertThat(response.getStatus())
        .isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void shouldReturnStatusFromPostResponse() throws Exception {
      mockRequestWithStatus("POST", HttpServletResponse.SC_NOT_FOUND);

      Response response = resource.handlePost(request, servletResponse);

      verify(servletResponse).sendError(HttpServletResponse.SC_NOT_FOUND);
      assertThat(response.getStatus())
        .isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    private void mockRequestWithStatus(String method, int status) {
      when(request.getMethod()).thenReturn(method);
      when(request.getRequestURI()).thenReturn("/not-the-mcp-endpoint");
      when(servletResponse.getStatus()).thenReturn(status);
    }
  }
