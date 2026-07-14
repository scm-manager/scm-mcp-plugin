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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.NotFoundException;
import sonia.scm.repository.Branch;
import sonia.scm.repository.Branches;
import sonia.scm.repository.InternalRepositoryException;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.api.Command;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.repository.api.ScmProtocol;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolCreateFrontendLinkTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RepositoryServiceFactory repositoryServiceFactory;

  private ToolCreateFrontendLink tool;

  @BeforeEach
  void createTool() {
    tool = new ToolCreateFrontendLink(Set.of(
      new RepositoryFrontendLinkResolver(repositoryServiceFactory),
      new FileFrontendLinkResolver(repositoryServiceFactory),
      new DirectoryFrontendLinkResolver(repositoryServiceFactory),
      new CommitFrontendLinkResolver(repositoryServiceFactory),
      new BranchFrontendLinkResolver(repositoryServiceFactory),
      new TagFrontendLinkResolver(repositoryServiceFactory)
    ));
  }

  @BeforeEach
  void mockHttpUrlForRepository() {
    NamespaceAndName namespaceAndName = new NamespaceAndName("hitchhiker", "hog");
    when(repositoryServiceFactory.create(namespaceAndName).getSupportedProtocols())
      .thenReturn(Stream.of(new ScmProtocol(){
        @Override
        public String getType() {
          return "http";
        }

        @Override
        public String getUrl() {
          return "https://scm.hitchhiker.com/repo/hitchhiker/hog";
        }
      }));
  }

  @Test
  void shouldGenerateStableInputSchema() {
    String schema = tool.getInputSchema();

    assertThat(schema)
      .contains("\"targetType\"")
      .contains("\"namespace\"")
      .contains("\"name\"")
      .contains("\"revision\"")
      .contains("\"path\"")
      .contains("\"id\"")
      .contains("\"line\"")
      .contains("\"required\" : [ \"targetType\" ]");
  }

  @Test
  void shouldDescribeRegisteredResolvers() {
    assertThat(tool.getDescription())
      .contains("- repository: Link to the repository page, which by default is the page with the root of the sources. Required parameters: name, namespace.")
      .contains("- file: Link to a file in a repository. Required parameters: name, namespace, path. Optional parameters: line.");
  }

  @Test
  void shouldCreateRepositoryLink() {
    ToolResult result = tool.execute(repositoryInput());

    assertThat(result.isError()).isFalse();
    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] Created frontend link.
        ---------------------------------------------------------
        * [hitchhiker/hog](https://scm.hitchhiker.com/repo/hitchhiker/hog)
        """);
    assertLink(result, "repository", "https://scm.hitchhiker.com/repo/hitchhiker/hog");
  }

  @Test
  void shouldCreateFileLinkWithLine() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");
    input.setRevision("main");
    input.setPath("/src/HeartOfGold.java");
    input.setLine(42);

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] Created frontend link.
        ---------------------------------------------------------
        * [hitchhiker/hog@main:src/HeartOfGold.java](https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/src/HeartOfGold.java#line-42)
        """);
    assertLink(result, "file", "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/src/HeartOfGold.java#line-42");
    assertThat(linkMetadata(result))
      .containsEntry("revision", "main")
      .containsEntry("path", "src/HeartOfGold.java")
      .containsEntry("line", 42);
    verify(repositoryServiceFactory.create(new NamespaceAndName("hitchhiker", "hog")), never())
      .isSupported(Command.BRANCHES);
  }

  @Test
  void shouldCreateFileLinkWithEncodedBranch() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");
    input.setRevision("feature/hog");
    input.setPath("/src/HeartOfGold.java");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] Created frontend link.
        ---------------------------------------------------------
        * [hitchhiker/hog@feature/hog:src/HeartOfGold.java](https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/feature%2Fhog/src/HeartOfGold.java)
        """);
    assertLink(result, "file", "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/feature%2Fhog/src/HeartOfGold.java");
    assertThat(linkMetadata(result))
      .containsEntry("revision", "feature/hog")
      .containsEntry("path", "src/HeartOfGold.java");
    verify(repositoryServiceFactory.create(new NamespaceAndName("hitchhiker", "hog")), never())
      .isSupported(Command.BRANCHES);
  }

  @ParameterizedTest
  @ValueSource(strings = {"file", "directory"})
  void shouldUseDefaultBranchWhenRevisionIsMissing(String targetType) throws IOException {
    NamespaceAndName namespaceAndName = new NamespaceAndName("hitchhiker", "hog");
    when(repositoryServiceFactory.create(namespaceAndName).isSupported(Command.BRANCHES)).thenReturn(true);
    when(repositoryServiceFactory.create(namespaceAndName).getBranchesCommand().getBranches())
      .thenReturn(new Branches(
        Branch.normalBranch("develop", "123abc", null, null),
        Branch.defaultBranch("main", "456def", null, null)
      ));
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType(targetType);
    input.setPath("/src/main/java");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] Created frontend link.
        ---------------------------------------------------------
        * [hitchhiker/hog@main:src/main/java](https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/src/main/java)
        """);
    assertLink(result, targetType, "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/src/main/java");
    assertThat(linkMetadata(result))
      .containsEntry("revision", "main")
      .containsEntry("path", "src/main/java");
  }

  @Test
  void shouldUseSvnRevisionWhenBranchesAreUnsupported() {
    NamespaceAndName namespaceAndName = new NamespaceAndName("hitchhiker", "hog");
    when(repositoryServiceFactory.create(namespaceAndName).isSupported(Command.BRANCHES)).thenReturn(false);
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("directory");
    input.setPath("trunk/src");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertLink(result, "directory", "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/-1/trunk/src");
    assertThat(result.getContent().get(0)).contains("[hitchhiker/hog@-1:trunk/src]");
    assertThat(linkMetadata(result))
      .containsEntry("revision", "-1")
      .containsEntry("path", "trunk/src");
  }

  @Test
  void shouldFailWhenRepositoryHasNoDefaultBranch() throws IOException {
    NamespaceAndName namespaceAndName = new NamespaceAndName("hitchhiker", "hog");
    when(repositoryServiceFactory.create(namespaceAndName).isSupported(Command.BRANCHES)).thenReturn(true);
    when(repositoryServiceFactory.create(namespaceAndName).getBranchesCommand().getBranches())
      .thenReturn(new Branches(Branch.normalBranch("develop", "123abc", null, null)));
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");
    input.setPath("README.md");
    Assertions.setMaxStackTraceElementsDisplayed(10000);

    assertThatThrownBy(() -> tool.execute(input))
      .isInstanceOf(NotFoundException.class)
      .hasMessage("could not find branch with id default in repository with id hitchhiker/hog");
  }

  @Test
  void shouldWrapFailureToLoadDefaultBranch() throws IOException {
    NamespaceAndName namespaceAndName = new NamespaceAndName("hitchhiker", "hog");
    when(repositoryServiceFactory.create(namespaceAndName).isSupported(Command.BRANCHES)).thenReturn(true);
    when(repositoryServiceFactory.create(namespaceAndName).getBranchesCommand().getBranches())
      .thenThrow(new IOException("could not read branches"));
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");
    input.setPath("README.md");

    assertThatThrownBy(() -> tool.execute(input))
      .isInstanceOf(InternalRepositoryException.class)
      .hasMessage("Error loading branches for hitchhiker/hog");
  }

  @Test
  void shouldCreateDirectoryLinkWithLine() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("directory");
    input.setRevision("main");
    input.setPath("/src/main/java");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] Created frontend link.
        ---------------------------------------------------------
        * [hitchhiker/hog@main:src/main/java](https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/src/main/java)
        """);
    assertLink(result, "directory", "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/src/main/java");
    assertThat(linkMetadata(result))
      .containsEntry("revision", "main")
      .containsEntry("path", "src/main/java");
  }

  @Test
  void shouldCreateCommitLink() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("commit");
    input.setRevision("123abc");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] Created frontend link.
        ---------------------------------------------------------
        * [hitchhiker/hog commit 123abc](https://scm.hitchhiker.com/repo/hitchhiker/hog/code/changeset/123abc)
        """);
    assertLink(result, "commit", "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/changeset/123abc");
    assertThat(linkMetadata(result))
      .containsEntry("commit", "123abc")
      .containsEntry("namespace", "hitchhiker")
      .containsEntry("name", "hog");
  }

  @Test
  void shouldCreateEncodedBranchLink() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("branch");
    input.setRevision("feature/hog");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertThat(result.getContent())
      .containsExactly("""
        STATUS: [SUCCESS] Created frontend link.
        ---------------------------------------------------------
        * [hitchhiker/hog branch feature/hog](https://scm.hitchhiker.com/repo/hitchhiker/hog/branch/feature%2Fhog)
        """);
    assertLink(result, "branch", "https://scm.hitchhiker.com/repo/hitchhiker/hog/branch/feature%2Fhog");
    assertThat(linkMetadata(result))
      .containsEntry("branch", "feature/hog")
      .containsEntry("namespace", "hitchhiker")
      .containsEntry("name", "hog");
  }

  @Test
  void shouldNormalizeCurrentDirectorySegments() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");
    input.setRevision("main");
    input.setPath("/././docs/./README.md");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertLink(result, "file", "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/docs/README.md");
    assertThat(linkMetadata(result)).containsEntry("path", "docs/README.md");
    assertThat(result.getContent().get(0)).contains("[hitchhiker/hog@main:docs/README.md]");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "../README.md",
    "docs/../README.md",
    "..\\README.md",
    "docs\\..\\README.md",
    "%2e%2e/README.md",
    "%2E./README.md",
    ".%2e%2fREADME.md",
    "%2e%2e%5cREADME.md",
    "%252e%252e/README.md"
  })
  void shouldRejectPathTraversal(String path) {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");
    input.setRevision("main");
    input.setPath(path);

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isTrue();
    assertThat(result.getMessage()).isEqualTo("Path must not contain parent directory segments ('..').");
  }

  @Test
  void shouldRejectPathTraversalForDirectoryLinks() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("directory");
    input.setRevision("main");
    input.setPath("docs/../secrets");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isTrue();
    assertThat(result.getMessage()).isEqualTo("Path must not contain parent directory segments ('..').");
  }

  @ParameterizedTest
  @ValueSource(strings = {".keep", "...", "README..md"})
  void shouldAllowDotsInsidePathSegment(String path) {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");
    input.setRevision("main");
    input.setPath(path);

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    assertLink(result, "file", "https://scm.hitchhiker.com/repo/hitchhiker/hog/code/sources/main/" + path);
    assertThat(linkMetadata(result)).containsEntry("path", path);
  }

  @Test
  void shouldRejectUnknownTargetType() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("pullRequest");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isTrue();
    assertThat(result.getMessage())
      .matches("Unknown targetType 'pullRequest'\\. Supported targetType values: .+");
  }

  @Test
  void shouldRejectMissingParameters() {
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("file");

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isTrue();
    assertThat(result.getMessage())
      .isEqualTo("Missing required parameter(s) for targetType 'file': path.");
  }

  @Test
  void shouldUsePluginProvidedResolverWithId() {
    ToolCreateFrontendLink toolWithPullRequestResolver = new ToolCreateFrontendLink(Set.of(new PullRequestFrontendLinkResolver()));
    CreateFrontendLinkInput input = repositoryInput();
    input.setTargetType("pullRequest");
    input.setId("42");

    ToolResult result = toolWithPullRequestResolver.execute(input);

    assertThat(toolWithPullRequestResolver.getDescription())
      .contains("- pullRequest: Link to a pull request. Required parameters: id, name, namespace.");
    assertThat(result.isError()).isFalse();
    assertLink(result, "pullRequest", "https://scm.hitchhiker.com/repo/hitchhiker/hog/pull-request/42");
    assertThat(linkMetadata(result))
      .containsEntry("id", "42");
  }

  private CreateFrontendLinkInput repositoryInput() {
    CreateFrontendLinkInput input = new CreateFrontendLinkInput();
    input.setTargetType("repository");
    input.setNamespace("hitchhiker");
    input.setName("hog");
    return input;
  }

  @SuppressWarnings("unchecked")
  private void assertLink(ToolResult result, String targetType, String url) {
    Map<String, Object> link = (Map<String, Object>) result.getStructuredContent().get("link");
    assertThat(link)
      .containsEntry("targetType", targetType)
      .containsEntry("url", url)
      .containsEntry("markdown", link.get("markdown"));
    assertThat(link.get("markdown").toString()).contains(url);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> linkMetadata(ToolResult result) {
    Map<String, Object> link = (Map<String, Object>) result.getStructuredContent().get("link");
    return (Map<String, Object>) link.get("metadata");
  }

  private static class PullRequestFrontendLinkResolver implements FrontendLinkResolver {

    @Override
    public String getTargetType() {
      return "pullRequest";
    }

    @Override
    public String getDescription() {
      return "Link to a pull request.";
    }

    @Override
    public Set<FrontendLinkParameter> getRequiredParameters() {
      return Set.of(FrontendLinkParameter.NAMESPACE, FrontendLinkParameter.NAME, FrontendLinkParameter.ID);
    }

    @Override
    public FrontendLinkResult createLink(CreateFrontendLinkInput input) {
      return FrontendLinkResult.of(
        getTargetType(),
        input.getNamespace() + "/" + input.getName() + "#" + input.getId(),
        "https://scm.hitchhiker.com/repo/%s/%s/pull-request/%s".formatted(input.getNamespace(), input.getName(), input.getId()),
        Map.of(
          "namespace", input.getNamespace(),
          "name", input.getName(),
          "id", input.getId()
        )
      );
    }

    @Override
    public Optional<String> validate(CreateFrontendLinkInput input) {
      return FrontendLinkResolver.super.validate(input);
    }
  }
}
