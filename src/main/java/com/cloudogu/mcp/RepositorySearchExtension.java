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

import sonia.scm.plugin.Extension;
import sonia.scm.search.Hit;

import java.util.Map;

@Extension
class RepositorySearchExtension implements ToolSearchExtension {
  @Override
  public String getSearchType() {
    return "repository";
  }

  @Override
  public String getDescription() {
    return """
      Search fields of type 'repository':
      
      1. namespace - The namespace of the repository
      2. name - The name of the repository
      3. type - The type of the repository (git, hg or svn)
      4. description - The description of the repository
      5. creationDate - The creation date of the repository as unix epoch timestamp
      6. lastModified - The last time the repository was modified as a unix epoch timestamp""";
  }

  @Override
  public String getSummary() {
    return "Repositories (corresponding type is 'repository')";
  }

  @Override
  public String[] tableColumnHeader() {
    return new String[]{"Repository", "Repository Type", "Description (if the search term was found here)"};
  }

  @Override
  public String[] transformHitToTableFields(Hit hit) {
    String namespace = extractValue(hit, "namespace");
    String name = extractValue(hit, "name");
    String type = extractValue(hit, "type");
    String description = extractValue(hit, "description");
    return new String[]{
      namespace + "/" + name,
      type,
      description
    };
  }

  @Override
  public Map<String, Object> transformHitToStructuredAnswer(Hit hit) {
    Map<String, Object> result = ToolSearchExtension.super.transformHitToStructuredAnswer(hit);
    Object namespace = result.remove("namespace");
    Object name = result.remove("name");
    result.put("repository", namespace + "/" + name);
    return result;
  }
}
