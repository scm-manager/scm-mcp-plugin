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

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import org.apache.commons.lang.StringUtils;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.Branch;
import sonia.scm.repository.InternalRepositoryException;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.api.Command;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.util.HttpUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static sonia.scm.ContextEntry.ContextBuilder.entity;
import static sonia.scm.NotFoundException.notFound;

@Extension
class RepositoryFrontendLinkResolver implements FrontendLinkResolver {

  private final RepositoryServiceFactory repositoryServiceFactory;

  @Inject
  RepositoryFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public String getTargetType() {
    return "repository";
  }

  @Override
  public String getDescription() {
    return "Link to the repository page, which by default is the page with the root of the sources.";
  }

  @Override
  public Set<FrontendLinkParameter> getRequiredParameters() {
    return Set.of(FrontendLinkParameter.NAMESPACE, FrontendLinkParameter.NAME);
  }

  @Override
  public FrontendLinkResult createLink(CreateFrontendLinkInput input) {
    NamespaceAndName namespaceAndName = new NamespaceAndName(input.getNamespace(), input.getName());
    return FrontendLinkResult.of(
      getTargetType(),
      namespaceAndName.toString(),
      findRepositoryUrl(repositoryServiceFactory, namespaceAndName),
      repositoryMetadata(input)
    );
  }
}

abstract class SourceFrontendLinkResolverBase implements FrontendLinkResolver {

  private static final String LINE_FRAGMENT_PREFIX = "line-";

  private final RepositoryServiceFactory repositoryServiceFactory;

  SourceFrontendLinkResolverBase(RepositoryServiceFactory repositoryServiceFactory) {
    this.repositoryServiceFactory = repositoryServiceFactory;
  }

  @Override
  public Set<FrontendLinkParameter> getRequiredParameters() {
    return Set.of(
      FrontendLinkParameter.NAMESPACE,
      FrontendLinkParameter.NAME,
      FrontendLinkParameter.PATH
    );
  }

  @Override
  public Optional<String> validate(CreateFrontendLinkInput input) {
    Optional<String> validationError = FrontendLinkResolver.super.validate(input);
    if (validationError.isPresent()) {
      return validationError;
    }

    if (containsParentDirectorySegment(input.getPath())) {
      return Optional.of("Path must not contain parent directory segments ('..').");
    }

    return Optional.empty();
  }

  @Override
  public FrontendLinkResult createLink(CreateFrontendLinkInput input) {
    String path = normalizePath(input.getPath());
    NamespaceAndName namespaceAndName = new NamespaceAndName(input.getNamespace(), input.getName());
    String effectiveRevision = computeEffectiveRevision(namespaceAndName, input);
    String url = findRepositoryUrl(repositoryServiceFactory, namespaceAndName) + "/code/sources/" + HttpUtil.encode(effectiveRevision) + '/' + path;
    if (supportsLine() && input.getLine() != null) {
      url = appendLineFragment(url, input.getLine());
    }

    return FrontendLinkResult.of(
      getTargetType(),
      createLabel(input, path, effectiveRevision),
      url,
      sourceMetadata(input, path, effectiveRevision)
    );
  }

  private String computeEffectiveRevision(NamespaceAndName namespaceAndName, CreateFrontendLinkInput input) {
    if (input.getRevision() == null) {
      try (RepositoryService repositoryService = repositoryServiceFactory.create(namespaceAndName)) {
        if (repositoryService.isSupported(Command.BRANCHES)) {
          return repositoryService
            .getBranchesCommand()
            .getBranches()
            .getBranches()
            .stream()
            .filter(Branch::isDefaultBranch)
            .findAny()
            .map(Branch::getName)
            .orElseThrow(() -> notFound(entity(Branch.class, "default").in(namespaceAndName)));
        } else {
          return "-1"; // Fallback for SVN
        }
      } catch (IOException e) {
        throw new InternalRepositoryException(entity(namespaceAndName), "Error loading branches for " + namespaceAndName);
      }
    }
    return input.getRevision();
  }

  private String createLabel(CreateFrontendLinkInput input, String path, String effectiveRevision) {
    String label = input.getNamespace() + "/" + input.getName();
    if (!Strings.isNullOrEmpty(effectiveRevision)) {
      label += "@" + effectiveRevision;
    }
    if (!Strings.isNullOrEmpty(path)) {
      label += ":" + path;
    }
    return label;
  }

  private Map<String, Object> sourceMetadata(CreateFrontendLinkInput input, String path, String effectiveRevision) {
    Map<String, Object> metadata = repositoryMetadata(input);
    metadata.put("revision", effectiveRevision);
    metadata.put("path", path);
    if (supportsLine() && input.getLine() != null) {
      metadata.put("line", input.getLine());
    }
    return metadata;
  }

  abstract boolean supportsLine();

  private String normalizePath(String path) {
    String effectivePath = Strings.nullToEmpty(path);
    while (effectivePath.startsWith("/")) {
      effectivePath = effectivePath.substring(1);
    }
    return Arrays.stream(effectivePath.split("/", -1))
      .filter(segment -> !segment.equals("."))
      .collect(Collectors.joining("/"));
  }

