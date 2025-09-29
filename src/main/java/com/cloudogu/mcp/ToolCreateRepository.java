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
import sonia.scm.AlreadyExistsException;
import sonia.scm.config.ScmConfiguration;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.NamespaceStrategy;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.web.api.RepositoryToHalMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@Extension
class ToolCreateRepository implements Tool {

  private static final String SCHEMA_WITH_NAMESPACES = """
    {
      "type" : "object",
      "id" : "tools/create-repository",
      "properties" : {
        "namespace" : {
          "type" : "string",
          "description": "The namespace for the new repository"
        },
        "name" : {
          "type" : "string",
          "description": "The name for the new repository",
          "pattern": "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\.][A-Za-z0-9\\\\.\\\\-_]*$"
        },
        "type" : {
          "type" : "string",
          "description": "The type of the new repository. This must be either 'git', 'hg', or 'svn'",
          "enum": ["git", "hg", "svn"],
          "default": "git"
        }
      },
      "required": ["namespace", "name"]
    }
    """;
  private static final String SCHEMA_WITHOUT_NAMESPACES = """
    {
      "type" : "object",
      "id" : "tools/create-repository",
      "properties" : {
        "name" : {
          "type" : "string",
          "description": "The name for the new repository",
          "pattern": "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\.][A-Za-z0-9\\\\.\\\\-_]*$"
        },
        "type" : {
          "type" : "string",
          "description": "The type of the new repository. This must be either 'git', 'hg', or 'svn'",
          "enum": ["git", "hg", "svn"],
          "default": "git"
        }
      },
      "required": ["name"]
    }
    """;

  private final RepositoryManager repositoryManager;
  private final RepositoryToHalMapper repositoryToHalMapper;
  private final ScmConfiguration scmConfiguration;
  private final Set<NamespaceStrategy> strategies;

  @Inject
  public ToolCreateRepository(RepositoryManager repositoryManager, RepositoryToHalMapper repositoryToHalMapper, ScmConfiguration scmConfiguration, Set<NamespaceStrategy> strategies) {
    this.repositoryManager = repositoryManager;
    this.repositoryToHalMapper = repositoryToHalMapper;
    this.scmConfiguration = scmConfiguration;
    this.strategies = strategies;
  }

  @Override
  public String getName() {
    return "create_repository";
  }

  @Override
  public String getDescription() {
    return "Create a new repository";
  }

  @Override
  public String getInputSchema() {
    return isRenameNamespacePossible() ?
      SCHEMA_WITH_NAMESPACES :
      SCHEMA_WITHOUT_NAMESPACES;
  }

  @Override
  public BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> getCallHandler() {
    return (exchange, request) -> {
      String namespace = request.arguments().get("namespace") == null ? null : request.arguments().get("namespace").toString();
      String name = getRequiredArgument(request, "name").toString();
      String type = getRequiredArgument(request, "type", "git");

      try {
        Repository repository = repositoryManager.create(new Repository(null, type, namespace, name));

        return new McpSchema.CallToolResult(
          List.of(new McpSchema.TextContent(repository.getNamespaceAndName().toString())),
          false,
          Map.of("repository", repositoryToHalMapper.map(repository))
        );
      } catch (AlreadyExistsException existsException) {
        return new McpSchema.CallToolResult(
          "A repository with this namespace and name already exists",
          true
        );
      }
    };
  }

  private boolean isRenameNamespacePossible() {
    for (NamespaceStrategy strategy : strategies) {
      if (strategy.getClass().getSimpleName().equals(scmConfiguration.getNamespaceStrategy())) {
        return strategy.canBeChanged();
      }
    }
    return false;
  }
}
