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
import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.ConcurrentModificationException;
import sonia.scm.NoChangesMadeException;
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

import static com.cloudogu.mcp.OkResultRenderer.success;
import static java.util.Collections.emptyList;

@Slf4j
@Extension
class ToolModifyFiles implements TypedTool<ModifyFilesInput> {

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  public ToolModifyFiles(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public String getName() {
    return "modify-files";
  }

  @Override
  public String getDescription() {
    return "Create, edit, move, or delete files";
  }

  @Override
  public Class<ModifyFilesInput> getInputClass() {
    return ModifyFilesInput.class;
  }

  @Override
  public ToolResult execute(ModifyFilesInput input) {
    log.trace("executing request {}", input);
    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.push(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }
      if (
        input.getFilesToMove().isEmpty()
          && input.getFilesToDelete().isEmpty()
          && input.getFilesToCreateOrEdit().isEmpty()
      ) {
        return ToolResult.error("At least one file to move, edit or delete must be specified.");
      }

      return executeModification(input, repositoryService);
    } catch (ConcurrentModificationException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "The repository has been modified concurrently. Your changes have not been applied. Please update your state and try again."
      );
    } catch (NoChangesMadeException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "There have been no changes in the given files. No commit has been created."
      );
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading or writing the files"
      );
    }
  }

  private ToolResult executeModification(ModifyFilesInput input, RepositoryService repositoryService) throws IOException {
    ModifyCommandBuilder modifyCommandBuilder =
      repositoryService.getModifyCommand()
        .setCommitMessage(input.getCommitMessage());
    if (!Strings.isNullOrEmpty(input.getBranch())) {
      modifyCommandBuilder.setBranch(input.getBranch());
    }

    handleCreateOrEdit(input, modifyCommandBuilder);
    handleMove(input, modifyCommandBuilder);
    handleDelete(input, modifyCommandBuilder);

    String revision = modifyCommandBuilder
      .execute();

    log.trace("files created");
    return success(
      String.format(
        "Created or modified %s files in revision %s in repository %s/%s",
        input.getFilesToCreateOrEdit().size(),
        revision,
        input.getNamespace(), input.getName()
      )
    ).render();
  }

  private void handleDelete(ModifyFilesInput input, ModifyCommandBuilder modifyCommandBuilder) {
    for (FileDeleteEntry delete : input.getFilesToDelete()) {
      modifyCommandBuilder
        .deleteFile(delete.getPath());
    }
  }

  private void handleMove(ModifyFilesInput input, ModifyCommandBuilder modifyCommandBuilder) {
    for (FileMoveEntry move : input.getFilesToMove()) {
      modifyCommandBuilder
        .move(move.getFromPath())
        .to(move.getToPath());
    }
  }

  private void handleCreateOrEdit(ModifyFilesInput input, ModifyCommandBuilder modifyCommandBuilder) throws IOException {
    for (FileModificationEntry modification : input.getFilesToCreateOrEdit()) {
      modifyCommandBuilder
        .createFile(modification.getPath())
        .setOverwrite(true)
        .withData(new ByteArrayInputStream(modification.getContent().getBytes(StandardCharsets.UTF_8)));
    }
  }
}

@Getter
@ToString(exclude = "commitMessage")
class ModifyFilesInput {
  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository where the files should be created or edited.")
  private String namespace;

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository where the files should be created or edited.")
  private String name;

  @Pattern(regexp = Validations.BRANCH_REGEX)
  @JsonPropertyDescription(
    "The target branch where the files should be created or edited. If omitted, the default branch will be used."
  )
  private String branch;

  @Valid
  @JsonPropertyDescription("A list of files to create or edit.")
  private List<FileModificationEntry> filesToCreateOrEdit = emptyList();

  @Valid
  @JsonPropertyDescription("""
    A list of files that shall be deleted.
    This does not work for directories.
    If you want to delete a directory completely, you have to list all files recursively first and delete each single file.""")
  private List<FileDeleteEntry> filesToDelete = emptyList();

  @Valid
  @JsonPropertyDescription("""
    A list of files that shall be moved or renamed.
    This does not work for directories.
    If you want to rename a directory, you have to list all files recursively first and move each single file.""")
  private List<FileMoveEntry> filesToMove = emptyList();

  @NotEmpty
  @JsonPropertyDescription("Commit message for the change")
  private String commitMessage = "Change by MCP server";
}

@Getter
@ToString(exclude = "content")
class FileModificationEntry {
  @NotEmpty
  @JsonPropertyDescription("The path for the file that needs to be created or edited.")
  private String path;

  @NotNull
  @JsonPropertyDescription("The content for the file.")
  private String content;
}

@Getter
@ToString
class FileDeleteEntry {
  @NotEmpty
  @JsonPropertyDescription("The path of the file that shall be deleted.")
  private String path;
}

@Getter
@ToString
class FileMoveEntry {
  @NotEmpty
  @JsonPropertyDescription("The path of the file that shall be moved/renamed.")
  private String fromPath;

  @NotEmpty
  @JsonPropertyDescription("""
    The new path of the file.
    If the file should be moved to another directory, specify the complete new path here including
    the name of the file itself, not just the target directory.
    If you want to rename the file, again specify the complete new path here including the directory.""")
  private String toPath;
}
