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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Extension
class ToolCreateOrEditFiles implements TypedTool<CreateOrEditFilesInput> {

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  public ToolCreateOrEditFiles(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public String getName() {
    return "create-or-edit-files";
  }

  @Override
  public String getDescription() {
    return "Create or edit Files";
  }

  @Override
  public Class<CreateOrEditFilesInput> getInputClass() {
    return CreateOrEditFilesInput.class;
  }

  @Override
  public ToolResult execute(CreateOrEditFilesInput input) {
    log.trace("executing request {}", input);
    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      RepositoryPermissions.push(repositoryService.getRepository()).check();
      ModifyCommandBuilder modifyCommandBuilder = repositoryService.getModifyCommand()
        .setCommitMessage(input.getCommitMessage());
      for (FileEntry file : input.getFiles()) {
        modifyCommandBuilder
          .createFile(file.getPath())
          .setOverwrite(true)
          .withData(new ByteArrayInputStream(file.getContent().getBytes(StandardCharsets.UTF_8)));
      }
      modifyCommandBuilder
        .execute();
    } catch (NotFoundException e) {
      log.trace("requested repository not found");
      return ToolResult.error(
        String.format("The requested repository %s/%s was not found", input.getNamespace(), input.getName())
      );
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading or writing the files"
      );
    } catch (UnauthorizedException e) {
      log.trace("requested repository not authorized");
      return ToolResult.error("User is not authorized to use this resource");
    }

    log.trace("files created");
    return ToolResult.ok("The files have been successfully created / edited");
  }
}

@Getter
@ToString(exclude = "commitMessage")
class CreateOrEditFilesInput {
  @NotNull
  @JsonPropertyDescription("The namespace for the new repository")
  private String namespace;

  @NotNull
  @JsonPropertyDescription("The name for the new repository")
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  private String name;

  @NotNull
  @JsonPropertyDescription("A list of files to create or edit.")
  @Valid // Important: This tells the validator to validate the objects INSIDE the list
  private List<FileEntry> files;

  @NotNull
  @JsonPropertyDescription("Commit message for the change")
  private String commitMessage = "Change by MCP server";
}

@Getter
@ToString(exclude = "content")
class FileEntry {
  @NotNull
  @JsonPropertyDescription("The path for the file that needs to be created or edited.")
  private String path;

  @NotNull
  @JsonPropertyDescription("The content for the file.")
  private String content;
}
