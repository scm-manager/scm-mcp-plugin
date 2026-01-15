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
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.repository.api.ScmProtocol;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Extension
class ToolListRepositories implements TypedTool<ListRepositoriesInput> {

  private final RepositoryManager repositoryManager;
  private final RepositoryServiceFactory repositoryServiceFactory;
  private final RepositoryMapper repositoryMapper;

  @Inject
  public ToolListRepositories(RepositoryManager repositoryManager, RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryManager = repositoryManager;
    this.repositoryServiceFactory = repositoryServiceFactory;
    this.repositoryMapper = new RepositoryMapperImpl();
  }

  @Override
  public String getName() {
    return "list-repositories";
  }

  @Override
  public String getDescription() {
    return """
      List available repositories.
      The result will contain the following information about each repository:
      - the namespace and the name in the form of "namespace/name",
      - the link to the repository in SCM-Manager as a markdown formatted link
        (which can be used for a clone, too, and should be part of the result for the user),
      - the type of the repository (git, hg, or svn).
      
      For more information about the repositories, set `includeDetails` to true.
      This will give you additional structured data for the repositories with
      - all protocol links if available (for example for ssh),
      - the description,
      - the creation date,
      - the contact information,
      - a flag whether the repository is set to archived or not.
      """;
  }

  @Override
  public Class<ListRepositoriesInput> getInputClass() {
    return ListRepositoriesInput.class;
  }

  @Override
  public ToolResult execute(ListRepositoriesInput input) {
    log.trace("executing request");
    Collection<Repository> allRepositories = repositoryManager.getAll();
    Collection<Repository> repositories = allRepositories.stream()
      .filter(repository -> Strings.isNullOrEmpty(input.getNamespace()) || input.getNamespace().equals(repository.getNamespace()))
      .filter(repository -> Strings.isNullOrEmpty(input.getName()) || input.getName().equals(repository.getName()))
      .filter(repository -> input.getType() == null || input.getType().name().equals(repository.getType()))
      .toList();

    Map<String, Object> structuredContent = new HashMap<>(repositories.size());

    log.trace("found {} repositories", repositories.size());

    OkResultRenderer resultRenderer = OkResultRenderer.success(String.format("Found %s repositories.", repositories.size()));
    if (input.isIncludeDetails()) {
      resultRenderer.withInfoText("Detailed metadata (URLs, dates, descriptions and more) for each is available in the structured data block under their respective names.");
    } else {
      resultRenderer.withInfoText("You can find the first 100 bytes of the descriptions for each repository in the structured data block under their respective names.");
    }

    handleRepository(input, repositories, structuredContent, resultRenderer);

    return resultRenderer.render(structuredContent);
  }

  private void handleRepository(ListRepositoriesInput input, Collection<Repository> repositories, Map<String, Object> structuredContent, OkResultRenderer resultRenderer) {
    for (Repository repository : repositories) {
      Map<String, String> protocolLinks = getProtocolLinks(repository);
      createStructuredContentForRepository(input, structuredContent, repository, protocolLinks);
      renderContentForRepository(resultRenderer, repository, protocolLinks);
    }
  }

  private void renderContentForRepository(OkResultRenderer resultRenderer, Repository repository, Map<String, String> protocolLinks) {
    resultRenderer
      .append("* [")
      .append(repository.getNamespaceAndName())
      .append("](")
      .append(protocolLinks.get("http"))
      .append(") (")
      .append(repository.getType())
      .append(")\n");
  }

  private void createStructuredContentForRepository(ListRepositoriesInput input, Map<String, Object> structuredContent, Repository repository, Map<String, String> protocolLinks) {
    if (input.isIncludeDetails()) {
      McpRepositoryDto dto = repositoryMapper.toDto(repository);
      dto.setProtocolLinks(protocolLinks);
      structuredContent.put(
        repository.getNamespaceAndName().toString(),
        dto
      );
    } else if (!Strings.isNullOrEmpty(repository.getDescription())) {
      structuredContent.put(
        repository.getNamespaceAndName().toString(),
        repository.getDescription().substring(0, Math.min(repository.getDescription().length(), 100))
      );
    }
  }

  private Map<String, String> getProtocolLinks(Repository repository) {
    try (RepositoryService repositoryService = repositoryServiceFactory.create(repository)) {
      return repositoryService.getSupportedProtocols()
        .collect(Collectors.toMap(
          ScmProtocol::getType,
          ScmProtocol::getUrl
        ));
    }
  }
}

@Getter
@Setter(AccessLevel.PACKAGE) // for test purposes
@ToString
class ListRepositoriesInput {
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("If set, list only the repositories from this namespace.")
  private String namespace;
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("If set, list only the repositories with this name.")
  private String name;
  @JsonPropertyDescription("If set, list only the repositories with this type.")
  private RepositoryType type;
  @JsonPropertyDescription("""
    If set to `true`, details for the repositories will be sent as structured data.
    If `false`, only the list of namespaces and names with links to the repositories and the repository types will be returned
    with an additional map of the first 100 bytes of the descriptions for the repositories, if availabe
    (the keys for this map are the namespace/name pairs of the repositories;
     repositories without description will have no entry in this map)."""
  )
  private boolean includeDetails;

  @SuppressWarnings("java:S115") // we want lower caps here so that the enum can be used directly by the AI
  enum RepositoryType {
    git,
    svn,
    hg
  }
}
