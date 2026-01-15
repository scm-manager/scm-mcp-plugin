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
import lombok.Value;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.github.sdorra.jse.ShiroExtension;
import org.github.sdorra.jse.SubjectAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.repository.Changeset;
import sonia.scm.repository.ChangesetPagingResult;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.Person;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.repository.api.LogCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(value = "trillian", permissions = "*")
class ToolListCommitsTest {

  @Mock
  private RepositoryServiceFactory repositoryServiceFactory;
  @Mock
  private RepositoryService repositoryService;


  @Mock(answer = Answers.RETURNS_SELF)
  private LogCommandBuilder logCommandBuilder;

  @Nested
  class WithRepository {

    private static final Repository REPOSITORY = RepositoryTestData.createHeartOfGold();
    private ToolListCommits tool;
    private final ListCommitsInput input = new ListCommitsInput();
    private final ToolListCommits.CompositeInput compositeInput = new ToolListCommits.CompositeInput(input);

    @BeforeEach
    void createTool() {
      tool = new ToolListCommits(repositoryServiceFactory, emptySet());
    }

    @BeforeEach
    void mockRepositoryServiceFactory() {
      when(repositoryServiceFactory.create(new NamespaceAndName(REPOSITORY.getNamespace(), REPOSITORY.getName())))
        .thenReturn(repositoryService);
      when(repositoryService.getLogCommand())
        .thenReturn(logCommandBuilder);
      when(repositoryService.getRepository())
        .thenReturn(REPOSITORY);

      input.setNamespace(REPOSITORY.getNamespace());
      input.setName(REPOSITORY.getName());
    }

    @Nested
    class WithSimpleCommits {

      @BeforeEach
      void mockCommits() throws IOException {
        Changeset commit1 = new Changeset
          ("23",
            Instant.parse("1985-05-23T21:00:00.000Z").toEpochMilli(),
            new Person("Arthur Dent", "dent@hog.org"),
            """
              Escape from Earth

              Just followed some old friend of mine.
              """);
        commit1.setTags(List.of("1.0"));
        Changeset commit2 = new Changeset(
          "42",
          Instant.parse("1985-06-01T16:00:00.000Z").toEpochMilli(),
          new Person("Trillian McMillan", "trish@hog.org"),
          """
            Fix improbability drive

            The drive got stuck due to some depressed robot.
            """);
        commit2.setParents(List.of("23"));
        lenient().when(logCommandBuilder.getChangesets())
          .thenReturn(new ChangesetPagingResult(
            2,
            List.of(
              commit2,
              commit1
            )
          ));
      }

      @Test
      void shouldListCommits() {
        ToolResult result = tool.execute(compositeInput);

        assertThat(result.getContent().get(0))
          .isEqualTo("""
            STATUS: [SUCCESS] Found all 2 commits of 2 in total.
            ---------------------------------------------------------
            commit 42
            Author: Trillian McMillan <trish@hog.org>
            Date: 1985-06-01T16:00:00Z

                Fix improbability drive

            commit 23 (tag: 1.0)
            Author: Arthur Dent <dent@hog.org>
            Date: 1985-05-23T21:00:00Z

                Escape from Earth

            """);
      }

      @Test
      void shouldListCommitsWithDetails() {
        input.setIncludeDetails(true);
        ToolResult result = tool.execute(new ToolListCommits.CompositeInput(input));

        assertThat(result.getContent().get(0))
          .startsWith("""
            STATUS: [SUCCESS] Found all 2 commits of 2 in total.
            INFO: Detailed metadata (complete commit message labeled as 'description', parents, and contributors) for each commit is available in the structured data block under their respective revisions.
            ---------------------------------------------------------
            commit 42
            """);

        Object commit1 = result.getStructuredContent().get("42");
        assertThat(commit1)
          .extracting("author")
          .extracting("name")
          .isEqualTo("Trillian McMillan");
        assertThat(commit1)
          .extracting("author")
          .extracting("mail")
          .isEqualTo("trish@hog.org");
        assertThat(commit1)
          .extracting("description")
          .isEqualTo("""
            Fix improbability drive

            The drive got stuck due to some depressed robot.
            """);
        assertThat(commit1)
          .extracting("parents")
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .containsExactly("23");

        Object commit2 = result.getStructuredContent().get("23");
        assertThat(commit2)
          .extracting("tags")
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .containsExactly("1.0");
      }

