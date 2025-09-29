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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import sonia.scm.plugin.Extension;
import sonia.scm.search.Hit;
import sonia.scm.search.QueryBuilder;
import sonia.scm.search.SearchEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@SuppressWarnings("UnstableApiUsage")
@Extension
public class ToolSearchGlobally implements Tool {

  private final SearchEngine searchEngine;

  @Inject
  public ToolSearchGlobally(SearchEngine searchEngine) {
    this.searchEngine = searchEngine;
  }

  @Override
  public String getName() {
    return "search_globally";
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
  public String getInputSchema() {
    return """
      {
        "type" : "object",
        "id" : "tools/search_globally",
        "properties" : {
          "query" : {
            "type" : "string",
            "description": "Query that is used to search for the results"
          },
          "page" : {
            "type" : "integer",
            "description": "Which page of the result should be shown",
            "minimum": 0,
            "default": 0
          },
          "pageSize" : {
            "type" : "integer",
            "description": "Amount of results that should be shown per page",
            "minimum": 1,
            "default": 10
          },
          "type": {
            "type" : "string",
            "description": "The type of object the user is search for. For example 'repository'",
            "enum": ["repository", "content"],
            "default": "repository"
          }
        },
        "required": ["query", "type"]
      }
      """;
  }

  @Override
  public BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> getCallHandler() {
    return this::search;
  }

  private McpSchema.CallToolResult search(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
    String query = getRequiredArgument(request, "query").toString();
    String type = getRequiredArgument(request, "type", "repository");
    int page = getRequiredArgument(request, "page", 0);
    int pageSize = getRequiredArgument(request, "pageSize", 10);

    QueryBuilder<Object> queryBuilder = searchEngine.forType(type)
      .search()
      .start(page * pageSize)
      .limit(pageSize);

    List<Hit> hits = queryBuilder.execute(query).getHits();
    List<McpSchema.Content> unstructuredResults = new ArrayList<>(hits.size());
    Map<String, Object> structuredResults = new HashMap<>(hits.size());

    for (Hit hit : hits) {
      unstructuredResults.add(transformHitToUnstructuredAnswer(hit));
      structuredResults.put(hit.getId(), transformHitToStructuredAnswer(hit));
    }

    return new McpSchema.CallToolResult(unstructuredResults, false, structuredResults);
  }

  private Map<String,Object> transformHitToStructuredAnswer(Hit hit) {
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

  private McpSchema.Content transformHitToUnstructuredAnswer(Hit hit) {
    StringBuilder answer = new StringBuilder();
    answer.append(String.format("Search Result ID: %s\n", hit.getId()));
    hit.getRepositoryId().ifPresent(
      repositoryId -> answer.append(String.format("Repository ID: %s\n", repositoryId))
    );
    hit.getFields().forEach(
      (fieldName, field) -> answer.append(String.format("Field Name: %s, Field Value: %s\n", fieldName, transformField(field)))
    );

    return new McpSchema.TextContent(answer.toString());
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
