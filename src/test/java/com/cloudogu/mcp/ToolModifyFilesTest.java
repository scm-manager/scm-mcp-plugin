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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.repository.api.ModifyCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(value = "trillian", permissions = "*")
class ToolModifyFilesTest {

  private static final Repository REPOSITORY = RepositoryTestData.createHeartOfGold();

  @Mock
  private RepositoryServiceFactory repositoryServiceFactory;
  @Mock
  private RepositoryService repositoryService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ModifyCommandBuilder modifyCommandBuilder;
  @Mock
  private CommitFrontendLinkResolver commitLinkResolver;
  @Mock
  private FileFrontendLinkResolver fileLinkResolver;
  @Mock
  private RepositoryFrontendLinkResolver repositoryLinkResolver;

  private ToolModifyFiles tool;

  @BeforeEach
  void setUp() {
    tool = new ToolModifyFiles(repositoryServiceFactory, commitLinkResolver, fileLinkResolver, repositoryLinkResolver);
    when(repositoryServiceFactory.create(REPOSITORY.getNamespaceAndName())).thenReturn(repositoryService);
    when(repositoryService.getRepository()).thenReturn(REPOSITORY);
    when(repositoryService.getModifyCommand()).thenReturn(modifyCommandBuilder);
    when(modifyCommandBuilder.setCommitMessage("Test changes")).thenReturn(modifyCommandBuilder);
    when(modifyCommandBuilder.execute()).thenReturn("42");
    when(commitLinkResolver.createLink(any())).thenReturn(link("https://scm.example/commit/42"));
    when(repositoryLinkResolver.createLink(any())).thenReturn(link("https://scm.example/repository"));
  }

  @Test
  void shouldCreateOrModifyFile() throws IOException {
    FileModificationEntry modification = mock(FileModificationEntry.class);
    when(modification.getPath()).thenReturn("README.md");
    when(modification.getContent()).thenReturn("The answer is 42.");
    when(fileLinkResolver.createLink(any())).thenReturn(link("https://scm.example/files/README.md"));

    ToolResult result = tool.execute(input(List.of(modification), emptyList(), emptyList()));

    ArgumentCaptor<InputStream> data = ArgumentCaptor.forClass(InputStream.class);
    verify(modifyCommandBuilder.createFile("README.md").setOverwrite(true)).withData(data.capture());
    assertThat(new String(data.getValue().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("The answer is 42.");
    assertThat(result.getContent().get(0))
      .contains("Created or modified 1 files, moved 0 files, and deleted 0 files")
      .contains("[README.md](https://scm.example/files/README.md)");
  }

  @Test
  void shouldMoveFile() {
    FileMoveEntry move = mock(FileMoveEntry.class);
    when(move.getFromPath()).thenReturn("README.md");
    when(move.getToPath()).thenReturn("docs/README.md");
    when(fileLinkResolver.createLink(any())).thenReturn(link("https://scm.example/files/docs/README.md"));

    ToolResult result = tool.execute(input(emptyList(), List.of(move), emptyList()));

    verify(modifyCommandBuilder.move("README.md")).to("docs/README.md");
    assertThat(result.getContent().get(0))
      .contains("Created or modified 0 files, moved 1 files, and deleted 0 files")
      .contains("[docs/README.md](https://scm.example/files/docs/README.md)");
  }

  @Test
  void shouldDeleteFile() {
    FileDeleteEntry deletion = mock(FileDeleteEntry.class);
    when(deletion.getPath()).thenReturn("README.md");

    ToolResult result = tool.execute(input(emptyList(), emptyList(), List.of(deletion)));

    verify(modifyCommandBuilder).deleteFile("README.md");
    assertThat(result.getContent().get(0))
      .contains("Created or modified 0 files, moved 0 files, and deleted 1 files")
      .doesNotContain("You can find the modified or moved files here");
  }

  private ModifyFilesInput input(List<FileModificationEntry> modifications,
                                 List<FileMoveEntry> moves,
                                 List<FileDeleteEntry> deletions) {
    ModifyFilesInput input = mock(ModifyFilesInput.class);
    when(input.getNamespace()).thenReturn(REPOSITORY.getNamespace());
    when(input.getName()).thenReturn(REPOSITORY.getName());
    when(input.getBranch()).thenReturn("main");
    when(input.getCommitMessage()).thenReturn("Test changes");
    when(input.getFilesToCreateOrEdit()).thenReturn(modifications);
    when(input.getFilesToMove()).thenReturn(moves);
    when(input.getFilesToDelete()).thenReturn(deletions);
    return input;
  }

  private FrontendLinkResult link(String url) {
    return FrontendLinkResult.of("test", "test", url, null);
  }
}
