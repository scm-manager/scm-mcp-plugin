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
import org.apache.shiro.authz.UnauthorizedException;
import sonia.scm.NotFoundException;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.RepositoryPermissions;
import sonia.scm.repository.api.ModifyCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Extension
class ToolCreateOrEditFiles implements Tool {

  private static final String SCHEMA = """
    {
      "type" : "object",
      "id" : "tools/create-or-edit-files",
      "properties" : {
        "namespace" : {
          "type" : "string",
          "description": "The namespace for the new repository"
        },
        "name" : {
          "type" : "string",
          "description": "The name for the new repository",
          "pattern": "(?!^\\\\\\\\.\\\\\\\\.$)(?!^\\\\\\\\.$)(?!.*[\\\\\\\\\\\\\\\\\\\\\\\\[\\\\\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\\\\\.][A-Za-z0-9\\\\\\\\.\\\\\\\\-_]*$"
        },
        "files": {
          "type": "array",
          "description": "A list of files to create or edit.",
          "items": {
            "type": "object",
            "properties": {
              "path": {
                "type": "string",
                "description": "The path for the file that needs to be created or edited."
              },
              "content": {
                "type": "string",
                "description": "The content for the file."
              }
            },
            "required": ["path", "content"]
          }
        },
        "commitMessage": {
          "type" : "string",
          "description": "Commit message for the change",
          "default": "Change by MCP server"
        }
      },
      "required": ["namespace", "name", "files", "commitMessage"]
    }
    """;

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  public ToolCreateOrEditFiles(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public String getName() {
    return "create_or_edit_files";
  }

  @Override
  public String getDescription() {
    return "Create or edit Files";
  }

  @Override
  public String getInputSchema() {
    return SCHEMA;
  }

  @Override
  public BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> getCallHandler() {
    return this::createOrEditFile;
  }

  private McpSchema.CallToolResult createOrEditFile(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
    String namespace = getRequiredArgument(request, "namespace").toString();
    String name = getRequiredArgument(request, "name").toString();
    Object filesObj = getRequiredArgument(request, "files");
    if (!(filesObj instanceof List<?> files)) {
      return new McpSchema.CallToolResult("The 'files' argument must be a list.", true);
    }
    if (files.isEmpty()) {
      return new McpSchema.CallToolResult("The 'files' list must not be empty.", true);
    }
    String commitMessage = getRequiredArgument(request, "commitMessage", "Change by MCP server");

    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(namespace, name))) {
      RepositoryPermissions.push(repositoryService.getRepository()).check();
      ModifyCommandBuilder modifyCommandBuilder = repositoryService.getModifyCommand()
        .setCommitMessage(commitMessage);
      for (Object fileObj : files) {
        if (!(fileObj instanceof Map<?, ?> file)) {
          return new McpSchema.CallToolResult("Each item in the 'files' list must be an object.", true);
        }
        if (!file.containsKey("path") || !file.containsKey("content")) {
          return new McpSchema.CallToolResult("Each file object must contain 'path' and 'content' properties.", true);
        }
        String path = file.get("path").toString();
        String content = file.get("content").toString();
        modifyCommandBuilder
          .createFile(path)
          .setOverwrite(true)
          .withData(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
      }
      modifyCommandBuilder
        .execute();
    } catch (NotFoundException e) {
      return new McpSchema.CallToolResult(
        String.format("The requested repository %s/%s was not found", namespace, name),
        true
      );
    } catch (IOException e) {
      return new McpSchema.CallToolResult(
        "Something went wrong reading or writing the files",
        true
      );
    } catch (UnauthorizedException e) {
      return new McpSchema.CallToolResult("User is not authorized to use this resource", true);
    }

    return new McpSchema.CallToolResult("The files have been successfully created / edited",false);
  }
}
