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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Getter
@ToString
@Setter(AccessLevel.PACKAGE)
public class ListCommitsInput {
  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository")
  private String namespace;

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository")
  private String name;

  @JsonPropertyDescription("""
    The revision to list the commits for. This can be either a 'real' revision, a branch, or a tag.
    If this is omitted, the default branch of the repository will be taken.""")
  private String revision;

  @JsonPropertyDescription("Filter for commits that contain this string in the commit message. This filter is case insensitive.")
  private String commitMessageFilter;

  @JsonPropertyDescription("Filter for commits whose author contains this string. This filter is case insensitive.")
  private String authorFilter;

  @JsonPropertyDescription("Filter for commits committed before this timestamp (ISO 8601 format, e.g. 2024-01-01T10:00:00Z).")
  private Instant committedBefore;
  @JsonPropertyDescription("Filter for commits committed before this timestamp (ISO 8601 format, e.g. 2024-01-01T10:00:00Z).")
  private Instant committedAfter;

  @JsonPropertyDescription("""
    If set to `true`, details for the commits will be sent as structured data.
    If `false`, only the commit log like `git log` would produce will be returned."""
  )
  private boolean includeDetails;

  @Min(1)
  @JsonPropertyDescription("The maximum number of commits to read.")
  private int limit = 20;
}
