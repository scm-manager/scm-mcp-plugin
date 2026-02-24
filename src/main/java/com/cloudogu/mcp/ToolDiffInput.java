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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

import static java.util.Collections.emptyList;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ToolDiffInput {

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository to create the diff for.")
  private String namespace;

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository to create the diff for.")
  private String name;

  @NotEmpty
  @JsonPropertyDescription("""
    The target expression for the diff. Check the main tool description for the exact syntax and the
    list of currently supported formats.""")
  private String diffTarget;

  @JsonPropertyDescription("""
    Diff output will be limited to this number of diff lines.
    If file diffs are omitted because of this, the result will nonetheless contain the diff headers for the other files
    and the number of diff lines that have been omitted for these files.
    If you are only interested in the names of the files that have been changed, added or deleted, you can set this to 0.
    """)
  private int diffLineLimit = 1000;

  @JsonPropertyDescription("""
    Diff output will be limited to this number of files.
    If files are omitted because of this, the number of omitted files will be given in the result.
    """)
  private int diffFileLimit = 1000;

  @JsonPropertyDescription("""
    If this is not empty, the diffs will only be created for the given paths
    (that is, either the old or the new path of the diff entry have to match an entry in this list).
    These paths can contain glob patterns. So if you want to limit the diff to all files in `src/main` for example, add
    `src/main/*` to this list. If you only want to get the diff for markdown files, add `*.md`.
    If this is not empty, diffs for all files not matching entries in this list will be omitted completely.
    """)
  private List<String> pathFilter = emptyList();

  @JsonPropertyDescription("If you want to ignore whitespace changes in your diff, set this to `true`.")
  private boolean ignoreWhitespace;
}
