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
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.search.Hit;
import sonia.scm.search.SearchEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Extension
@SuppressWarnings("UnstableApiUsage")
class ToolSearchGlobally implements TypedTool<SearchInput> {

  private final SearchEngine searchEngine;
  private final Set<ToolSearchExtension> searchExtensions;

  @Inject
  public ToolSearchGlobally(SearchEngine searchEngine, Set<ToolSearchExtension> searchExtensions) {
    this.searchEngine = searchEngine;
    this.searchExtensions = searchExtensions;
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
      These query types only have to be set in the search input. This **must not** be part of the query string itself!
      """ +
      searchExtensions.stream()
        .map(ToolSearchExtension::getSummary)
        .map(s -> "- " + s)
        .collect(Collectors.joining("\n")) +
      """
      
      Each type supports different fields that can be searched:

      """ +
      searchExtensions.stream()
        .map(ToolSearchExtension::getDescription)
        .collect(Collectors.joining("\n")) +
      """

      If you want to search for a value within a specific field, then you have to combine the field name and the value you are searching for with a colon (`:`) seperating them.
      For example if you want to search for a file that contains the word "react", then the query would look like this `content:react`.
    
      If you dont want to search within a specific field, then you only need to add the value you are searching for.
      For example if you want to search for the term "react" within all fields, then the query would look like this `react`.
    
      You can also combine search terms logically with the AND and OR operators.
      For example if you want to search for a Java file that contains the term "static void main", then the query would look like this `extension:java AND content:"static void main"`.
      As you can see, if you want to combine multiple words into one term, then you need to surround them with double quotes (`"`).
      """;
  }

  @Override
  public Class<SearchInput> getInputClass() {
    return SearchInput.class;
  }

  @Override
  public ToolResult execute(SearchInput searchInput) {
    log.trace("executing request {}", searchInput);

    return searchExtensions.stream()
      .filter(e -> searchInput.getType().equals(e.getSearchType()))
      .findFirst()
      .map(extension -> runSearchWithExtension(searchInput, extension))
      .orElse(
        ToolResult.error(
          String.format("The search type '%s' does not exist; please choose one of '%s'",
            searchInput.getType(),
            searchExtensions.stream().map(ToolSearchExtension::getSearchType).collect(Collectors.joining("', '"))
          )
        )
      );
  }

  private ToolResult runSearchWithExtension(SearchInput searchInput, ToolSearchExtension extension) {
    List<Hit> hits = searchEngine.forType(searchInput.getType())
      .search()
      .start(searchInput.getPage() * searchInput.getPageSize())
      .limit(searchInput.getPageSize())
      .execute(searchInput.getQuery()).getHits();

    Map<String, Object> structuredResults = new HashMap<>(hits.size());
    if (hits.isEmpty()) {
      log.trace("found {} hit(s)", hits.size());
      return OkResultRenderer.ok("EMPTY", "Nothing found for the query.").render();
    } else {
      return handleHits(extension, hits, structuredResults);
    }
  }

  private ToolResult handleHits(ToolSearchExtension extension, List<Hit> hits, Map<String, Object> structuredResults) {
    OkResultRenderer resultRenderer = OkResultRenderer.success(String.format("Found %d hits", hits.size()));
    resultRenderer.withInfoText("More details may be found in the structured result using the hit number as the key.");

    renderTableHeader(extension, resultRenderer);

    int hitNr = 0;
    for (Hit hit : hits) {
      ++hitNr;
      resultRenderer.append(hitNr).append(" | ");
      resultRenderer.appendLine(String.join(" | ", extension.transformHitToTableFields(hit)));
      structuredResults.put(Integer.toString(hitNr), extension.transformHitToStructuredAnswer(hit));
    }

    log.trace("found {} hit(s)", hits.size());
    return resultRenderer.render(structuredResults);
  }

  private static void renderTableHeader(ToolSearchExtension extension, OkResultRenderer resultRenderer) {
    resultRenderer.append("Hit Nr. | ");
    resultRenderer.appendLine(String.join(" | ", extension.tableColumnHeader()));
    for (int i = 0; i < extension.tableColumnHeader().length; ++i) {
      if (i > 0) {
        resultRenderer.append("|");
      }
      resultRenderer.append("---");
    }
    resultRenderer.append('\n');
  }
}

@Getter
@Setter(AccessLevel.PACKAGE) // used for testing
@ToString
class SearchInput {

  @NotEmpty
  @JsonPropertyDescription("Query that is used to search for the results. Do not try to set the query type here, only use the terms that are searched for.")
  private String query;

  @Min(0)
  @JsonPropertyDescription("Page of the result that should be returned.")
  private int page = 0;

  @Min(1)
  @JsonPropertyDescription("Amount of results that should be shown per page")
  private int pageSize = 10;

  @NotNull
  @JsonPropertyDescription("The type of object the user is search for. For example 'repository'")
  private String type = "repository";
}
