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

import org.apache.commons.lang.StringUtils;
import org.github.sdorra.jse.ShiroExtension;
import org.github.sdorra.jse.SubjectAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.repository.api.Command;
import sonia.scm.repository.api.DiffFile;
import sonia.scm.repository.api.DiffLine;
import sonia.scm.repository.api.DiffResult;
import sonia.scm.repository.api.DiffResultCommandBuilder;
import sonia.scm.repository.api.Hunk;
import sonia.scm.repository.api.IgnoreWhitespaceLevel;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
class ToolDiffTest {

  private static final Repository REPOSITORY = RepositoryTestData.createHeartOfGold();

  @Mock
  private RepositoryServiceFactory repositoryServiceFactory;
  @Mock
  private RepositoryService repositoryService;
  @Mock(answer = Answers.RETURNS_SELF)
  private DiffResultCommandBuilder commandBuilder;

  private ToolDiff tool;

  @BeforeEach
  void setUp() {
    tool = new ToolDiff(repositoryServiceFactory,
      Set.of(new SimpleRevisionToolDiffExtension(), new BranchComparisonToolDiffExtension())
    );
    when(repositoryServiceFactory.create(REPOSITORY.getNamespaceAndName())).thenReturn(repositoryService);
  }

  @Test
  void shouldCheckForAuthorization() {
    initializeMockedRepository();

    ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
      REPOSITORY.getName(),
      "42",
      1000,
      1000,
      emptyList(),
      false
    );

    ToolResult result = tool.execute(input);

