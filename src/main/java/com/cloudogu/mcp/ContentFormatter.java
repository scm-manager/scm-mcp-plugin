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

import java.util.Collection;

public class ContentFormatter {

  private final String file;

  public ContentFormatter(String file) {
    this.file = file;
  }

  public OkResultRenderer writeComplete(Collection<String> lines, String info) {
    if (lines.isEmpty()) {
      OkResultRenderer resultRenderer = writeEmpty();
      return resultRenderer.withInfoText(info);
    }
    return write(Status.COMPLETE, lines, 1, info);
  }

  public OkResultRenderer writeEmpty() {
    return OkResultRenderer.ok("EMPTY", String.format("The file `%s` is empty.", file));
  }

  public OkResultRenderer write(Status status, Collection<String> lines, int firstLineNumber) {
    return write(status, lines, firstLineNumber, null);
  }

  public OkResultRenderer write(Status status, Collection<String> lines, int firstLineNumber, String info) {
    OkResultRenderer resultRenderer;
    switch (status) {
      case TRUNCATED ->
        resultRenderer = OkResultRenderer.ok("TRUNCATED", String.format("Showing lines %s-%s of `%s`.", firstLineNumber, firstLineNumber + lines.size() - 1, file));
      case COMPLETE ->
        resultRenderer = OkResultRenderer.ok("COMPLETE", String.format("Showing all lines %s-%s of `%s`.", firstLineNumber, firstLineNumber + lines.size() - 1, file));
      case EMPTY -> resultRenderer = OkResultRenderer.ok("EMPTY", String.format("Range outside of file bounds of `%s`.", file));
      default -> throw new IllegalStateException("should not reach this with status " + status);
    }
    writeInfo(resultRenderer, info);
    write(resultRenderer, lines, firstLineNumber);
    return resultRenderer;
  }

  private void writeInfo(OkResultRenderer resultRenderer, String info) {
    if (info != null) {
      resultRenderer.withInfoText(info);
    }
  }

  private void write(OkResultRenderer resultRenderer, Collection<String> lines, int firstLineNumber) {
    if (lines == null || lines.isEmpty()) {
      return;
    }

    int maxWidth = getMaxWidthOfLineNumbers(lines, firstLineNumber);
    String lineNumberFormat = "%" + maxWidth + "d | %s%n";

    resultRenderer.append("```\n");
    int currentLine = firstLineNumber;
    for (String line : lines) {
      resultRenderer.append(String.format(lineNumberFormat, currentLine, line));
      currentLine++;
    }
    resultRenderer.append("```\n");
  }

  private static int getMaxWidthOfLineNumbers(Collection<String> lines, int firstLineNumber) {
    int lastLineNumber = firstLineNumber + lines.size() - 1;
    return String.valueOf(lastLineNumber).length();
  }

  public enum Status {
    EMPTY, COMPLETE, TRUNCATED
  }
}
