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
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.UnauthorizedException;
import sonia.scm.AlreadyExistsException;
import sonia.scm.ContextEntry;
import sonia.scm.ExceptionWithContext;
import sonia.scm.NotFoundException;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryManager;

import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder;

@Slf4j
final class ExceptionHandlingToolExecutorFactory {

  private final RepositoryManager repositoryManager;

  @Inject
  ExceptionHandlingToolExecutorFactory(RepositoryManager repositoryManager) {
    this.repositoryManager = repositoryManager;
  }

  BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> executor(Tool tool) {
    return (exchange, request) -> {
      try {
        return tool.execute(exchange, request);
      } catch (UnauthorizedException e) {
        log.trace("not authorized", e);
        return builder().addTextContent("The current user does not have the permission to do this.").isError(true).build();
      } catch (NotFoundException e) {
        log.trace("got not found exception", e);
        String context = buildContextString(e);
        return builder().addTextContent("Could not find " + context + ".").isError(true).build();
      } catch (AlreadyExistsException e) {
        log.trace("got already exists exception", e);
        String context = buildContextString(e);
        return builder().addTextContent("There already exists a " + context + ".").isError(true).build();
      } catch (Exception e) {
        log.error("unhandled exception while executing mcp request of class {}", this.getClass(), e);
        return builder().addTextContent("An internal error occurred while executing the request.").isError(true).build();
      }
    };
  }

  private String buildContextString(ExceptionWithContext e) {
    return e.getContext()
      .stream()
      .map(this::formatContextEntry)
      .collect(Collectors.joining(" for "));
  }

  private String formatContextEntry(ContextEntry c) {
    return String.format("%s '%s'", c.getType(), getEntryString(c));
  }

  private String getEntryString(ContextEntry c) {
    if (c.getType().equals("Repository") && !c.getId().contains("/")) {
      Repository repository = repositoryManager.get(c.getId());
      if (repository != null) {
        return repository.getNamespaceAndName().toString();
      }
    }
    return c.getId();
  }
}