    assertThat(result.getMessage()).startsWith("User is not authorized to use this resource.");
  }

  @Nested
  @SubjectAware(value = "trillian", permissions = "*")
  class WithPermission {

    @Test
    void shouldNotRunWithWrongRepositoryTypex() {
      initializeMockedRepository();
      when(repositoryService.isSupported(Command.DIFF_RESULT)).thenReturn(false);

      ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
        REPOSITORY.getName(),
        "42",
        1000,
        1000,
        emptyList(),
        false
      );

      ToolResult result = tool.execute(input);

      assertThat(result.getMessage()).startsWith("This is only available for git repositories.");
    }

    @Nested
    class WithRepository {
      @BeforeEach
      void mockRepository() {
        initializeMockedRepository();
      }

      @Test
      void shouldCreateDiffForRevision() throws IOException {
        when(commandBuilder.getDiffResult()).thenReturn(createDiff(createDiffFile()));

        ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
          REPOSITORY.getName(),
          "42",
          1000,
          1000,
          emptyList(),
          false
        );

        ToolResult result = tool.execute(input);

        verify(commandBuilder).setRevision("42");
        verify(commandBuilder).setIgnoreWhitespace(IgnoreWhitespaceLevel.NONE);

        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [SUCCESS] The diff has been created successfully.
          ---------------------------------------------------------
          diff --git a/README.md b/README.md
          --- a/README.md
          +++ b/README.md
          @@ -1,3 +1,4 @@
          [    1 |    - ] -# HOG
          [    - |    1 ] +# Heart Of Gold
          [    2 |    2 ] \s
          [    3 |    - ] -The Heart of Gold is a imaginary space ship.
          [    - |    3 ] +The *Heart of Gold* is a spaceship from the Hitchhiker's Guide to the Galaxy.
          [    - |    4 ] +It's most important feature is the Improbability Drive.
          """);
      }

      @Test
      void shouldCreateLimitedDiff() throws IOException {
        when(commandBuilder.getDiffResult()).thenReturn(createDiff(createDiffFile()));

        ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
          REPOSITORY.getName(),
          "42",
          3,
          1000,
          emptyList(),
          false
        );

        ToolResult result = tool.execute(input);

        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [TRUNCATED] The diff has been created successfully. The diff has been too large. The output had been aborted. You will find the diff headers for all files without complete diff, nonetheless. You will also find the number of diff lines that have been omitted for each file.
          ---------------------------------------------------------
          diff --git a/README.md b/README.md
          --- a/README.md
          +++ b/README.md
          @@ -1,3 +1,4 @@
          [    1 |    - ] -# HOG
          [    - |    1 ] +# Heart Of Gold
          [    2 |    2 ] \s

          === Omitted 3 lines of this diff. ===

          """);
      }

      @Test
      void shouldLimitFiles() throws IOException {
        when(commandBuilder.getDiffResult()).thenReturn(createDiff(createDiffFile(), createDiffFile()));

        ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
          REPOSITORY.getName(),
          "42",
          1000,
          1,
          emptyList(),
          false
        );

        ToolResult result = tool.execute(input);

        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [TRUNCATED] The diff has been created successfully. There have been too many files. 1 files have been omitted completely. You can try to limit the amount of files by using a path filter.
          ---------------------------------------------------------
          diff --git a/README.md b/README.md
          --- a/README.md
          +++ b/README.md
          @@ -1,3 +1,4 @@
          [    1 |    - ] -# HOG
          [    - |    1 ] +# Heart Of Gold
          [    2 |    2 ] \s
          [    3 |    - ] -The Heart of Gold is a imaginary space ship.
          [    - |    3 ] +The *Heart of Gold* is a spaceship from the Hitchhiker's Guide to the Galaxy.
          [    - |    4 ] +It's most important feature is the Improbability Drive.
          """);
      }

      @Test
      void shouldCreateDiffOverview() throws IOException {
        when(commandBuilder.getDiffResult()).thenReturn(createDiff(createDiffFile()));

        ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
          REPOSITORY.getName(),
          "42",
          0,
          1000,
          emptyList(),
          false
        );

        ToolResult result = tool.execute(input);

        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [SUCCESS] The diff has been created successfully. The diffs have been omitted like requested.
          ---------------------------------------------------------
          diff --git a/README.md b/README.md

          === Omitted 6 lines of this diff. ===

          """);
      }

      @Test
      void shouldFilterForFiles() throws IOException {
        when(commandBuilder.getDiffResult()).thenReturn(createDiff(createDiffFile()));

        ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
          REPOSITORY.getName(),
          "42",
          1000,
          1000,
          List.of("src/*"),
          false
        );

        ToolResult result = tool.execute(input);

        assertThat(result.getContent().get(0)).isEqualTo("""
          STATUS: [SUCCESS] The diff has been created successfully. 1 diff entries have been omitted due to the `limitToPaths` filter.
          """);
      }

      @Test
      void shouldCreateDiffBetweenRevisions() throws IOException {
        when(commandBuilder.getDiffResult()).thenReturn(createDiff());

        ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
          REPOSITORY.getName(),
          "42",
          1000,
          1000,
          emptyList(),
          false
        );

        tool.execute(input);

        verify(commandBuilder).setRevision("42");
      }

      @Test
      void shouldIgnoreWhitespaces() throws IOException {
        when(commandBuilder.getDiffResult()).thenReturn(createDiff());

        ToolDiffInput input = new ToolDiffInput(REPOSITORY.getNamespace(),
          REPOSITORY.getName(),
          "42",
          1000,
          1000,
          emptyList(),
          true
        );

        tool.execute(input);

        verify(commandBuilder).setIgnoreWhitespace(IgnoreWhitespaceLevel.ALL);
      }
    }
  }

  private void initializeMockedRepository() {
    lenient().when(repositoryService.isSupported(Command.DIFF_RESULT)).thenReturn(true);
    lenient().when(repositoryService.getDiffResultCommand()).thenReturn(commandBuilder);
    lenient().when(repositoryService.getRepository()).thenReturn(REPOSITORY);
  }

  private DiffResult createDiff(DiffFile... diffFiles) {
    return new DiffResult() {
      @Override
      public String getOldRevision() {
        return "";
      }

      @Override
      public String getNewRevision() {
        return "";
      }

      @Override
      public Iterator<DiffFile> iterator() {
        return List.of(diffFiles).iterator();
      }
    };
  }

  private DiffFile createDiffFile() {
    return new DiffFile() {
      @Override
      public String getOldRevision() {
        return "";
      }

      @Override
      public String getNewRevision() {
        return "";
      }

      @Override
      public String getOldPath() {
        return "README.md";
      }

      @Override
      public String getNewPath() {
        return "README.md";
      }

      @Override
      public ChangeType getChangeType() {
        return ChangeType.MODIFY;
      }

      @Override
      public Iterator<Hunk> iterator() {
        return List.of((Hunk) new Hunk() {
          @Override
          public int getOldStart() {
            return 1;
          }

          @Override
          public int getOldLineCount() {
            return 3;
          }

          @Override
          public int getNewStart() {
            return 1;
          }

          @Override
          public int getNewLineCount() {
            return 4;
          }

          @Override
          public Iterator<DiffLine> iterator() {
            return createDiff("""
              # HOG
                                    
              The Heart of Gold is a imaginary space ship.""", """
              # Heart Of Gold
                                    
              The *Heart of Gold* is a spaceship from the Hitchhiker's Guide to the Galaxy.
              It's most important feature is the Improbability Drive.""");
          }
        }).iterator();
      }
    };
  }

  private Iterator<DiffLine> createDiff(String oldContent, String newContent) {
    String[] oldLines = oldContent.split("\n");
    String[] newLines = newContent.split("\n");
    Collection<DiffLine> diff = new ArrayList<>();

    for (int i = 0; i < Math.max(oldLines.length, newLines.length); i++) {
      String oldLine = i < oldLines.length ? oldLines[i] : null;
      String newLine = i < newLines.length ? newLines[i] : null;
      int line = i + 1;

      if (StringUtils.equals(oldLine, newLine)) {
        diff.add(new DiffLine() {
          @Override
          public String getContent() {
            return oldLine;
          }

          @Override
          public OptionalInt getOldLineNumber() {
            return OptionalInt.of(line);
          }

          @Override
          public OptionalInt getNewLineNumber() {
            return OptionalInt.of(line);
          }
        });
      } else if (oldLine != null && newLine != null) {
        diff.add(new DiffLine() {
          @Override
          public String getContent() {
            return oldLine;
          }

          @Override
          public OptionalInt getOldLineNumber() {
            return OptionalInt.of(line);
          }

          @Override
          public OptionalInt getNewLineNumber() {
            return OptionalInt.empty();
          }
        });
        diff.add(new DiffLine() {
          @Override
          public String getContent() {
            return newLine;
          }

          @Override
          public OptionalInt getOldLineNumber() {
            return OptionalInt.empty();
          }

          @Override
          public OptionalInt getNewLineNumber() {
            return OptionalInt.of(line);
          }
        });
      } else if (oldLine == null) {
        diff.add(new DiffLine() {
          @Override
          public String getContent() {
            return newLine;
          }

          @Override
          public OptionalInt getOldLineNumber() {
            return OptionalInt.empty();
          }

          @Override
          public OptionalInt getNewLineNumber() {
            return OptionalInt.of(line);
          }
        });
      } else {
        diff.add(new DiffLine() {
          @Override
          public String getContent() {
            return oldLine;
          }

          @Override
          public OptionalInt getOldLineNumber() {
            return OptionalInt.of(line);
          }

          @Override
          public OptionalInt getNewLineNumber() {
            return OptionalInt.empty();
          }
        });
      }
    }
    return diff.iterator();
  }
}
