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
import com.google.common.annotations.VisibleForTesting;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Extension
class ToolCreateRepository implements TypedTool<CreateRepositoryInput> {

  private final RepositoryManager repositoryManager;
  private final RepositoryToHalMapper repositoryToHalMapper;
  private final boolean canSetNamespace;

  @Inject
  public ToolCreateRepository(RepositoryManager repositoryManager, RepositoryToHalMapper repositoryToHalMapper, ScmConfiguration scmConfiguration, Set<NamespaceStrategy> strategies) {
    this(repositoryManager, repositoryToHalMapper, isRenameNamespacePossible(scmConfiguration, strategies));
  }

  @VisibleForTesting
  ToolCreateRepository(RepositoryManager repositoryManager, RepositoryToHalMapper repositoryToHalMapper, boolean canSetNamespace) {
    this.repositoryManager = repositoryManager;
    this.repositoryToHalMapper = repositoryToHalMapper;
    this.canSetNamespace = canSetNamespace;
  }

  @Override
  public String getName() {
    return "create-repository";
  }

  @Override
  public String getDescription() {
    return "Create a new repository";
  }

  @Override
  public Class<? extends CreateRepositoryInput> getInputClass() {
    return canSetNamespace ?
      CreateRepositoryInputWithNamespace.class :
      CreateRepositoryInputWithoutNamespace.class;
  }

  @Override
  public ToolResult execute(CreateRepositoryInput input) {
    log.trace("executing request {}", input);
    try {
      Repository repository = repositoryManager.create(
        new Repository(
          null,
          input.getType().name(),
          input.getNamespace(),
          input.getName())
      );

      log.trace("created new repository {}", repository);

      return ToolResult.ok(
        List.of(repository.getNamespaceAndName().toString()),
        Map.of("repository", repositoryToHalMapper.map(repository))
      );
    } catch (AlreadyExistsException existsException) {
      log.trace("repository already exists", existsException);
      return ToolResult.error(
        "A repository with this namespace and name already exists"
      );
    }
  }

  private static boolean isRenameNamespacePossible(ScmConfiguration scmConfiguration, Set<NamespaceStrategy> strategies) {
    for (NamespaceStrategy strategy : strategies) {
      if (strategy.getClass().getSimpleName().equals(scmConfiguration.getNamespaceStrategy())) {
        return strategy.canBeChanged();
      }
    }
    return false;
  }

}

interface CreateRepositoryInput {
  String getNamespace();

  String getName();

  RepoType getType();
}

@Getter
@ToString(callSuper = true)
class CreateRepositoryInputWithNamespace extends CreateRepositoryInputWithoutNamespace {
  @NotNull // Marks this field as required in the schema
  @JsonPropertyDescription("The namespace for the new repository")
  private String namespace;
}

@Getter
@ToString
class CreateRepositoryInputWithoutNamespace implements CreateRepositoryInput {

  @NotNull
  @JsonPropertyDescription("The name for the new repository")
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  private String name;

  @JsonPropertyDescription("The type of the new repository. This must be either 'git', 'hg', or 'svn'")
  private RepoType type = RepoType.git; // Default value in Java

  @JsonPropertyDescription("Optional description for the repository")
  private String description;

  @Email
  @JsonPropertyDescription("Optional contact in form of an email address")
  private String contact;

  @Override
  public String getNamespace() {
    return null;
  }
}

@SuppressWarnings("java:S115") // we want lower caps here so that the enum can be used directly by the AI
enum RepoType {
  git, hg, svn
}
