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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Length;
import sonia.scm.io.ContentType;
import sonia.scm.io.ContentTypeResolver;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.RepositoryPermissions;
import sonia.scm.repository.api.CatCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.util.IOUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Extension
class ToolReadFile implements TypedTool<ReadFilesInput> {

  private static final int HEAD_BUFFER_SIZE = 1024;

  private final RepositoryServiceFactory repositoryServiceFactory;
  private final ContentTypeResolver contentTypeResolver;

  @Inject
  public ToolReadFile(RepositoryServiceFactory repositoryServiceFactory, ContentTypeResolver contentTypeResolver) {
    this.repositoryServiceFactory = repositoryServiceFactory;
    this.contentTypeResolver = contentTypeResolver;
  }

  @Override
  public String getName() {
    return "read-file";
  }

  @Override
  public String getDescription() {
    return """
      Returns the content of the file.
      Note: The output includes line numbers in the format 'L | content' to help you reference specific lines.
      When editing or rewriting the file, do NOT include these numbers.""";
  }

  @Override
  public Class<ReadFilesInput> getInputClass() {
    return ReadFilesInput.class;
  }

  @Override
  public ToolResult execute(ReadFilesInput input) {
    String path = input.getPath();
    while (path.startsWith("/")) {
      path = path.substring(1);
    }

    log.trace("executing request {}", input);
    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.read(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }

      return tryReadFile(input, path, repositoryService);
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading the files"
      );
    }
  }

  private ToolResult tryReadFile(ReadFilesInput input, String path, RepositoryService repositoryService) throws IOException {
    return readFile(input, path, repositoryService);
  }

  private ToolResult readFile(ReadFilesInput input, String path, RepositoryService repositoryService) throws IOException {
    byte[] head = getHead(input.getRevision(), path, repositoryService);
    ContentType contentType = contentTypeResolver.resolve(path, head);

    OkResultRenderer resultRenderer;
    if (head.length == 0) {
      resultRenderer = handleEmptyFile(path);
    } else if (contentType.isText()) {
      resultRenderer = handleTextFile(path, input, repositoryService, contentType);
    } else {
      resultRenderer = handleBinaryFile(path, contentType);
    }

    log.trace("file read");
    return resultRenderer.render();
  }

  private OkResultRenderer handleBinaryFile(String file, ContentType contentType) {
    return OkResultRenderer.ok("BINARY FILE", String.format("The file `%s` has binary content and cannot be displayed as text.", file))
      .withInfoText(String.format("The detected content type of this file is `%s`.", contentType.getRaw()));
  }

  private OkResultRenderer handleEmptyFile(String file) {
    return new ContentFormatter(file).writeEmpty();
  }

  private OkResultRenderer handleTextFile(String path, ReadFilesInput input, RepositoryService repositoryService, ContentType contentType) throws IOException {
    String[] parts = input.getLineRange().split("-");
    int start = Integer.parseInt(parts[0].trim());
    int end = Integer.parseInt(parts[1].trim());
    CatCommandBuilder catCommandBuilder = repositoryService.getCatCommand().setRevision(input.getRevision());
    ReadResult readResult;
    try (InputStream inputStream = catCommandBuilder.getStream(path)) {
      readResult = readLines(inputStream, start, end);
    }
    ContentFormatter.Status status;
    if (readResult.lines.isEmpty()) {
      status = ContentFormatter.Status.EMPTY;
    } else if (readResult.moreAvailable) {
      status = ContentFormatter.Status.TRUNCATED;
    } else {
      status = ContentFormatter.Status.COMPLETE;
    }
    String info = "The content type for this file is " + contentType.getRaw() + '.';
    if (getLanguage(contentType).isPresent()) {
      info = info + " The detected language is " + getLanguage(contentType).get() + '.';
    }

    return new ContentFormatter(path).write(status, readResult.lines, start, info);
  }

  private ReadResult readLines(InputStream inputStream, int startLine, int endLine) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int currentLineNumber = 0;
      boolean moreAvailable = false;

      while ((line = reader.readLine()) != null) {
        currentLineNumber++;
        if (currentLineNumber >= startLine && currentLineNumber <= endLine) {
          lines.add(line);
        }

        if (currentLineNumber >= endLine) {
          moreAvailable = reader.ready();
          break;
        }
      }
      return new ReadResult(moreAvailable, lines);
    }
  }

  private Optional<String> getLanguage(ContentType contentType) {
    return contentType.getLanguage();
  }

  private byte[] getHead(String revision, String path, RepositoryService repositoryService) throws IOException {
    InputStream stream = repositoryService.getCatCommand().setRevision(revision).getStream(path);
    try {
      byte[] buffer = new byte[HEAD_BUFFER_SIZE];
      int length = stream.read(buffer);
      if (length < 0) { // empty file
        return new byte[]{};
      } else if (length < buffer.length) {
        return Arrays.copyOf(buffer, length);
      } else {
        return buffer;
      }
    } finally {
      IOUtil.close(stream);
    }
  }

  private record ReadResult(boolean moreAvailable, List<String> lines) {
  }
}

@Data
class ReadFilesInput {
  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAMESPACE_REGEX)
  @JsonPropertyDescription("The namespace of the repository to read files from.")
  private String namespace;

  @NotEmpty
  @Pattern(regexp = Validations.REPOSITORY_NAME_REGEX)
  @JsonPropertyDescription("The name of the repository to read files from.")
  private String name;

  @JsonPropertyDescription("The revision to read the files from. This can be either a 'real' revision, a branch, or a tag. If this is omitted, the default branch of the repository will be taken.")
  private String revision;

  @NotNull
  @JsonPropertyDescription("The file paths to read. The file path have to be absolute. It is of no relevance, whether it starts with a `/` or not.")
  private String path;

  @Length(min = 1, max = 20)
  @Pattern(regexp = "\\d{1,7}\\W*-\\W*\\d{1,7}")
  @JsonPropertyDescription("""
    The range of lines to read from the file, formatted as 'start-end' (e.g., '1-50').
    The range is 1-indexed and inclusive.
    Use this to read large files in chunks to avoid hitting context limits.""")
  private String lineRange = "1-100";
}
