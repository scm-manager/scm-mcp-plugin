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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.repository.api.ScmProtocol;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(value = "trillian", permissions = "*")
class ToolCreateRepositoryTest {

  @Mock
  private RepositoryManager repositoryManager;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RepositoryServiceFactory repositoryServiceFactory;

  private ToolCreateRepository tool;

  @BeforeEach
  void initTool() {
    tool = new ToolCreateRepository(repositoryManager, new RepositoryMapperImpl(), true, repositoryServiceFactory);
    when(repositoryManager.create(any()))
      .thenAnswer(invocation -> invocation.getArgument(0));
    when(repositoryServiceFactory.create(any(Repository.class)).getSupportedProtocols())
      .thenReturn(Stream.of(
        new ScmProtocol() {
          @Override
          public String getType() {
            return "http";
          }

          @Override
          public String getUrl() {
            return "http://hitchhiker.org/scm/repo/hitchhiker/hog";
          }
        }
      ));
  }

  @Test
  void shouldCreateRepository() {
    CreateRepositoryInputWithNamespace input = new CreateRepositoryInputWithNamespace();
    input.setNamespace("hitchhike");
    input.setName("hog");

    ToolResult result = tool.execute(input);

    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] The repository 'hitchhike/hog' has been created successfully.
        INFO: The repository can be viewed or cloned using the url http://hitchhiker.org/scm/repo/hitchhiker/hog
        """);
    verify(repositoryManager)
      .create(argThat(repository -> {
        assertThat(repository.getPermissions())
          .hasSize(1)
          .extracting("name")
          .containsExactly("trillian");
        return true;
      }));
  }
}