  private static boolean containsParentDirectorySegment(String path) {
    String decodedPath = decodeSecurityRelevantCharacters(Strings.nullToEmpty(path));
    int segmentStart = 0;
    for (int i = 0; i <= decodedPath.length(); i++) {
      if (i == decodedPath.length() || decodedPath.charAt(i) == '/' || decodedPath.charAt(i) == '\\') {
        if (i - segmentStart == 2 && decodedPath.regionMatches(segmentStart, "..", 0, 2)) {
          return true;
        }
        segmentStart = i + 1;
      }
    }
    return false;
  }

  private static String decodeSecurityRelevantCharacters(String path) {
    String decodedPath = path;
    String previousPath;
    do {
      previousPath = decodedPath;
      StringBuilder decoded = new StringBuilder(previousPath.length());
      for (int i = 0; i < previousPath.length(); i++) {
        if (i + 2 < previousPath.length() && previousPath.charAt(i) == '%') {
          int firstDigit = Character.digit(previousPath.charAt(i + 1), 16);
          int secondDigit = Character.digit(previousPath.charAt(i + 2), 16);
          if (firstDigit >= 0 && secondDigit >= 0) {
            int value = firstDigit * 16 + secondDigit;
            if (value == '%' || value == '.' || value == '/' || value == '\\') {
              decoded.append((char) value);
              i += 2;
              continue;
            }
          }
        }
        decoded.append(previousPath.charAt(i));
      }
      decodedPath = decoded.toString();
    } while (!decodedPath.equals(previousPath));
    return decodedPath;
  }

  private String appendLineFragment(String url, Integer line) {
    return url + "#" + LINE_FRAGMENT_PREFIX + line;
  }
}

@Extension
class FileFrontendLinkResolver extends SourceFrontendLinkResolverBase {

  @Inject
  FileFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory) {
    super(repositoryServiceFactory);
  }

  @Override
  public String getTargetType() {
    return "file";
  }

  @Override
  public String getDescription() {
    return "Link to a file in a repository.";
  }

  @Override
  public Set<FrontendLinkParameter> getOptionalParameters() {
    return Set.of(FrontendLinkParameter.LINE);
  }

  @Override
  boolean supportsLine() {
    return true;
  }
}

@Extension
class DirectoryFrontendLinkResolver extends SourceFrontendLinkResolverBase {

  @Inject
  DirectoryFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory) {
    super(repositoryServiceFactory);
  }

  @Override
  public String getTargetType() {
    return "directory";
  }

  @Override
  public String getDescription() {
    return "Link to a directory in a repository.";
  }

  @Override
  boolean supportsLine() {
    return false;
  }
}

abstract class RevisionFrontendLinkResolver implements FrontendLinkResolver {

  private final RepositoryServiceFactory repositoryServiceFactory;
  private final String type;
  private final String urlPath;
  private final String description;

  RevisionFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory, String type) {
    this(repositoryServiceFactory, type, type);
  }

  RevisionFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory, String type, String urlPath) {
    this.repositoryServiceFactory = repositoryServiceFactory;
    this.type = type;
    this.urlPath = urlPath;
    this.description = String.format("Link to a %s in a repository. Specify the %s name as the revision", type, type);
  }

  @Override
  public String getTargetType() {
    return type;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public Set<FrontendLinkParameter> getRequiredParameters() {
    return Set.of(
      FrontendLinkParameter.NAMESPACE,
      FrontendLinkParameter.NAME,
      FrontendLinkParameter.REVISION
    );
  }

  @Override
  public FrontendLinkResult createLink(CreateFrontendLinkInput input) {
    Map<String, Object> metadata = repositoryMetadata(input);
    metadata.put(type, input.getRevision());
    return FrontendLinkResult.of(
      getTargetType(),
      String.format("%s/%s %s %s", input.getNamespace(), input.getName(), type, input.getRevision()),
      String.format("%s/%s/%s", findRepositoryUrl(repositoryServiceFactory, input), urlPath, HttpUtil.encode(input.getRevision())),
      metadata
    );
  }
}

@Extension
class CommitFrontendLinkResolver extends RevisionFrontendLinkResolver {

  @Inject
  CommitFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory) {
    super(repositoryServiceFactory, "commit", "code/changeset");
  }
}

@Extension
class BranchFrontendLinkResolver extends RevisionFrontendLinkResolver {

  @Inject
  BranchFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory) {
    super(repositoryServiceFactory, "branch");
  }
}

@Extension
class TagFrontendLinkResolver extends RevisionFrontendLinkResolver {

  @Inject
  TagFrontendLinkResolver(RepositoryServiceFactory repositoryServiceFactory) {
    super(repositoryServiceFactory, "tag");
  }
}

class NoHttpUrlFoundForRepositoryException extends RuntimeException {
  public NoHttpUrlFoundForRepositoryException(NamespaceAndName namespaceAndName) {
    super("Could not find http URL for repository " + namespaceAndName);
  }
}
