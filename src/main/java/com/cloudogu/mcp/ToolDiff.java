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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.RepositoryPermissions;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;

@Slf4j
@Extension
class ToolDiff implements TypedTool<DiffInput> {

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  public ToolDiff(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public Class<? extends DiffInput> getInputClass() {
    return DiffInput.class;
  }

  @Override
  public ToolResult execute(DiffInput input) {
    log.trace("executing request {}", input);
    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.read(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }
      return readDiff(input, repositoryService);
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading or writing the files"
      );
    }
  }

  private ToolResult readDiff(DiffInput input, RepositoryService repositoryService) throws IOException {
    String diff = computeDiff(input, repositoryService);

    return OkResultRenderer.success("Diff has been created.")
      .withInfoText("The diff has the default git diff format.")
      .append(diff)
      .render();
  }

  private String computeDiff(DiffInput input, RepositoryService repositoryService) throws IOException {
    return repositoryService.getDiffCommand()
      .setRevision(input.getRevision())
      .getContent();
  }

  @Override
  public String getName() {
    return "compute-diff";
  }

  @Override
  public String getDescription() {
    return """
      Compute the diff of a specific revision from the specified repository.
      The result will be formatted in the standard git diff format.""";
  }
}

@Getter
@ToString
class DiffInput {

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository to create the diff for.")
  private String namespace;

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository to create the diff for.")
  private String name;

  @NotEmpty
  @JsonPropertyDescription("The revision to create the diff for")
  private String revision;
}