      @Test
      void shouldListCommitsOfRevision() {
        input.setRevision("42");

        ToolResult result = tool.execute(compositeInput);

        verify(logCommandBuilder).setStartChangeset("42");
        assertThat(result.getContent().get(0))
          .startsWith("STATUS: [SUCCESS] Found all 2 commits of 2 in total.");
      }

      @Test
      void shouldListCommitsForAuthor() {
        input.setAuthorFilter("trillian");
        input.setIncludeDetails(true);

        ToolResult result = tool.execute(compositeInput);

        assertThat(result.getContent().get(0))
          .startsWith("STATUS: [SUCCESS] Found all 1 commits of 2 in total.");
        assertThat(result.getStructuredContent().keySet()).containsExactly("42");
      }

      @Test
      void shouldListCommitsForCommitMessage() {
        input.setCommitMessageFilter("Improbability");
        input.setIncludeDetails(true);

        ToolResult result = tool.execute(compositeInput);

        assertThat(result.getContent().get(0))
          .startsWith("STATUS: [SUCCESS] Found all 1 commits of 2 in total.");
        assertThat(result.getStructuredContent().keySet()).containsExactly("42");
      }

      @Test
      void shouldHandleEmptyResult() {
        input.setCommitMessageFilter("Vogons");
        input.setIncludeDetails(true);

        ToolResult result = tool.execute(compositeInput);

        assertThat(result.getContent().get(0))
          .isEqualTo("STATUS: [SUCCESS] None of the commits match your input.\n");
        assertThat(result.getStructuredContent().keySet()).isEmpty();
      }

      @Nested
      class WithExtensions {

        private ToolListCommits tool;
        private ToolListCommitsFilterEnhancement firstExtension;
        private ToolListCommitsFilterEnhancement secondExtension;
        private ToolListCommitsFilterEnhancement thirdExtension;

        @BeforeEach
        void createTool() {
          firstExtension = spy(new ToolListCommitsFilterEnhancement() {
            @Override
            public Optional<Class<?>> getInputClass() {
              return of(FirstExtensionInput.class);
            }

            @Override
            public String getNamespace() {
              return "pullRequest";
            }
          });

          secondExtension = spy(new ToolListCommitsFilterEnhancement() {
            @Override
            public Optional<Class<?>> getInputClass() {
              return of(SecondExtensionInput.class);
            }

            @Override
            public String getNamespace() {
              return "ci";
            }

            @Override
            public boolean includeCommit(Repository repository, Changeset changeset, ToolListCommits.CompositeInput input) {
              SecondExtensionInput extensionInput = (SecondExtensionInput) input.getExtensionInput("ci");
              return extensionInput == null || extensionInput.isIncludeStatus();
            }
          });

          thirdExtension = new ToolListCommitsFilterEnhancement() {
            @Override
            public Optional<Class<?>> getInputClass() {
              return empty();
            }

            @Override
            public String getNamespace() {
              return "none";
            }

            @Override
            public void enhanceStructuredResult(Repository repository, Changeset changeset, ToolListCommits.CompositeInput input, BiConsumer<String, Object> keyValueConsumer) {
              keyValueConsumer.accept("enhancement", "great");
            }
          };

          tool = new ToolListCommits(
            repositoryServiceFactory,
            Set.of(
              firstExtension,
              secondExtension,
              thirdExtension
            )
          );
        }

        @Value
        static class FirstExtensionInput {
          @JsonPropertyDescription("List commits for the given pull request id.")
          String id;
        }

        @Value
        static class SecondExtensionInput {
          @JsonPropertyDescription("If set to true, this will check for ci status of the commits.")
          boolean includeStatus;
        }

