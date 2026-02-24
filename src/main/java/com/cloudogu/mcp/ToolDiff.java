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

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.RepositoryPermissions;
import sonia.scm.repository.api.Command;
import sonia.scm.repository.api.DiffFile;
import sonia.scm.repository.api.DiffLine;
import sonia.scm.repository.api.DiffResult;
import sonia.scm.repository.api.DiffResultCommandBuilder;
import sonia.scm.repository.api.Hunk;
import sonia.scm.repository.api.IgnoreWhitespaceLevel;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.util.GlobUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

@Slf4j
@Extension
class ToolDiff implements TypedTool<ToolDiffInput> {

  public static final String DEV_NULL = "/dev/null";

  private final RepositoryServiceFactory repositoryServiceFactory;
  private final Set<ToolDiffExtensionPoint> extensions;

  @Inject
  public ToolDiff(RepositoryServiceFactory repositoryServiceFactory, Set<ToolDiffExtensionPoint> extensions) {
    this.repositoryServiceFactory = repositoryServiceFactory;
    this.extensions = extensions;
  }

  @Override
  public Class<? extends ToolDiffInput> getInputClass() {
    return ToolDiffInput.class;
  }

  @Override
  public ToolResult execute(ToolDiffInput input) {
    log.trace("executing request {}", input);
    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.read(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }
      if (!repositoryService.isSupported(Command.DIFF_RESULT)) {
        log.trace("diff not available for non-git repository");
        return ToolResult.error("This is only available for git repositories.");
      }
      return readDiff(input, repositoryService);
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading or writing the files"
      );
    }
  }

  private ToolResult readDiff(ToolDiffInput input, RepositoryService repositoryService) throws IOException {
    OkResultRenderer.PostponedResultRenderer resultRenderer = OkResultRenderer.postponedStatus();

    Optional<ToolDiffExtensionPoint> handler = findHandler(input);
    if (handler.isEmpty()) {
      return ToolResult.error("Your target expression could not be evaluated. Please check your expression.");
    }

    DiffMetaResult result = computeDiff(input, repositoryService, resultRenderer, handler.get());

    Collection<String> status = new ArrayList<>();

    status.add("The diff has been created successfully.");

    boolean truncated = false;

    if (result.aborted && input.getDiffLineLimit() > 0) {
      status.add(
        "The diff has been too large. The output had been aborted. You will find the diff headers for all files " +
          "without complete diff, nonetheless. You will also find the number of diff lines that have been omitted " +
          "for each file.");
      truncated = true;
    }
    if (input.getDiffLineLimit() == 0) {
      status.add("The diffs have been omitted like requested.");
    }
    if (result.fileCount > input.getDiffFileLimit()) {
      status.add(String.format(
        "There have been too many files. %s files have been omitted completely. You can try to limit the amount of files by using a path filter.",
        result.fileCount - input.getDiffFileLimit()
      ));
      truncated = true;
    }
    if (result.omittedFileCount > 0) {
      status.add(
        result.omittedFileCount + " diff entries have been omitted due to the `limitToPaths` filter.");
    }

    return resultRenderer.withStatus(
      truncated ? "TRUNCATED" : "SUCCESS",
      String.join(" ", status)
    ).render();
  }

  private DiffMetaResult computeDiff(
    ToolDiffInput input,
    RepositoryService repositoryService,
    OkResultRenderer.PostponedResultRenderer resultRenderer,
    ToolDiffExtensionPoint toolDiffExtensionPoint
  ) throws IOException {
    DiffResultCommandBuilder diffResultCommandBuilder = repositoryService.getDiffResultCommand()
      .setIgnoreWhitespace(input.isIgnoreWhitespace() ? IgnoreWhitespaceLevel.ALL : IgnoreWhitespaceLevel.NONE);
    toolDiffExtensionPoint.handleExpression(input, diffResultCommandBuilder);

    int diffLineCount = 0;
    int fileCount = 0;
    int omittedFileCount = 0;

    DiffResult diffResult = diffResultCommandBuilder.getDiffResult();
    for (DiffFile diffFile : diffResult) {
      String oldPath = diffFile.getOldPath();
      String newPath = diffFile.getNewPath();

      if (diffShouldBeOmitted(input, newPath, oldPath)) {
        ++omittedFileCount;
        log.trace("omitted file {} due to filter", diffFile.getOldPath());
        continue;
      }

      ++fileCount;
      if (fileCount > input.getDiffFileLimit()) {
        continue; // from now on, we are only interested in the overall file count
      }

      resultRenderer.append("diff --git a/").append(DEV_NULL.equals(oldPath) ? newPath : oldPath).append(" b/").appendLine(DEV_NULL.equals(newPath) ? oldPath : newPath);

      if (DEV_NULL.equals(oldPath)) {
        resultRenderer.appendLine("new file mode 100644");
      }
      if (DEV_NULL.equals(newPath)) {
        resultRenderer.appendLine("deleted file mode 100644");
      }

      Iterator<Hunk> hunkIterator = diffFile.iterator();

      if (diffLineCount < input.getDiffLineLimit()) {
        if (hunkIterator.hasNext()) {
          resultRenderer.append("--- ").appendLine(prefixedPath("a", oldPath));
          resultRenderer.append("+++ ").appendLine(prefixedPath("b", newPath));
        } else {
          resultRenderer.appendLine("similarity index 100%");
        }
      }

      if (!oldPath.equals(newPath) && !DEV_NULL.equals(oldPath) && !DEV_NULL.equals(newPath)) {
        resultRenderer.append("rename from ").appendLine(oldPath);
        resultRenderer.append("rename to ").appendLine(newPath);
      }

      boolean aborted = diffLineCount >= input.getDiffLineLimit();
      int omittedCount = 0;
      final int PADDING_WIDTH = 4;
      final String FORMAT_TEMPLATE = "[ %" + PADDING_WIDTH + "s | %" + PADDING_WIDTH + "s ] ";
      while (hunkIterator.hasNext()) {
        Hunk hunk = hunkIterator.next();
        if (!aborted) {
          resultRenderer.appendLine(hunk.getRawHeader());
        }
        for (DiffLine diffLine : hunk) {
          ++diffLineCount;
          if (diffLineCount > input.getDiffLineLimit()) {
            aborted = true;
            ++omittedCount;
          } else {
            resultRenderer.append(
              String.format(
                FORMAT_TEMPLATE,
                lineNumberOrDash(diffLine.getOldLineNumber()),
                lineNumberOrDash(diffLine.getNewLineNumber())
              )
            );

            if (diffLine.getNewLineNumber().isEmpty()) {
              resultRenderer.append("-");
            } else if (diffLine.getOldLineNumber().isEmpty()) {
              resultRenderer.append("+");
            } else {
              resultRenderer.append(" ");
            }
            resultRenderer.appendLine(diffLine.getContent());
          }
        }
      }
      if (aborted) {
        log.trace("omitted {} lines of diff", omittedCount);
        resultRenderer.appendLine("");
        resultRenderer.append("=== Omitted ").append(omittedCount).appendLine(" lines of this diff. ===");
        resultRenderer.appendLine("");
      }
    }
    return new DiffMetaResult(diffLineCount > input.getDiffLineLimit(), omittedFileCount, fileCount);
  }

  private String lineNumberOrDash(OptionalInt lineNumber) {
    if (lineNumber.isPresent()) {
      return Integer.toString(lineNumber.getAsInt());
    }
    return "-";
  }

  private Optional<ToolDiffExtensionPoint> findHandler(ToolDiffInput input) {
    return extensions.stream().filter(ex -> ex.canHandleExpression(input)).findFirst();
  }

  private boolean diffShouldBeOmitted(ToolDiffInput input, String newPath, String oldPath) {
    return !input.getPathFilter().isEmpty() &&
      input.getPathFilter().stream().noneMatch(filter -> GlobUtil.matches(filter, newPath)) &&
      input.getPathFilter().stream().noneMatch(filter -> GlobUtil.matches(filter, oldPath));
  }

  private String prefixedPath(String prefix, String path) {
    if (DEV_NULL.equals(path)) {
      return path;
    } else {
      return prefix + "/" + path;
    }
  }

  @Override
  public String getName() {
    return "compute-diff";
  }

  @Override
  public String getDescription() {
    return """
      Generates an enhanced git-like diff for a specified Git repository,
      featuring explicit side-by-side line numbers (formatted as `[ old | new ]`)
      to help you accurately pinpoint line numbers for code comments.
      
      Do not use this tool for non-Git repositories.
      
      You must use the `diffTarget` parameter to specify what to compare.
      The following formats are currently supported:
      
      """ +
        extensions.stream()
          .sorted(comparing(ToolDiffExtensionPoint::getPriority))
          .map(ToolDiffExtensionPoint::usageDescription)
          .map(d -> "- " + d)
          .collect(joining("\n"));
  }

  private record DiffMetaResult(boolean aborted, int omittedFileCount, int fileCount) {
  }
}
