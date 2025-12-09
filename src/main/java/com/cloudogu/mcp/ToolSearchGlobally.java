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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.search.Hit;
import sonia.scm.search.QueryBuilder;
import sonia.scm.search.SearchEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Extension
@SuppressWarnings("UnstableApiUsage")
public class ToolSearchGlobally implements TypedTool<SearchInput> {

  private final SearchEngine searchEngine;

  @Inject
  public ToolSearchGlobally(SearchEngine searchEngine) {
    this.searchEngine = searchEngine;
  }

  @Override
  public String getName() {
    return "search-globally";
  }

  @Override
  public String getDescription() {
    return """
      Search the SCM-Manager for content globally.
      This search currently allows searching for the following types.
      1. Repositories (corresponding type is repository)
      2. File content within repositories (corresponding type is content)
      
      Each type supports different fields that can be searched.
      1. Fields of repository type
        1.1 namespace - The namespace of the repository
        1.2 name - The name of the repository
        1.3 type - The type of the repository (git, hg or svn)
        1.4 description - The description of the repository
        1.5 creationDate - The creation date of the repository as unix epoch timestamp
        1.6 lastModified - The last time the repository was modified as a unix epoch timestamp
      2. Fields of content type
        2.1 path - Path of the file within the repository
        2.2 filename - Name of the file including the extension within the repository
        2.3 extension - Name of the file extension within the repository
        2.4 content - Content of the file within the repository
      
      If you want to search for a value within a specific field, then you have to combine the field name and the value you are searching for with a colon (:) seperating them.
      For example if you want to search for a file that contains the word react, then the query would look like this 'content:react'.
      
      If you dont want to search within a specific field then you only need to add the value you are searching for.
      For example if you want to search for the term react within all fields, then the query would look like this 'react'.
      
      You can also combine search terms logically with the AND and OR operators.
      For example if you want to search for a Java file that contains the term 'static void main', then the query would look like this 'extension:java AND content:"static void main".
      As you can see if you want to combine multiple words into one term, then you need to surround them with double quotes (").
      """;
  }

  @Override
  public Class<SearchInput> getInputClass() {
    return SearchInput.class;
  }

  @Override
  public ToolResult execute(SearchInput searchInput) {
    log.trace("executing request {}", searchInput);
    QueryBuilder<Object> queryBuilder = searchEngine.forType(searchInput.getType().name())
      .search()
      .start(searchInput.getPage() * searchInput.getPageSize())
      .limit(searchInput.getPageSize());

    List<Hit> hits = queryBuilder.execute(searchInput.getQuery()).getHits();
    List<String> unstructuredResults = new ArrayList<>(hits.size());
    Map<String, Object> structuredResults = new HashMap<>(hits.size());

    for (Hit hit : hits) {
      unstructuredResults.add(transformHitToUnstructuredAnswer(hit));
      structuredResults.put(hit.getId(), transformHitToStructuredAnswer(hit));
    }

    log.trace("found {} hit(s)", hits.size());
    return ToolResult.ok(unstructuredResults, structuredResults);
  }

  private Map<String, Object> transformHitToStructuredAnswer(Hit hit) {
    Map<String, Object> transformedHit = new HashMap<>(3);
    transformedHit.put("id", hit.getId());
    hit.getRepositoryId().ifPresent(
      repositoryId -> transformedHit.put("repositoryId", repositoryId)
    );

    Map<String, Object> transformedFields = new HashMap<>(hit.getFields().size());
    hit.getFields().forEach(
      (fieldName, field) -> transformedFields.put(fieldName, transformField(field))
    );
    transformedHit.put("fields", transformedFields);

    return transformedHit;
  }

  private String transformHitToUnstructuredAnswer(Hit hit) {
    StringBuilder answer = new StringBuilder();
    answer.append(String.format("Search Result ID: %s\n", hit.getId()));
    hit.getRepositoryId().ifPresent(
      repositoryId -> answer.append(String.format("Repository ID: %s\n", repositoryId))
    );
    hit.getFields().forEach(
      (fieldName, field) -> answer.append(String.format("Field Name: %s, Field Value: %s\n", fieldName, transformField(field)))
    );

    return answer.toString();
  }

  private String transformField(Hit.Field field) {
    if (field instanceof Hit.ValueField valueField) {
      return valueField.getValue().toString();
    }

    if (field instanceof Hit.HighlightedField highlightedField) {
      return String.join("", highlightedField.getFragments());
    }

    throw new RuntimeException(String.format("Unsupported field type: %s", field.getClass()));
  }
}

@Getter
@ToString
class SearchInput {

  @NotNull
  @JsonPropertyDescription("Query that is used to search for the results")
  private String query;

  @Min(0)
  @JsonPropertyDescription("Which page of the result should be shown")
  private int page = 0;

  @Min(1)
  @JsonPropertyDescription("Amount of results that should be shown per page")
  private int pageSize = 10;

  @NotNull
  @JsonPropertyDescription("The type of object the user is search for. For example 'repository'")
  @JsonProperty(defaultValue = "repository")
  private SearchType type = SearchType.repository;
}

@SuppressWarnings("java:S115") // we want lower caps here so that the enum can be used directly by the AI
enum SearchType {
  repository,
  content
}
