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
import sonia.scm.repository.BrowserResult;
import sonia.scm.repository.FileObject;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.repository.api.BrowseCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(value = "trillian", permissions = "*")
class ToolListFilesTest {

  @Mock
  private RepositoryServiceFactory repositoryServiceFactory;
  @Mock
  private RepositoryService repositoryService;

  @InjectMocks
  private ToolListFiles tool;

  @Mock(answer = Answers.RETURNS_SELF)
  private BrowseCommandBuilder browseCommandBuilder;

  @Nested
  class WithRepository {

    private static final Repository REPOSITORY = RepositoryTestData.createHeartOfGold();
    private ListFilesInput input = new ListFilesInput();

    @BeforeEach
    void mockRepositoryServiceFactory() {
      when(repositoryServiceFactory.create(new NamespaceAndName(REPOSITORY.getNamespace(), REPOSITORY.getName())))
        .thenReturn(repositoryService);
      when(repositoryService.getBrowseCommand())
        .thenReturn(browseCommandBuilder);
      when(repositoryService.getRepository())
        .thenReturn(REPOSITORY);

      input.setNamespace(REPOSITORY.getNamespace());
      input.setName(REPOSITORY.getName());
    }

    @Test
    void shouldListRoot() throws IOException {
      FileObject root = createDirectory("");
      root.setChildren(
        List.of(
          createDirectory("docs"),
          createDirectory("src"),
          createFile(".gitignore"),
          createFile("README.md")
        )
      );
      when(browseCommandBuilder.getBrowserResult())
        .thenReturn(new BrowserResult(null, root));

      ToolResult result = tool.execute(input);

      assertThat(result.getContent().get(0))
        .isEqualTo("""
          STATUS: [SUCCESS] File listing complete.
          INFO: Found 2 directories and 2 files. The result is complete.
          ---------------------------------------------------------
          └── /
              ├── docs/
              ├── src/
              ├── .gitignore
              └── README.md
          """);
    }

    @Test
    void shouldListRecursively() throws IOException {
      input.setRecursive(true);
      FileObject root = createDirectory("");
      FileObject docsDir = createDirectory("docs");
      docsDir.setChildren(
        List.of(
          createFile("index.md")
        )
      );
      FileObject srcDir = createDirectory("src");
      FileObject srcMainDir = createDirectory("main");
      FileObject srcTestDir = createDirectory("test");
      srcMainDir.setChildren(
        List.of(
          createFile("Main.java")
        )
      );
      srcTestDir.setChildren(
        List.of(
          createFile("MainTest.java")
        )
      );
      srcDir.setChildren(
        List.of(
          srcMainDir,
          srcTestDir
        )
      );
      root.setChildren(
        List.of(
          docsDir,
          srcDir,
          createFile(".gitignore"),
          createFile("README.md")
        )
      );
      when(browseCommandBuilder.getBrowserResult())
        .thenReturn(new BrowserResult(null, root));

      ToolResult result = tool.execute(input);

      verify(browseCommandBuilder).setRecursive(true);
      assertThat(result.getContent().get(0))
        .isEqualTo("""
          STATUS: [SUCCESS] File listing complete.
          INFO: Found 4 directories and 5 files. The result is complete.
          ---------------------------------------------------------
          └── /
              ├── docs/
              │   └── index.md
              ├── src/
              │   ├── main/
              │   │   └── Main.java
              │   └── test/
              │       └── MainTest.java
              ├── .gitignore
              └── README.md
          """);
    }

    @Test
    void shouldListTruncatedRoot() throws IOException {
      input.setMaximumNumberOfFiles(4);
      FileObject root = createDirectory("");
      root.setTruncated(true);
      root.setChildren(
        List.of(
          createDirectory("docs"),
          createDirectory("src"),
          createFile(".gitignore"),
          createFile("README.md")
        )
      );
      when(browseCommandBuilder.getBrowserResult())
        .thenReturn(new BrowserResult(null, root));

      ToolResult result = tool.execute(input);

      verify(browseCommandBuilder).setLimit(4);
      verify(browseCommandBuilder).setRecursive(false);
      assertThat(result.getContent().get(0))
        .isEqualTo("""
          STATUS: [TRUNCATED] Maximum file limit reached.
          INFO: Displaying the first 2 directories and 2 files.
          ---------------------------------------------------------
          └── /
              ├── docs/
              ├── src/
              ├── .gitignore
              └── README.md
          """);
    }
  }

  private FileObject createDirectory(String path) {
    FileObject directory = createFile(path);
    directory.setDirectory(true);
    return directory;
  }

  private FileObject createFile(String path) {
    FileObject file = new FileObject();
    file.setName(path);
    return file;
  }
}
