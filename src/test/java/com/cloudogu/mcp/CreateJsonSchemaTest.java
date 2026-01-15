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

import java.util.Set;
import java.util.stream.Stream;

class CreateJsonSchemaTest {

  private static final String GET_REPOSITORIES_SCHEMA = """
    {
      "$schema" : "https://json-schema.org/draft/2020-12/schema",
      "type" : "object",
      "properties" : {
        "includeDetails" : {
          "type" : "boolean",
          "description" : "If set to `true`, details for the repositories will be sent as structured data.\\nIf `false`, only the list of namespaces and names with links to the repositories and the repository types will be returned\\nwith an additional map of the first 100 bytes of the descriptions for the repositories, if availabe\\n(the keys for this map are the namespace/name pairs of the repositories;\\n repositories without description will have no entry in this map).",
          "default" : false
        },
        "name" : {
          "type" : "string",
          "description" : "If set, list only the repositories with this name.",
          "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\.][A-Za-z0-9\\\\.\\\\-_]*$"
        },
        "namespace" : {
          "type" : "string",
          "description" : "If set, list only the repositories from this namespace.",
          "pattern" : "^(?:(?:[^:/?#;&=\\\\s@%\\\\\\\\][^:/?#;&=%\\\\\\\\]*[^:/?#;&=\\\\s%\\\\\\\\])|(?:[^:/?#;&=\\\\s@%\\\\\\\\]))$"
        },
        "type" : {
          "type" : "string",
          "enum" : [ "git", "svn", "hg" ],
          "description" : "If set, list only the repositories with this type."
        }
      },
      "id" : "tools/list-repositories"
    }""";

  private static final String CREATE_OR_EDIT_FILES_SCHEMA = """
    {
      "$schema" : "https://json-schema.org/draft/2020-12/schema",
      "type" : "object",
      "properties" : {
        "branch" : {
          "type" : "string",
          "description" : "The target branch where the files should be created or edited. If omitted, the default branch will be used.",
          "pattern" : "[^.\\\\\\\\/\\\\s\\\\[~^:?*](?:[^\\\\\\\\/\\\\s\\\\[~^:?*]*[^.\\\\\\\\/\\\\s\\\\[~^:?*])?(?:/[^.\\\\\\\\/\\\\s\\\\[~^:?*](?:[^\\\\\\\\/\\\\s\\\\[~^:?*]*[^.\\\\\\\\/\\\\s\\\\[~^:?*])?)*"
        },
        "commitMessage" : {
          "type" : "string",
          "description" : "Commit message for the change",
          "default" : "Change by MCP server",
          "minLength" : 1
        },
        "filesToCreateOrEdit" : {
          "description" : "A list of files to create or edit.",
          "default" : [ ],
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
                "description" : "The path for the file that needs to be created or edited.",
                "minLength" : 1
              }
            },
            "required" : [ "content", "path" ],
            "default" : [ ]
          }
        },
        "filesToDelete" : {
          "description" : "A list of files that shall be deleted.\\nThis does not work for directories.\\nIf you want to delete a directory completely, you have to list all files recursively first and delete each single file.",
          "default" : [ ],
          "type" : "array",
          "items" : {
            "type" : "object",
            "properties" : {
              "path" : {
                "type" : "string",
                "description" : "The path of the file that shall be deleted.",
                "minLength" : 1
              }
            },
            "required" : [ "path" ],
            "default" : [ ]
          }
        },
        "filesToMove" : {
          "description" : "A list of files that shall be moved or renamed.\\nThis does not work for directories.\\nIf you want to rename a directory, you have to list all files recursively first and move each single file.",
          "default" : [ ],
          "type" : "array",
          "items" : {
            "type" : "object",
            "properties" : {
              "fromPath" : {
                "type" : "string",
                "description" : "The path of the file that shall be moved/renamed.",
                "minLength" : 1
              },
              "toPath" : {
                "type" : "string",
                "description" : "The new path of the file.\\nIf the file should be moved to another directory, specify the complete new path here including\\nthe name of the file itself, not just the target directory.\\nIf you want to rename the file, again specify the complete new path here including the directory.",
                "minLength" : 1
              }
            },
            "required" : [ "fromPath", "toPath" ],
            "default" : [ ]
          }
        },
        "name" : {
          "type" : "string",
          "description" : "The name of the repository where the files should be created or edited.",
          "minLength" : 1,
          "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\.][A-Za-z0-9\\\\.\\\\-_]*$"
        },
        "namespace" : {
          "type" : "string",
          "description" : "The namespace of the repository where the files should be created or edited.",
          "minLength" : 1,
          "pattern" : "^(?:(?:[^:/?#;&=\\\\s@%\\\\\\\\][^:/?#;&=%\\\\\\\\]*[^:/?#;&=\\\\s%\\\\\\\\])|(?:[^:/?#;&=\\\\s@%\\\\\\\\]))$"
        }
      },
      "required" : [ "commitMessage", "name", "namespace" ],
      "id" : "tools/modify-files"
    }""";

  private static final String SEARCH_GLOBALLY_SCHEMA = """
    {
      "$schema" : "https://json-schema.org/draft/2020-12/schema",
      "type" : "object",
      "properties" : {
        "page" : {
          "type" : "integer",
          "description" : "Page of the result that should be returned.",
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
          "description" : "Query that is used to search for the results. Do not try to set the query type here, only use the terms that are searched for.",
          "minLength" : 1
        },
        "type" : {
          "type" : "string",
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
          "minLength" : 1,
          "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\.][A-Za-z0-9\\\\.\\\\-_]*$"
        },
        "namespace" : {
          "type" : "string",
          "description" : "The namespace for the new repository",
          "minLength" : 1,
          "pattern" : "^(?:(?:[^:/?#;&=\\\\s@%\\\\\\\\][^:/?#;&=%\\\\\\\\]*[^:/?#;&=\\\\s%\\\\\\\\])|(?:[^:/?#;&=\\\\s@%\\\\\\\\]))$"
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
          "minLength" : 1,
          "pattern" : "(?!^\\\\.\\\\.$)(?!^\\\\.$)(?!.*[\\\\\\\\\\\\[\\\\]])(?!.*[.]git$)^[A-Za-z0-9\\\\.][A-Za-z0-9\\\\.\\\\-_]*$"
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
      Arguments.of(new ToolListRepositories(null, null), GET_REPOSITORIES_SCHEMA),
      Arguments.of(new ToolModifyFiles(null), CREATE_OR_EDIT_FILES_SCHEMA),
      Arguments.of(new ToolSearchGlobally(null, Set.of(new RepositorySearchExtension())), SEARCH_GLOBALLY_SCHEMA),
      Arguments.of(new ToolCreateRepository(null, null, false, null), CREATE_REPOSITORY_SCHEMA_WITHOUT_NAMESPACES),
      Arguments.of(new ToolCreateRepository(null, null, true, null), CREATE_REPOSITORY_SCHEMA_WITH_NAMESPACES)
    );
  }
}
