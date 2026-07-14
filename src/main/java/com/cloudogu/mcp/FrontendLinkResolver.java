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

import sonia.scm.plugin.ExtensionPoint;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.repository.api.ScmProtocol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;

@ExtensionPoint
public interface FrontendLinkResolver {

  /**
   * The target type value that has to be used as {@link CreateFrontendLinkInput#getTargetType()}.
   */
  String getTargetType();

  /**
   * A short description of the frontend link target that will be included in the MCP tool description.
   */
  String getDescription();

  /**
   * Parameters that must be set for this target type in addition to the common {@code targetType} parameter.
   */
  Set<FrontendLinkParameter> getRequiredParameters();

  /**
   * Parameters that are optional for this target type.
   */
  default Set<FrontendLinkParameter> getOptionalParameters() {
    return emptySet();
  }

  /**
   * Validates the input for this target type. Implementations can override this for target-specific rules.
   */
  default Optional<String> validate(CreateFrontendLinkInput input) {
    String missingParameters = getRequiredParameters()
      .stream()
      .filter(parameter -> !parameter.isSet(input))
      .map(FrontendLinkParameter::getFieldName)
      .sorted()
      .collect(Collectors.joining(", "));

    if (missingParameters.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
      String.format("Missing required parameter(s) for targetType '%s': %s.", getTargetType(), missingParameters)
    );
  }

  /**
   * Creates the frontend link. This is only called after {@link #validate(CreateFrontendLinkInput)} has succeeded.
   */
  FrontendLinkResult createLink(CreateFrontendLinkInput input);

  /**
   * Creates the base link to the repository with the given namespace and name. Just create the {@link NamespaceAndName}
   * from the input parameters and calls {@link #findRepositoryUrl(RepositoryServiceFactory, NamespaceAndName)}.
   */
  default String findRepositoryUrl(RepositoryServiceFactory repositoryServiceFactory, CreateFrontendLinkInput input) {
    NamespaceAndName namespaceAndName = new NamespaceAndName(input.getNamespace(), input.getName());
    return findRepositoryUrl(repositoryServiceFactory, namespaceAndName);
  }

  /**
   * Creates the base link to the repository with the given namespace and name.
   */
  default String findRepositoryUrl(RepositoryServiceFactory repositoryServiceFactory, NamespaceAndName namespaceAndName) {
    try (RepositoryService repositoryService = repositoryServiceFactory.create(namespaceAndName)) {
      return repositoryService
        .getSupportedProtocols()
        .filter(p -> "http".equals(p.getType()))
        .map(ScmProtocol::getUrl)
        .findAny()
        .orElseThrow(() -> new NoHttpUrlFoundForRepositoryException(namespaceAndName));
    }
  }

  /**
   * Creates a mutable map with the namespace and the name already added which can be used for 
   * the metadata map in the result of {@link FrontendLinkResolver#createLink(CreateFrontendLinkInput)}.
   */
  default Map<String, Object> repositoryMetadata(CreateFrontendLinkInput input) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("namespace", input.getNamespace());
    metadata.put("name", input.getName());
    return metadata;
  }
}
