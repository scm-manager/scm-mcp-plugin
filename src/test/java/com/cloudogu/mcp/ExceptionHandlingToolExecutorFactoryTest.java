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
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static sonia.scm.AlreadyExistsException.alreadyExists;
import static sonia.scm.ContextEntry.ContextBuilder.entity;
import static sonia.scm.NotFoundException.notFound;

@ExtendWith(MockitoExtension.class)
class ExceptionHandlingToolExecutorFactoryTest {

  @InjectMocks
  private ExceptionHandlingToolExecutorFactory factory;

  @Mock
  private Tool tool;
  @Mock
  private McpSyncServerExchange exchange;
  @Mock
  private McpSchema.CallToolRequest request;
  private BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, CallToolResult> executor;

  @BeforeEach
  void createExecutor() {
    executor = factory.executor(tool);
  }

  @Test
  void shouldCreateExecutor() {
    CallToolResult expectedResult = mock(CallToolResult.class);
    when(tool.execute(exchange, request))
      .thenReturn(expectedResult);

    CallToolResult actualResult = executor.apply(exchange, request);

    verify(tool).execute(exchange, request);
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  @Test
  void shouldHandleNotFoundException() {
    when(tool.execute(exchange, request))
      .thenThrow(notFound(entity("Planet", "Earth").in("Galaxy", "Milky Way")));

    CallToolResult actualResult = executor.apply(exchange, request);

    verify(tool).execute(exchange, request);
    assertThat(actualResult.isError()).isTrue();
    assertThat(actualResult.content())
      .extracting("text")
      .containsExactly("Could not find Planet 'Earth' for Galaxy 'Milky Way'.");
  }

  @Test
  void shouldHandleAlreadyExistsException() {
    when(tool.execute(exchange, request))
      .thenThrow(alreadyExists(entity("Planet", "Earth").in("Galaxy", "Milky Way")));

    CallToolResult actualResult = executor.apply(exchange, request);

    verify(tool).execute(exchange, request);
    assertThat(actualResult.isError()).isTrue();
    assertThat(actualResult.content())
      .extracting("text")
      .containsExactly("There already exists a Planet 'Earth' for Galaxy 'Milky Way'.");
  }

  @Test
  void shouldHandleOtherExceptions() {
    when(tool.execute(exchange, request))
      .thenThrow(new RuntimeException("Something went wrong"));

    CallToolResult actualResult = executor.apply(exchange, request);

    verify(tool).execute(exchange, request);
    assertThat(actualResult.isError()).isTrue();
    assertThat(actualResult.content())
      .extracting("text")
      .containsExactly("An internal error occurred while executing the request.");
  }
}
