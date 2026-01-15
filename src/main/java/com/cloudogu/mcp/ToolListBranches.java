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
import sonia.scm.repository.api.BranchesCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Extension
class ToolListBranches implements TypedTool<ListBranchesInput> {

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  public ToolListBranches(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public String getName() {
    return "list-all-branches";
  }

  @Override
  public String getDescription() {
    return "List all branches from a repository";
  }

  @Override
  public Class<ListBranchesInput> getInputClass() {
    return ListBranchesInput.class;
  }

  @Override
  public ToolResult execute(ListBranchesInput input) {
    log.trace("executing request {}", input);

    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.read(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }

      return readBranches(input, repositoryService);
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading the branches. Please check the namespace and the name of the repository."
      );
    }
  }

  private static ToolResult readBranches(ListBranchesInput input, RepositoryService repositoryService) throws IOException {
    BranchesCommandBuilder branchesCommand = repositoryService.getBranchesCommand();
    List<Branch> branches = branchesCommand.getBranches().getBranches();

    log.trace("found {} branches", branches.size());
    OkResultRenderer resultRenderer = OkResultRenderer.success(String.format("Found %s branches.", branches.size()));
    Map<String, Object> structuredContent = new HashMap<>();
    if (input.isIncludeDetails()) {
      resultRenderer.withInfoText("Detailed metadata (revision, last commit date, last committer) for each is available in the structured data block under their respective names.");
    }
    for (Branch branch : branches) {
      structuredContent.put(branch.getName(), createStructuredBranchInfo(branch));
      renderBranchLine(branch, resultRenderer);
    }
    return resultRenderer.render(input.isIncludeDetails() ? structuredContent : null);
  }

  private static void renderBranchLine(Branch branch, OkResultRenderer resultRenderer) {
    resultRenderer.append("* ").append(branch.getName());
    if (branch.isDefaultBranch()) {
      resultRenderer.append(" [DEFAULT]");
    }
    resultRenderer.append('\n');
  }

  private static Map<String, Object> createStructuredBranchInfo(Branch branch) {
    Map<String, Object> branchInfo = new HashMap<>();
    branchInfo.put("revision", branch.getRevision());
    branchInfo.put("defaultBranch", branch.isDefaultBranch());
    branchInfo.put("lastCommitDate", branch.getLastCommitDate().map(Instant::ofEpochMilli).orElse(null));
    branchInfo.put("lastCommitter", branch.getLastCommitter());
    return branchInfo;
  }
}

@Getter
@ToString
class ListBranchesInput {
  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository")
  private String namespace;

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository")
  private String name;

  @JsonPropertyDescription("""
    If set to `true`, details for the branches will be sent as structured data.
    These details include
    - the date of the last commit,
    - the committer of the last commit,
    - the current revision of the branch,
    - whether this is the default branch for the repository.
    If `false`, only the list of the branch names with a flag for the default branch will be returned."""
  )
  private boolean includeDetails;
}

