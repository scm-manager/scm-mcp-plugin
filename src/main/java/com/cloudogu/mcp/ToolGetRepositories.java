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
import de.otto.edison.hal.Link;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.repository.api.ScmProtocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Extension
class ToolGetRepositories implements TypedTool<ListRepositoriesInput> {

  private final RepositoryManager repositoryManager;
  private final RepositoryMapper repositoryMapper;
  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  public ToolGetRepositories(RepositoryManager repositoryManager, RepositoryMapper repositoryMapper, RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryManager = repositoryManager;
    this.repositoryMapper = repositoryMapper;
    this.repositoryServiceFactory = repositoryServiceFactory;
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
      if (!Strings.isNullOrEmpty(input.getNamespace()) && !input.getNamespace().equals(repository.getNamespace())) {
        continue;
      }
      if (input.getType() != null && !input.getType().name().equals(repository.getType())) {
        continue;
      }
      McpRepositoryDto dto = repositoryMapper.toDto(repository);
      try (RepositoryService repositoryService = repositoryServiceFactory.create(repository)) {
        dto.setProtocolLinks(
          repositoryService.getSupportedProtocols()
            .map(this::createProtocolLink)
            .toList()
        );
      }
      structuredContent.put(
        repository.getNamespaceAndName().toString(),
        dto
      );
      repositoryNames.add(repository.getNamespaceAndName().toString());
    }

    log.trace("found {} repositories", repositories.size());
    return ToolResult.ok(repositoryNames, input.isDetails() ? structuredContent : null);
  }

  private Link createProtocolLink(ScmProtocol protocol) {
    return Link.link(protocol.getType(), protocol.getUrl());
  }
}

@Getter
@ToString
class ListRepositoriesInput {
  @JsonPropertyDescription("If set, list only the repositories from this namespace.")
  private String namespace;
  @JsonPropertyDescription("If set, list only the repositories with this type.")
  private RepositoryType type;
  @JsonPropertyDescription("If set to `true`, details for the repositories will be sent as structured data. If `false`, only the list of namespaces and names will be returned.")
  private boolean details;

  enum RepositoryType {
    git,
    svn,
    hg
  }
}