        @Test
        void shouldGenerateSchemaWithExtensions() {
          String schema = tool.getInputSchema();
          Assertions.assertThat(schema)
            .contains("""
          {
            "$schema" : "https://json-schema.org/draft/2020-12/schema",
            "type" : "object",
            "properties" : {
              "authorFilter" : {
                "type" : "string",
                "description" : "Filter for commits whose author contains this string. This filter is case insensitive."
              },
              "commitMessageFilter" : {
                "type" : "string",
                "description" : "Filter for commits that contain this string in the commit message. This filter is case insensitive."
              },
              "committedAfter" : {
                "type" : "string",
                "format" : "date-time",
                "description" : "Filter for commits committed before this timestamp (ISO 8601 format, e.g. 2024-01-01T10:00:00Z)."
              },
              "committedBefore" : {
                "type" : "string",
                "format" : "date-time",
                "description" : "Filter for commits committed before this timestamp (ISO 8601 format, e.g. 2024-01-01T10:00:00Z)."
              },
              "includeDetails" : {
                "type" : "boolean",
                "description" : "If set to `true`, details for the commits will be sent as structured data.\\nIf `false`, only the commit log like `git log` would produce will be returned.",
                "default" : false
              },
              "limit" : {
                "type" : "integer",
                "description" : "The maximum number of commits to read.",
                "default" : 20,
                "minimum" : 1
              },
              "name" : {
                "type" : "string",
                "description" : "The name of the repository",
                "minLength" : 1,
                "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\.][A-Za-z0-9\\\\.\\\\-_]*$"
              },
              "namespace" : {
                "type" : "string",
                "description" : "The namespace of the repository",
                "minLength" : 1,
                "pattern" : "^(?:(?:[^:/?#;&=\\\\s@%\\\\\\\\][^:/?#;&=%\\\\\\\\]*[^:/?#;&=\\\\s%\\\\\\\\])|(?:[^:/?#;&=\\\\s@%\\\\\\\\]))$"
              },
              "revision" : {
                "type" : "string",
                "description" : "The revision to list the commits for. This can be either a 'real' revision, a branch, or a tag.\\nIf this is omitted, the default branch of the repository will be taken."
              },
          """)
            .contains("""
          "pullRequest_id" : {
                "type" : "string",
                "description" : "List commits for the given pull request id."
              }""")
            .contains("""
          "ci_includeStatus" : {
                "type" : "boolean",
                "description" : "If set to true, this will check for ci status of the commits."
              }""")
            .contains("""
            },
            "required" : [ "name", "namespace" ],
            "id" : "tools/list-commits"
          }""");

          tool.execute(compositeInput);
        }

        @Test
        void shouldCallConfigureForEachExtensions() {
          ToolListCommits.CompositeInput compositeInput = new ToolListCommits.CompositeInput(input);
          FirstExtensionInput extensionInput1 = new FirstExtensionInput("true");
          compositeInput.addExtensionInput("pullRequest", extensionInput1);
          SecondExtensionInput extensionInput2 = new SecondExtensionInput(true);
          compositeInput.addExtensionInput("ci", extensionInput2);

          tool.execute(compositeInput);

          verify(firstExtension).configure(REPOSITORY, logCommandBuilder, compositeInput);
          verify(secondExtension).configure(REPOSITORY, logCommandBuilder, compositeInput);
        }

        @Test
        void shouldHandleErrorFromExtensions() {
          doReturn(of("This does not work"))
            .when(secondExtension)
            .configure(REPOSITORY, logCommandBuilder, compositeInput);

          ToolResult result = tool.execute(compositeInput);

          assertThat(result.isError()).isTrue();
          assertThat(result.getMessage()).isEqualTo("This does not work");
        }

        @Test
        void shouldIncludeWhenAllExtensionsAreOkay() {
          ToolListCommits.CompositeInput compositeInput = new ToolListCommits.CompositeInput(input);
          FirstExtensionInput extensionInput1 = new FirstExtensionInput("true");
          compositeInput.addExtensionInput("pullRequest", extensionInput1);
          SecondExtensionInput extensionInput2 = new SecondExtensionInput(true);
          compositeInput.addExtensionInput("ci", extensionInput2);

          ToolResult result = tool.execute(compositeInput);

          assertThat(result.getContent()).first().asString().contains("Found all 2 commits");
        }

