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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.FileObject;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.RepositoryPermissions;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Extension
public class ToolListFiles implements TypedTool<ListFilesInput> {

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  public ToolListFiles(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public Class<? extends ListFilesInput> getInputClass() {
    return ListFilesInput.class;
  }

  @Override
  public ToolResult execute(ListFilesInput input) {
    log.trace("executing request {}", input);

    FileObject root;

    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.read(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }

      root =
        repositoryService
          .getBrowseCommand()
          .setRevision(input.getRevision())
          .setPath(getEffectivePath(input))
          .setRecursive(input.isRecursive())
          .setDisableLastCommit(true)
          .setDisablePreProcessors(true)
          .setDisableSubRepositoryDetection(true)
          .setLimit(input.getMaximumNumberOfFiles())
          .getBrowserResult()
          .getFile();
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading the files"
      );
    }

    return createResult(root);
  }

  private ToolResult createResult(FileObject root) {
    Counts counts = count(root);

    OkResultRenderer resultRenderer;
    if (root.isTruncated()) {
      resultRenderer = OkResultRenderer.ok("TRUNCATED", "Maximum file limit reached.");
      resultRenderer.withInfoText(String.format("Displaying the first %s directories and %s files.", counts.directories - 1, counts.files));
    } else {
      resultRenderer = OkResultRenderer.success("File listing complete.");
      resultRenderer.withInfoText(String.format("Found %s directories and %s files. The result is complete.", counts.directories - 1, counts.files));
    }
    formatAsTree(resultRenderer, root);

    log.trace("files read");
    return resultRenderer.render(Map.of("maximumExceeded", root.isTruncated()));
  }

  private Counts count(FileObject root) {
    if (root.isDirectory()) {
      int fileCount = 0;
      int directoryCount = 1;
      for (FileObject file : root.getChildren()) {
        Counts subCounts = count(file);
        fileCount += subCounts.files;
        directoryCount += subCounts.directories;
      }
      return new Counts(fileCount, directoryCount);
    } else {
      return new Counts(1, 0);
    }
  }

  private void formatAsTree(OkResultRenderer resultRenderer, FileObject root) {
    // Start recursion with an empty prefix
    buildTree(root, resultRenderer, "", true);
  }

  private void buildTree(FileObject file, OkResultRenderer resultRenderer, String prefix, boolean isLast) {
    // 1. Append the current node with its prefix
    resultRenderer.append(prefix)
      .append(isLast ? "└── " : "├── ")
      .append(file.getName())
      .append(file.isDirectory() ? "/" : "") // Add slash for directories
      .append("\n");

    // 2. Prepare prefix for children
    String newPrefix = prefix + (isLast ? "    " : "│   ");

    // 3. Recurse through children
    List<FileObject> children = new ArrayList<>(file.getChildren());
    for (int i = 0; i < children.size(); i++) {
      boolean lastChild = (i == children.size() - 1);
      buildTree(children.get(i), resultRenderer, newPrefix, lastChild);
    }
  }

  private String getEffectivePath(ListFilesInput input) {
    if (Strings.isNullOrEmpty(input.getRoot()) || input.getRoot().equals("/")) {
      return "/";
    }
    if (input.getRoot().startsWith("/")) {
      return input.getRoot().substring(1);
    }
    return input.getRoot();
  }

  @Override
  public String getName() {
    return "list-files";
  }

  @Override
  public String getDescription() {
    return """
      List files from a specified repository. The result will contain a file tree. Directories will be followed by
      a `/` to distinguish them from files.""";
  }

  private record Counts(int files, int directories) {
  }
}

@Data
class ListFilesInput {
  @NotNull
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository to read files from.")
  private String namespace;

  @NotNull
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository to read files from.")
  private String name;

  @JsonPropertyDescription("""
    The revision to list the files for. This can be either a 'real' revision, a branch, or a tag.
    If this is omitted, the default branch of the repository will be taken.""")
  private String revision;

  @NotNull
  @JsonPropertyDescription("The path that shall be listed. The default value is the root directory.")
  private String root = "/";

  @Min(1)
  @JsonPropertyDescription("""
    The maximum number of file names that will be returned.
    The default for this is 100.
    If the result would contain more entries than this maximum, the flag `maximumExceeded`
    will be set to `true` in the result.""")
  private int maximumNumberOfFiles = 100;

  @JsonPropertyDescription("If set to `true`, the files will be listed recursively.")
  private boolean recursive;
}
