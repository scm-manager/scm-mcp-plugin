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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.repository.Branch;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(value = "trillian", permissions = "*")
class ToolCreateBranchTest {

  private static final Repository REPOSITORY = RepositoryTestData.createHeartOfGold();

  @Mock
  private RepositoryServiceFactory repositoryServiceFactory;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RepositoryService repositoryService;
  @Mock
  private BranchFrontendLinkResolver linkResolver;

  @Test
  void shouldCreateBranch() {
    CreateBranchInput input = mock(CreateBranchInput.class);
    when(input.getNamespace()).thenReturn(REPOSITORY.getNamespace());
    when(input.getName()).thenReturn(REPOSITORY.getName());
    when(input.getBaseBranch()).thenReturn("main");
    when(input.getNewBranchName()).thenReturn("feature/answer");
    when(repositoryServiceFactory.create(REPOSITORY.getNamespaceAndName())).thenReturn(repositoryService);
    when(repositoryService.getRepository()).thenReturn(REPOSITORY);

    Branch branch = mock(Branch.class);
    when(branch.getName()).thenReturn("feature/answer");
    when(branch.getRevision()).thenReturn("42");
    when(repositoryService.getBranchCommand().from("main").branch("feature/answer")).thenReturn(branch);
    when(linkResolver.createLink(any())).thenReturn(
      FrontendLinkResult.of("branch", "feature/answer", "https://scm.example/repo/hitchhiker/HeartOfGold/code/sources/feature%2Fanswer", null)
    );

    ToolResult result = new ToolCreateBranch(repositoryServiceFactory, linkResolver).execute(input);

    assertThat(result.getContent()).containsExactly("""
      STATUS: [SUCCESS] The new branch [feature/answer](https://scm.example/repo/hitchhiker/HeartOfGold/code/sources/feature%2Fanswer) has been created on revision 42.
      """);
  }
}
