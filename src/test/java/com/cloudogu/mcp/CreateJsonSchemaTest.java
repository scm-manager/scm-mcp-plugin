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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class CreateJsonSchemaTest {

  private static final String GET_REPOSITORIES_SCHEMA = """
    {
      "$schema" : "https://json-schema.org/draft/2020-12/schema",
      "type" : "object",
      "id" : "tools/list-all-repositories"
    }""";

  private static final String CREATE_OR_EDIT_FILES_SCHEMA = """
    {
      "$schema" : "https://json-schema.org/draft/2020-12/schema",
      "type" : "object",
      "properties" : {
        "commitMessage" : {
          "type" : "string",
          "description" : "Commit message for the change",
          "default" : "Change by MCP server"
        },
        "files" : {
          "description" : "A list of files to create or edit.",
          "type" : "array",
          "items" : {
            "type" : "object",
            "properties" : {
              "content" : {
                "type" : "string",
                "description" : "The content for the file."
              },
              "path" : {
                "type" : "string",
                "description" : "The path for the file that needs to be created or edited."
              }
            },
            "required" : [ "content", "path" ]
          }
        },
        "name" : {
          "type" : "string",
          "description" : "The name for the new repository",
          "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9.][A-Za-z0-9.\\\\-_]*$"
        },
        "namespace" : {
          "type" : "string",
          "description" : "The namespace for the new repository"
        }
      },
      "required" : [ "commitMessage", "files", "name", "namespace" ],
      "id" : "tools/create-or-edit-files"
    }""";

  private static final String SEARCH_GLOBALLY_SCHEMA = """
      {
        "$schema" : "https://json-schema.org/draft/2020-12/schema",
        "type" : "object",
        "properties" : {
          "page" : {
            "type" : "integer",
            "description" : "Which page of the result should be shown",
            "default" : 0,
            "minimum" : 0
          },
          "pageSize" : {
            "type" : "integer",
            "description" : "Amount of results that should be shown per page",
            "default" : 10,
            "minimum" : 1
          },
          "query" : {
            "type" : "string",
            "description" : "Query that is used to search for the results"
          },
          "type" : {
            "type" : "string",
            "enum" : [ "repository", "content" ],
            "description" : "The type of object the user is search for. For example 'repository'",
            "default" : "repository"
          }
        },
        "required" : [ "query", "type" ],
        "id" : "tools/search-globally"
      }""";

  private static final String CREATE_REPOSITORY_SCHEMA_WITH_NAMESPACES = """
    {
      "$schema" : "https://json-schema.org/draft/2020-12/schema",
      "type" : "object",
      "properties" : {
        "contact" : {
          "type" : "string",
          "description" : "Optional contact in form of an email address",
          "format" : "email"
        },
        "description" : {
          "type" : "string",
          "description" : "Optional description for the repository"
        },
        "name" : {
          "type" : "string",
          "description" : "The name for the new repository",
          "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9.][A-Za-z0-9.\\\\-_]*$"
        },
        "namespace" : {
          "type" : "string",
          "description" : "The namespace for the new repository"
        },
        "type" : {
          "type" : "string",
          "enum" : [ "git", "hg", "svn" ],
          "description" : "The type of the new repository. This must be either 'git', 'hg', or 'svn'",
          "default" : "git"
        }
      },
      "required" : [ "name", "namespace" ],
      "id" : "tools/create-repository"
    }""";

  private static final String CREATE_REPOSITORY_SCHEMA_WITHOUT_NAMESPACES = """
    {
      "$schema" : "https://json-schema.org/draft/2020-12/schema",
      "type" : "object",
      "properties" : {
        "contact" : {
          "type" : "string",
          "description" : "Optional contact in form of an email address",
          "format" : "email"
        },
        "description" : {
          "type" : "string",
          "description" : "Optional description for the repository"
        },
        "name" : {
          "type" : "string",
          "description" : "The name for the new repository",
          "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9.][A-Za-z0-9.\\\\-_]*$"
        },
        "type" : {
          "type" : "string",
          "enum" : [ "git", "hg", "svn" ],
          "description" : "The type of the new repository. This must be either 'git', 'hg', or 'svn'",
          "default" : "git"
        }
      },
      "required" : [ "name" ],
      "id" : "tools/create-repository"
    }""";

  @ParameterizedTest(name = "Tool: {0}")
  @MethodSource("provideClassesAndStrings")
  void shouldGenerateJsonSchemas(Tool tool, String expectedSchema) {
    String schema = tool.getInputSchema();
    Assertions.assertThat(schema).isEqualTo(expectedSchema);
  }

  static Stream<Arguments> provideClassesAndStrings() {
    return Stream.of(
      Arguments.of(new ToolGetRepositories(null, null), GET_REPOSITORIES_SCHEMA),
      Arguments.of(new ToolCreateOrEditFiles(null), CREATE_OR_EDIT_FILES_SCHEMA),
      Arguments.of(new ToolSearchGlobally(null), SEARCH_GLOBALLY_SCHEMA),
      Arguments.of(new ToolCreateRepository(null, null, false), CREATE_REPOSITORY_SCHEMA_WITHOUT_NAMESPACES),
      Arguments.of(new ToolCreateRepository(null, null, true), CREATE_REPOSITORY_SCHEMA_WITH_NAMESPACES)
    );
  }
}