        @Test
        void shouldRejectResultWhenOneExtensionsIsNotOkay() {
          ToolListCommits.CompositeInput compositeInput = new ToolListCommits.CompositeInput(input);
          FirstExtensionInput extensionInput1 = new FirstExtensionInput("true");
          compositeInput.addExtensionInput("pullRequest", extensionInput1);
          SecondExtensionInput extensionInput2 = new SecondExtensionInput(false);
          compositeInput.addExtensionInput("ci", extensionInput2);

          ToolResult result = tool.execute(compositeInput);

          assertThat(result.getContent()).first().asString().contains("None of the commits match your input");
        }

        @Test
        void shouldEnhanceStructuredResult() {
          input.setIncludeDetails(true);
          ToolListCommits.CompositeInput compositeInput = new ToolListCommits.CompositeInput(input);

          ToolResult result = tool.execute(compositeInput);

          assertThat(result.getStructuredContent().get("23"))
            .extracting("enhancement")
            .isEqualTo("great");
        }
      }
    }

    @Nested
    class WithManyCommits {

      private int pagingStart = 0;
      private int pagingLimit = 20;

      @BeforeEach
      void mockCommits() throws IOException {
        List<Changeset> commits = new ArrayList<>();
        for (int i = 1; i < 100; ++i) {
          Changeset commit = new Changeset
            (Integer.toString(i),
              Instant.parse(String.format("1985-05-23T21:%02d:%02d.000Z", i / 60, i % 60)).toEpochMilli(),
              new Person("Arthur Dent", "dent@hog.org"), String.format("Commit nr. %s", i));
          commits.add(commit);
        }

        Changeset commit = new Changeset
          (Integer.toString(100),
            Instant.parse("1985-05-23T21:00:00.000Z").toEpochMilli(),
            new Person("Trillian McMillan", "trish@hog.org"), "Commit nr. 100");
        commits.add(commit);

        doAnswer(invocationOnMock -> {
          pagingLimit = invocationOnMock.getArgument(0, Integer.class);
          return logCommandBuilder;
        }).when(logCommandBuilder).setPagingLimit(anyInt());
        doAnswer(invocationOnMock -> {
          pagingStart = invocationOnMock.getArgument(0, Integer.class);
          return logCommandBuilder;
        }).when(logCommandBuilder).setPagingStart(anyInt());
        doAnswer(invocationOnMock -> {
          if (pagingStart >= 100) {
            return new ChangesetPagingResult(commits.size(), emptyList());
          }
          return new ChangesetPagingResult(commits.size(), commits.subList(pagingStart, pagingStart + pagingLimit));
        }).when(logCommandBuilder).getChangesets();
      }

      @Test
      void shouldIterateCommitsForFiltersUntilExhausted() {
        input.setAuthorFilter("Trillian");
        input.setIncludeDetails(true);

        ToolResult result = tool.execute(compositeInput);

        assertThat(result.getContent().get(0))
          .startsWith("STATUS: [SUCCESS] Found all 1 commits of 100 in total.");
        assertThat(result.getStructuredContent().keySet()).containsExactly("100");
      }

      @Test
      void shouldIterateCommitsForFiltersUntilLimit() {
        input.setAuthorFilter("Dent");

        ToolResult result = tool.execute(compositeInput);

        assertThat(result.getContent().get(0))
          .startsWith("STATUS: [SUCCESS] Found the first 20 commits of 100 in total.");
      }

      @Test
      void shouldFiltersByTime() {
        input.setCommittedAfter(Instant.parse("1985-05-23T21:00:50.000Z"));
        input.setCommittedBefore(Instant.parse("1985-05-23T21:01:00.000Z"));

        ToolResult result = tool.execute(compositeInput);

        assertThat(result.getContent().get(0))
          .startsWith("STATUS: [SUCCESS] Found all 9 commits of 100 in total.");
      }
    }
  }
}
