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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CreateFrontendLinkInput {

  @NotEmpty
  @JsonPropertyDescription("""
    The type of frontend link to create. Use one of the supported target types listed in the tool description,
    for example `repository`, `file`, `directory`, or plugin-provided target types like `pullRequest`."""
  )
  private String targetType;

  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository for repository-related frontend links.")
  private String namespace;

  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository for repository-related frontend links.")
  private String name;

  @JsonPropertyDescription("The revision (branch, tag, or commit hash) to link to for branch, commit, tag, source, file, or directory frontend links.")
  private String revision;

  @JsonPropertyDescription("The repository path to link to for file or directory frontend links.")
  private String path;

  @JsonPropertyDescription("""
    A generic entity identifier for plugin-provided target types.
    For example, the review plugin can use this as the pull request ID when targetType is `pullRequest`."""
  )
  private String id;

  @Min(1)
  @JsonPropertyDescription("A concrete line number to link to for file frontend links.")
  private Integer line;
}
