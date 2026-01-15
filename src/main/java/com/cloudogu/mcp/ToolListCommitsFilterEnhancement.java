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
import sonia.scm.repository.Changeset;
import sonia.scm.repository.Repository;
import sonia.scm.repository.api.LogCommandBuilder;

import java.util.Optional;
import java.util.function.BiConsumer;

@ExtensionPoint
public interface ToolListCommitsFilterEnhancement {

  /**
   * If available, the config class (POJO) defining the fields this extension needs. If this enhancement does not
   * have additional input properties, this should return {@link Optional#empty()}.
   */
  Optional<Class<?>> getInputClass();

  /**
   * The namespace used to prefix fields in the final JSON schema.
   * Example: return "pullRequest"; -> fields become "pullRequest_id"
   */
  String getNamespace();

  /**
   * This is called for each potential changeset to check, whether the given changeset should be part of the result.
   * Defaults to <code>true</code>.
   *
   * @param repository The repository that is queried.
   * @param changeset  The changeset to check whether it should be contained in the result.
   * @param input      The input for this query.
   * @return <code>true</code> if the changeset should be part of the result.
   */
  default boolean includeCommit(Repository repository, Changeset changeset, ToolListCommits.CompositeInput input) {
    return true;
  }

  /**
   * Can be implemented to configure the {@link LogCommandBuilder} that is used to load the changesets.
   *
   * @param repository        The repository that is queried.
   * @param logCommandBuilder The {@link LogCommandBuilder} to configure.
   * @param input             The input for this query.
   * @return An error message, if the input cannot be processed. By default, this is empty.
   */
  default Optional<String> configure(Repository repository, LogCommandBuilder logCommandBuilder, ToolListCommits.CompositeInput input) {
    return Optional.empty();
  }

  /**
   * Can be implemented to enhance the structured result for each found commit if details have been selected.
   * The default implementation does nothing.
   *
   * @param repository       The repository that is queried.
   * @param changeset        The commit to add details for.
   * @param input            The input for this query.
   * @param keyValueConsumer Call this with the key and the value that shall be added to the result for this commit.
   */
  default void enhanceStructuredResult(Repository repository, Changeset changeset, ToolListCommits.CompositeInput input, BiConsumer<String, Object> keyValueConsumer) {
    // does nothing by default
  }

  /**
   * "Extracts" the specific input for this enhancement, if {@link #getInputClass()} returns an input class.
   *
   * @param input The input given from the tool.
   * @param <T>   The input class.
   * @return Instance of the specific input class from the input.
   */
  default <T> T getExtensionInput(ToolListCommits.CompositeInput input) {
    if (getInputClass().isEmpty()) {
      return null;
    }
    Object extensionInput = input.getExtensionInput(getNamespace());
    if (extensionInput != null && getRequiredInputClass().isAssignableFrom(extensionInput.getClass())) {
      return (T) extensionInput;
    }
    return null;
  }

  default Class<?> getRequiredInputClass() {
    return getInputClass().orElseThrow(() -> new IllegalStateException("Class not found for extension props"));
  }
}
