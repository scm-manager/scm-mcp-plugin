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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.search.Hit;
import sonia.scm.search.SearchEngine;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolSearchGloballyTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SearchEngine searchEngine;

  @Test
  void shouldConvertResultsAsTable() {
    when(
      searchEngine
        .forType("repository")
        .search()
        .start(0)
        .limit(10)
        .execute("heart")
        .getHits()
    )
      .thenReturn(
        List.of(
          new Hit("irrelevant1", "ab1", 0.9f,
            Map.of(
              "namespace", new Hit.ValueField("hitchhiker"),
              "name", new Hit.ValueField("HeartOfGold"),
              "type", new Hit.ValueField("git"),
              "creationDate", new Hit.ValueField(System.currentTimeMillis())
            )),
          new Hit("irrelevant2", "12a", 0.8f,
            Map.of(
              "namespace", new Hit.ValueField("heart_planets"),
              "name", new Hit.ValueField("Earth"),
              "type", new Hit.ValueField("git"),
              "creationDate", new Hit.ValueField(System.currentTimeMillis()),
              "description", new Hit.HighlightedField(new String[]{"This is the heart of all mankind"})
            ))
        )
      );

    ToolSearchGlobally tool = new ToolSearchGlobally(searchEngine, Set.of(new RepositorySearchExtension()));

    SearchInput searchInput = new SearchInput();
    searchInput.setQuery("heart");
    ToolResult result = tool.execute(searchInput);

    assertThat(result.getContent().get(0))
      .isEqualTo("""
        STATUS: [SUCCESS] Found 2 hits
        INFO: More details may be found in the structured result using the hit number as the key.
        ---------------------------------------------------------
        Hit Nr. | Repository | Repository Type | Description (if the search term was found here)
        ---|---|---
        1 | hitchhiker/HeartOfGold | git |\s
        2 | heart_planets/Earth | git | This is the heart of all mankind
        """);
  }

  @Test
  void shouldHandleEmptySearchResult() {
    when(
      searchEngine
        .forType("repository")
        .search()
        .start(0)
        .limit(10)
        .execute("vogon")
        .getHits()
    )
      .thenReturn(emptyList());

    ToolSearchGlobally tool = new ToolSearchGlobally(searchEngine, Set.of(new RepositorySearchExtension()));

    SearchInput searchInput = new SearchInput();
    searchInput.setQuery("vogon");
    ToolResult result = tool.execute(searchInput);

    assertThat(result.getContent().get(0))
      .isEqualTo("""
        STATUS: [EMPTY] Nothing found for the query.
        """);
  }

  @Test
  void shouldReturnStructuredContent() {
    when(
      searchEngine
        .forType("repository")
        .search()
        .start(0)
        .limit(10)
        .execute("heart")
        .getHits()
    )
      .thenReturn(
        List.of(
          new Hit("irrelevant1", "ab1", 0.9f,
            Map.of(
              "namespace", new Hit.ValueField("hitchhiker"),
              "name", new Hit.ValueField("HeartOfGold"),
              "type", new Hit.ValueField("git"),
              "creationDate", new Hit.ValueField(System.currentTimeMillis())
            )),
          new Hit("irrelevant2", "12a", 0.8f,
            Map.of(
              "namespace", new Hit.ValueField("heart_planets"),
              "name", new Hit.ValueField("Earth"),
              "type", new Hit.ValueField("git"),
              "creationDate", new Hit.ValueField(System.currentTimeMillis()),
              "description", new Hit.HighlightedField(new String[]{"This is the heart of all mankind"})
            ))
        )
      );

    ToolSearchGlobally tool = new ToolSearchGlobally(searchEngine, Set.of(new RepositorySearchExtension()));

    SearchInput searchInput = new SearchInput();
    searchInput.setQuery("heart");
    ToolResult result = tool.execute(searchInput);

    Map<String, Object> structuredContent = result.getStructuredContent();

    Object firstRepo = structuredContent.get("1");
    assertThat(firstRepo)
      .extracting("type")
      .isEqualTo("git");
    assertThat(firstRepo)
      .extracting("repository")
      .isEqualTo("hitchhiker/HeartOfGold");

    Object secondRepo = structuredContent.get("2");
    assertThat(secondRepo)
      .extracting("type")
      .isEqualTo("git");
    assertThat(secondRepo)
      .extracting("repository")
      .isEqualTo("heart_planets/Earth");
    assertThat(secondRepo)
      .extracting("description")
      .isEqualTo("This is the heart of all mankind");
  }
}
