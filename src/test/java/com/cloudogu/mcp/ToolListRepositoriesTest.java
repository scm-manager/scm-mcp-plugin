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

import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.repository.spi.HttpScmProtocol;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolListRepositoriesTest {

  private static final Repository REPOSITORY_1 = RepositoryTestData.createHeartOfGold("git");
  private static final Repository REPOSITORY_2 = RepositoryTestData.create42Puzzle("git");
  public static final String LONG_DESCRIPTION_TEXT = """
    This is a rather long description.
    The Hitchhiker's Guide to the Galaxy is a comedy science fiction franchise created by Douglas Adams.
    Originally a radio sitcom broadcast over two series on BBC Radio 4 between 1978 and 1980,
    it was soon adapted to other formats, including both novels and comic books, a 1981 BBC television series,
    a 1984 text adventure game, stage shows and a 2005 feature film.
    """;

  @Mock
  private RepositoryManager repositoryManager;
  @Mock
  private RepositoryServiceFactory repositoryServiceFactory;

  @InjectMocks
  private ToolListRepositories tool;

  @BeforeEach
  void mockRepositories() {
    REPOSITORY_1.setDescription(LONG_DESCRIPTION_TEXT);
    REPOSITORY_2.setDescription("Short description");
    when(repositoryManager.getAll())
      .thenReturn(
        List.of(
          REPOSITORY_1,
          REPOSITORY_2
        ));
    RepositoryService repositoryService1 = mock(RepositoryService.class);
    when(repositoryServiceFactory.create(REPOSITORY_1))
      .thenReturn(repositoryService1);
    RepositoryService repositoryService2 = mock(RepositoryService.class);
    when(repositoryServiceFactory.create(REPOSITORY_2))
      .thenReturn(repositoryService2);
    when(repositoryService1.getSupportedProtocols())
      .thenReturn(Stream.of(
        new MockedHttpScmProtocol(ToolListRepositoriesTest.REPOSITORY_1)
      ));
    when(repositoryService2.getSupportedProtocols())
      .thenReturn(Stream.of(
        new MockedHttpScmProtocol(ToolListRepositoriesTest.REPOSITORY_2)
      ));
  }

  @Test
  void shouldListRepositories() {
    ToolResult result = tool.execute(new ListRepositoriesInput());

    assertThat(result.getContent().get(0))
      .isEqualTo("""
        STATUS: [SUCCESS] Found 2 repositories.
        INFO: You can find the first 100 bytes of the descriptions for each repository in the structured data block under their respective names.
        ---------------------------------------------------------
        * [hitchhiker/HeartOfGold](http://scm.hog/scm/repo/hitchhiker/HeartOfGold) (git)
        * [hitchhiker/42Puzzle](http://scm.hog/scm/repo/hitchhiker/42Puzzle) (git)
        """);
  }

  @Test
  void shouldAddShortenedDescriptions() {
    ToolResult result = tool.execute(new ListRepositoriesInput());

    assertThat(result.getStructuredContent().get("hitchhiker/HeartOfGold"))
      .asString()
      .startsWith("This is a rather long description.")
      .hasSize(100);
    assertThat(result.getStructuredContent())
      .containsEntry("hitchhiker/42Puzzle", "Short description");
  }

  @Test
  void shouldIncludeDetails() {
    ListRepositoriesInput input = new ListRepositoriesInput();
    input.setIncludeDetails(true);
    ToolResult result = tool.execute(input);

    assertThat(result.getStructuredContent().get("hitchhiker/HeartOfGold"))
      .extracting("contact")
      .isEqualTo("zaphod.beeblebrox@hitchhiker.com");
    assertThat(result.getStructuredContent().get("hitchhiker/HeartOfGold"))
      .extracting("description")
      .isEqualTo(LONG_DESCRIPTION_TEXT);
  }

  private static class MockedHttpScmProtocol extends HttpScmProtocol {
    public MockedHttpScmProtocol(Repository repository) {
      super(repository, "http://scm.hog/scm");
    }

    @Override
    protected void serve(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Repository repository, ServletConfig servletConfig) {
    }
  }
}
