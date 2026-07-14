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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Extension
class ToolCreateFrontendLink implements TypedTool<CreateFrontendLinkInput> {

  private final Set<FrontendLinkResolver> resolvers;

  @Inject
  ToolCreateFrontendLink(Set<FrontendLinkResolver> resolvers) {
    this.resolvers = resolvers;
  }

  @Override
  public String getName() {
    return "create-frontend-link";
  }

  @Override
  public String getDescription() {
    return """
      Creates a browser/frontend link for SCM-Manager entities such as repositories, files, directories, and
      plugin-provided entities. Use this tool when you want to present a user with a link to an SCM-Manager page.
      Do not use repository clone URLs for frontend links.
      
      Supported targetType values:
      """ + renderResolverDescriptions();
  }

  @Override
  public Class<CreateFrontendLinkInput> getInputClass() {
    return CreateFrontendLinkInput.class;
  }

  @Override
  public ToolResult execute(CreateFrontendLinkInput input) {
    log.trace("executing request {}", input);

    List<FrontendLinkResolver> matchingResolvers = resolvers.stream()
      .filter(resolver -> resolver.getTargetType().equals(input.getTargetType()))
      .toList();

    if (matchingResolvers.isEmpty()) {
      return ToolResult.error(
        String.format(
          "Unknown targetType '%s'. Supported targetType values: %s.",
          input.getTargetType(),
          renderSupportedTargetTypes()
        )
      );
    }
    if (matchingResolvers.size() > 1) {
      return ToolResult.error(
        String.format("More than one frontend link resolver is registered for targetType '%s'.", input.getTargetType())
      );
    }

    FrontendLinkResolver resolver = matchingResolvers.get(0);
    Optional<String> validationError = resolver.validate(input);
    if (validationError.isPresent()) {
      return ToolResult.error(validationError.get());
    }

    FrontendLinkResult link = resolver.createLink(input);
    return OkResultRenderer.success("Created frontend link.")
      .append("* ")
      .append(link.markdown())
      .append("\n")
      .render(Map.of("link", link.toStructuredContent()));
  }

  private String renderResolverDescriptions() {
    if (resolvers.isEmpty()) {
      return "- No target types are currently registered.";
    }

    return resolvers.stream()
      .sorted((left, right) -> left.getTargetType().compareTo(right.getTargetType()))
      .map(this::renderResolverDescription)
      .collect(Collectors.joining("\n"));
  }

  private String renderResolverDescription(FrontendLinkResolver resolver) {
    String description = "- " + resolver.getTargetType() + ": " + resolver.getDescription()
      + " Required parameters: " + renderParameters(resolver.getRequiredParameters()) + ".";

    if (!resolver.getOptionalParameters().isEmpty()) {
      description += " Optional parameters: " + renderParameters(resolver.getOptionalParameters()) + ".";
    }

    return description;
  }

  private String renderParameters(Set<FrontendLinkParameter> parameters) {
    if (parameters.isEmpty()) {
      return "none";
    }
    return parameters.stream()
      .map(FrontendLinkParameter::getFieldName)
      .sorted()
      .collect(Collectors.joining(", "));
  }

  private String renderSupportedTargetTypes() {
    if (resolvers.isEmpty()) {
      return "none";
    }
    return resolvers.stream()
      .map(FrontendLinkResolver::getTargetType)
      .sorted()
      .collect(Collectors.joining(", "));
  }
}
