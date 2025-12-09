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

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.web.api.RepositoryToHalMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Extension
class ToolGetRepositories implements TypedTool<ListRepositoriesInput> {

  private final RepositoryManager repositoryManager;
  private final RepositoryToHalMapper repositoryToHalMapper;

  @Inject
  public ToolGetRepositories(RepositoryManager repositoryManager, RepositoryToHalMapper repositoryToHalMapper) {
    this.repositoryManager = repositoryManager;
    this.repositoryToHalMapper = repositoryToHalMapper;
  }

  @Override
  public String getName() {
    return "list-all-repositories";
  }

  @Override
  public String getDescription() {
    return "List all available repositories";
  }

  @Override
  public Class<ListRepositoriesInput> getInputClass() {
    return ListRepositoriesInput.class;
  }

  @Override
  public ToolResult execute(ListRepositoriesInput input) {
    log.trace("executing request");
    Collection<Repository> repositories = repositoryManager.getAll();
    Map<String, Object> structuredContent = new HashMap<>(repositories.size());
    List<String> repositoryNames = new ArrayList<>(repositories.size());

    for (Repository repository : repositories) {
      structuredContent.put(
        repository.getNamespaceAndName().toString(),
        repositoryToHalMapper.map(repository)
      );
      repositoryNames.add(repository.getNamespaceAndName().toString());
    }

    log.trace("found {} repositories", repositories.size());
    return ToolResult.ok(repositoryNames, structuredContent);
  }
}

class ListRepositoriesInput {
  // Intentionally empty
}
