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

import org.github.sdorra.jse.ShiroExtension;
import org.github.sdorra.jse.SubjectAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.io.ContentType;
import sonia.scm.io.ContentTypeResolver;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.repository.api.CatCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(value = "trillian", permissions = "*")
class ToolReadFileTest {

  @Mock
  private RepositoryServiceFactory repositoryServiceFactory;
  @Mock
  private RepositoryService repositoryService;
  @Mock
  private ContentTypeResolver contentTypeResolver;
  @InjectMocks
  private ToolReadFile tool;

  @Mock(answer = Answers.RETURNS_SELF)
  private CatCommandBuilder catCommandBuilder;

  @Nested
  class WithRepository {

    private static final Repository REPOSITORY = RepositoryTestData.createHeartOfGold();

    @BeforeEach
    void mockRepositoryServiceFactory() {
      when(repositoryServiceFactory.create(new NamespaceAndName(REPOSITORY.getNamespace(), REPOSITORY.getName())))
        .thenReturn(repositoryService);
      when(repositoryService.getCatCommand())
        .thenReturn(catCommandBuilder);
      when(repositoryService.getRepository())
        .thenReturn(REPOSITORY);
    }

    @Nested
    class WithSimpleContent {

      private ReadFilesInput input;

      @BeforeEach
      void mockContent() throws IOException {
        when(catCommandBuilder.getStream("README.md"))
          .thenAnswer(x -> new ByteArrayInputStream("""
            # Heart of Gold
            
            A spacecraft equipped with
            Infinite Improbability Drive.""".getBytes(StandardCharsets.UTF_8)));
        when(contentTypeResolver.resolve(eq("README.md"), any()))
          .thenReturn(createContentType("text/x-web-markdown", true, of("Markdown")));

        input = new ReadFilesInput();
        input.setNamespace("hitchhiker");
        input.setName("HeartOfGold");
        input.setPath("README.md");
      }

      @Test
      void readCompleteFile() {
        ToolResult result = tool.execute(input);

        assertThat(result.isError()).isFalse();
        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [COMPLETE] Showing all lines 1-4 of `README.md`.
          INFO: The content type for this file is text/x-web-markdown. The detected language is Markdown.
          ---------------------------------------------------------
          ```
          1 | # Heart of Gold
          2 |\s
          3 | A spacecraft equipped with
          4 | Infinite Improbability Drive.
          ```
          """);
      }

      @Test
      void readFileWithLeadingSlash() {
        input.setPath("/README.md");

        ToolResult result = tool.execute(input);

        assertThat(result.isError()).isFalse();
        assertThat(result.getContent().get(0)).contains("of `README.md`");
      }

      @Test
      void readPartialFile() {
        input.setLineRange("1-3");

        ToolResult result = tool.execute(input);

        assertThat(result.isError()).isFalse();
        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [TRUNCATED] Showing lines 1-3 of `README.md`.
          INFO: The content type for this file is text/x-web-markdown. The detected language is Markdown.
          ---------------------------------------------------------
          ```
          1 | # Heart of Gold
          2 |\s
          3 | A spacecraft equipped with
          ```
          """);
      }

      @Test
      void readMiddlePartOfFile() {
        input.setLineRange("2-3");

        ToolResult result = tool.execute(input);

        assertThat(result.isError()).isFalse();
        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [TRUNCATED] Showing lines 2-3 of `README.md`.
          INFO: The content type for this file is text/x-web-markdown. The detected language is Markdown.
          ---------------------------------------------------------
          ```
          2 |\s
          3 | A spacecraft equipped with
          ```
          """);
      }

      @Test
      void readEndOfFile() {
        input.setLineRange("2-5");

        ToolResult result = tool.execute(input);

        assertThat(result.isError()).isFalse();
        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [COMPLETE] Showing all lines 2-4 of `README.md`.
          INFO: The content type for this file is text/x-web-markdown. The detected language is Markdown.
          ---------------------------------------------------------
          ```
          2 |\s
          3 | A spacecraft equipped with
          4 | Infinite Improbability Drive.
          ```
          """);
      }

      @Test
      void readCompleteFileSharp() {
        input.setLineRange("1-4");

        ToolResult result = tool.execute(input);

        assertThat(result.isError()).isFalse();
        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [COMPLETE] Showing all lines 1-4 of `README.md`.
          INFO: The content type for this file is text/x-web-markdown. The detected language is Markdown.
          ---------------------------------------------------------
          ```
          1 | # Heart of Gold
          2 |\s
          3 | A spacecraft equipped with
          4 | Infinite Improbability Drive.
          ```
          """);
      }

      @Test
      void readOutOfBounds() {
        input.setLineRange("6-10");

        ToolResult result = tool.execute(input);

        assertThat(result.isError()).isFalse();
        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [EMPTY] Range outside of file bounds of `README.md`.
          INFO: The content type for this file is text/x-web-markdown. The detected language is Markdown.
          """);
      }
    }

    @Test
    void readEmptyFile() throws IOException {
      when(catCommandBuilder.getStream(".keep"))
        .thenReturn(new ByteArrayInputStream(new byte[0]));

      ReadFilesInput input = new ReadFilesInput();
      input.setNamespace("hitchhiker");
      input.setName("HeartOfGold");
      input.setPath(".keep");

      when(contentTypeResolver.resolve(eq(".keep"), any()))
        .thenReturn(createContentType("application/octet-stream", false, empty()));

      ToolResult result = tool.execute(input);

      assertThat(result.isError()).isFalse();
      assertThat(result.getContent().get(0)).isEqualTo("""
        STATUS: [EMPTY] The file `.keep` is empty.
        """);
    }

    @Test
    void readBinaryFile() throws IOException {
      when(catCommandBuilder.getStream("jars/commons.jar"))
        .thenReturn(new ByteArrayInputStream(new byte[10]));

      ReadFilesInput input = new ReadFilesInput();
      input.setNamespace("hitchhiker");
      input.setName("HeartOfGold");
      input.setPath("jars/commons.jar");

      when(contentTypeResolver.resolve(eq("jars/commons.jar"), any()))
        .thenReturn(createContentType("application/java-archive", false, empty()));

      ToolResult result = tool.execute(input);

      assertThat(result.isError()).isFalse();
      assertThat(result.getContent().get(0)).isEqualTo("""
        STATUS: [BINARY FILE] The file `jars/commons.jar` has binary content and cannot be displayed as text.
        INFO: The detected content type of this file is `application/java-archive`.
        """);
    }
  }

  private static ContentType createContentType(String raw, boolean isText, Optional<String> language) {
    return new ContentType() {
      @Override
      public String getPrimary() {
        return "";
      }

      @Override
      public String getSecondary() {
        return "";
      }

      @Override
      public String getRaw() {
        return raw;
      }

      @Override
      public boolean isText() {
        return isText;
      }

      @Override
      public Optional<String> getLanguage() {
        return language;
      }
    };
  }
}
