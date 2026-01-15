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
import sonia.scm.repository.Branch;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.RepositoryPermissions;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import static com.cloudogu.mcp.OkResultRenderer.success;

@Slf4j
@Extension
class ToolCreateBranch implements TypedTool<CreateBranchInput> {

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  ToolCreateBranch(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public Class<? extends CreateBranchInput> getInputClass() {
    return CreateBranchInput.class;
  }

  @Override
  public ToolResult execute(CreateBranchInput input) {
    log.trace("executing request {}", input);
    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.push(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }

      return executeCreateBranch(input, repositoryService);
    } catch (NullPointerException e) {
      log.info("got a null pointer exception in mcp tool to create branch; normally this means, that the base branch does not exist", e);
      return ToolResult.error(
        String.format("The base branch `%s` does not exist in repository %s/%s", input.getBaseBranch(), input.getNamespace(), input.getName())
      );
    }
  }

  private ToolResult executeCreateBranch(CreateBranchInput input, RepositoryService repositoryService) {
    Branch newBranch = repositoryService.getBranchCommand()
      .from(input.getBaseBranch())
      .branch(input.getNewBranchName());

    return success(String.format("The new branch `%s` has been created on revision %s.", newBranch.getName(), newBranch.getRevision())).render();
  }

  @Override
  public String getName() {
    return "create-branch";
  }

  @Override
  public String getDescription() {
    return "Create a new new branch based on another branch or a revision in a given repository.";
  }
}

@Getter
@ToString
class CreateBranchInput {

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository where the new branch should be created.")
  private String namespace;

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository where the new branch should be created.")
  private String name;

  @JsonPropertyDescription("""
    An existing branch where the new branch should be created.
    If this is omitted, the new branch will be created at the head of the default branch for this repository.""")
  private String baseBranch;

  @NotEmpty
  @Pattern(regexp = Validations.BRANCH_REGEX)
  @JsonPropertyDescription("The name of the new branch")
  private String newBranchName;
}
