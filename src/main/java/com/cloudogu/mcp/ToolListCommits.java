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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import sonia.scm.plugin.Extension;
import sonia.scm.repository.Changeset;
import sonia.scm.repository.NamespaceAndName;
import sonia.scm.repository.RepositoryPermissions;
import sonia.scm.repository.api.LogCommandBuilder;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Extension
public class ToolListCommits implements TypedTool<ToolListCommits.CompositeInput> {

  private final RepositoryServiceFactory repositoryServiceFactory;
  private final Set<ToolListCommitsFilterEnhancement> extensions;

  @Inject
  public ToolListCommits(RepositoryServiceFactory repositoryServiceFactory, Set<ToolListCommitsFilterEnhancement> extensions) {
    this.repositoryServiceFactory = repositoryServiceFactory;
    this.extensions = extensions;
  }

  @Override
  public Class<? extends CompositeInput> getInputClass() {
    return CompositeInput.class;
  }

  @Override
  public ToolResult execute(CompositeInput compositeInput) {
    log.trace("executing request {}", compositeInput);

    ListCommitsInput input = compositeInput.baseInput;

    try (RepositoryService repositoryService = repositoryServiceFactory.create(new NamespaceAndName(input.getNamespace(), input.getName()))) {
      if (!RepositoryPermissions.read(repositoryService.getRepository()).isPermitted()) {
        log.trace("requested repository not authorized");
        return ToolResult.error("User is not authorized to use this resource.");
      }
      return listCommits(compositeInput, repositoryService, input);
    } catch (IOException e) {
      log.debug("got exception while executing request", e);
      return ToolResult.error(
        "Something went wrong reading the commits. Please check the namespace and the name of the repository."
      );
    }
  }

  private ToolResult listCommits(CompositeInput compositeInput, RepositoryService repositoryService, ListCommitsInput input) throws IOException {
    LogCommandBuilder logCommandBuilder = repositoryService
      .getLogCommand()
      .setStartChangeset(input.getRevision())
      .setPagingLimit(input.getLimit());
    if (!Strings.isNullOrEmpty(input.getRevision())) {
      logCommandBuilder.setBranch(input.getRevision());
    }

    Optional<String> error = extensions.stream()
      .map(extension -> extension.configure(repositoryService.getRepository(), logCommandBuilder, compositeInput))
      .flatMap(Optional::stream)
      .findFirst();
    if (error.isPresent()) {
      return ToolResult.error(error.get());
    }

    ChangesetStreamer.FilterResult filterResult = applyFilters(compositeInput, repositoryService, input, logCommandBuilder);

    log.trace("found {} commits", filterResult.matches().size());
    if (filterResult.matches().isEmpty()) {
      return ToolResult.ok(OkResultRenderer.success("None of the commits match your input.").toString());
    }

    OkResultRenderer.PostponedResultRenderer resultRenderer = OkResultRenderer.postponedStatus();

    if (input.isIncludeDetails()) {
      resultRenderer.withInfoText("Detailed metadata (complete commit message labeled as 'description', parents, and contributors) for each commit is available in the structured data block under their respective revisions.");
    }

    int foundCounter = filterResult.matches().size();

    Map<String, Object> structuredContent = createStructuredContent(compositeInput, repositoryService, input, filterResult);
    renderContent(filterResult, resultRenderer);

    OkResultRenderer result;
    if (filterResult.endOfHistory()) {
      result = resultRenderer.withSuccess(String.format("Found all %s commits of %s in total.", foundCounter, filterResult.overallCount()));
    } else {
      result = resultRenderer.withSuccess(String.format("Found the first %s commits of %s in total.", foundCounter, filterResult.overallCount()));
    }

    return result.render(structuredContent);
  }

  private void renderContent(ChangesetStreamer.FilterResult filterResult, OkResultRenderer.PostponedResultRenderer resultRenderer) {
    for (Changeset changeset : filterResult.matches()) {
      resultRenderer.append("commit ").append(changeset.getId());
      if (!changeset.getTags().isEmpty() || !changeset.getBranches().isEmpty()) {
        resultRenderer.append(" (");
        if (!changeset.getTags().isEmpty()) {
          resultRenderer.append(changeset.getTags().stream().map(t -> "tag: " + t).collect(Collectors.joining(", ")));
        }
        if (!changeset.getBranches().isEmpty()) {
          resultRenderer.append(String.join(", ", changeset.getBranches()));
        }
        resultRenderer.append(')');
      }
      resultRenderer.append('\n');
      resultRenderer.append("Author: ").append(changeset.getAuthor()).append('\n');
      resultRenderer.append("Date: ").append(Instant.ofEpochMilli(changeset.getDate())).append('\n');
      resultRenderer.append('\n');
      resultRenderer.append("    ").append(changeset.getDescription().split("\n")[0]).append("\n");
      resultRenderer.append('\n');
    }
  }

  private Map<String, Object> createStructuredContent(CompositeInput compositeInput, RepositoryService repositoryService, ListCommitsInput input, ChangesetStreamer.FilterResult filterResult) {
    Map<String, Object> structuredContent = input.isIncludeDetails() ? new HashMap<>() : null;
    for (Changeset changeset : filterResult.matches()) {
      if (input.isIncludeDetails()) {
        Map<String, Object> changesetInfo = new HashMap<>();
        changesetInfo.put("author", changeset.getAuthor());
        changesetInfo.put("description", changeset.getDescription());
        changesetInfo.put("contributors", changeset.getContributors());
        changesetInfo.put("parents", changeset.getParents());
        changesetInfo.put("tags", changeset.getTags());
        extensions.forEach(extension -> extension.enhanceStructuredResult(repositoryService.getRepository(), changeset, compositeInput, changesetInfo::put));
        structuredContent.put(changeset.getId(), changesetInfo);
      }
    }
    return structuredContent;
  }

  private ChangesetStreamer.FilterResult applyFilters(CompositeInput compositeInput, RepositoryService repositoryService, ListCommitsInput input, LogCommandBuilder logCommandBuilder) throws IOException {
    ChangesetStreamer streamer = new ChangesetStreamer(logCommandBuilder, 20);
    return streamer.fetchFiltered(
      changeset -> (Strings.isNullOrEmpty(input.getAuthorFilter())
        || StringUtils.containsIgnoreCase(changeset.getAuthor().toString(), input.getAuthorFilter()))
        && (Strings.isNullOrEmpty(input.getCommitMessageFilter())
        || StringUtils.containsIgnoreCase(changeset.getDescription(), input.getCommitMessageFilter()))
        && (input.getCommittedBefore() == null
        || Instant.ofEpochMilli(changeset.getDate()).isBefore(input.getCommittedBefore()))
        && (input.getCommittedAfter() == null
        || Instant.ofEpochMilli(changeset.getDate()).isAfter(input.getCommittedAfter()))
        && (extensions.stream().allMatch(extension -> extension.includeCommit(repositoryService.getRepository(), changeset, compositeInput)
      )),
      input.getLimit()
    );
  }

  @Override
  public String getInputSchema() {
    // 1. Generate the Base Schema (The "Host")
    ObjectNode rootSchema = TypedTool.GENERATOR.generateSchema(ListCommitsInput.class);

    // Ensure 'properties' and 'required' nodes exist in the root, creating them if needed
    ObjectNode rootProperties = rootSchema.withObject("/properties");
    ArrayNode rootRequired = rootSchema.withArray("required");

    // 2. Iterate over all registered extensions
    for (ToolListCommitsFilterEnhancement ext : this.extensions) {
      String prefix = ext.getNamespace() + "_";
      ext.getInputClass().ifPresent(
        extClass -> {
          // 3. Generate the schema for this specific extension
          // We act as if this extension were a standalone tool for a moment
          ObjectNode extSchema = TypedTool.GENERATOR.generateSchema(extClass);

          // 4. Graft 'properties' (Fields)
          JsonNode extProperties = extSchema.get("properties");
          if (extProperties != null && extProperties.isObject()) {
            extProperties.fields().forEachRemaining(entry -> {
              String originalName = entry.getKey();
              JsonNode fieldDef = entry.getValue();

              // Create the new prefixed name: "jira_status"
              String namespacedName = prefix + originalName;

              // Inject into the Base Schema
              rootProperties.set(namespacedName, fieldDef);
            });
          }

          // 5. Graft 'required' (Validation)
          JsonNode extRequired = extSchema.get("required");
          if (extRequired != null && extRequired.isArray()) {
            for (JsonNode reqField : extRequired) {
              // Read the required field name from the extension schema
              String originalName = reqField.asText();

              // Prefix it so it matches the property we just added above
              String namespacedName = prefix + originalName;

              // Add to the Base Schema's required list
              rootRequired.add(namespacedName);
            }
          }
        }
      );


    }

    // 6. Final Polish
    // If we added a namespace that collided or created weird state, Jackson handles the JSON structure,
    // but the 'id' helps debugging.
    rootSchema.put("id", "tools/" + getName());

    // Clean up: if 'required' is empty (no fields were mandatory), strictly speaking it's valid,
    // but some parsers prefer it removed. Optional step.
    if (rootRequired.isEmpty()) {
      rootSchema.remove("required");
    }

    String finalSchema = rootSchema.toPrettyString();
    LOGGER.trace("Created composite schema for tool {}:\n{}", getName(), finalSchema);
    return finalSchema;
  }

  @Override
  public CompositeInput parse(McpSchema.CallToolRequest request) {
    Map<String, Object> rawArguments = request.arguments();

    // 1. Parse Base Input
    // (Assuming base fields are NOT prefixed, or use a "base" prefix)
    ListCommitsInput base = ToolInputParser.INSTANCE.parseAndValidate(
      rawArguments,
      ListCommitsInput.class
    );

    CompositeInput result = new CompositeInput(base);

    // 2. Parse Extensions
    for (ToolListCommitsFilterEnhancement ext : this.extensions) {
      if (ext.getInputClass().isPresent()) {
        String prefix = ext.getNamespace() + "_";

        // Extract only the fields belonging to this extension and strip the prefix
        Map<String, Object> extArgs = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawArguments.entrySet()) {
          if (entry.getKey().startsWith(prefix)) {
            String realFieldName = entry.getKey().substring(prefix.length());
            extArgs.put(realFieldName, entry.getValue());
          }
        }

        // Only parse if we found arguments for this extension
        if (!extArgs.isEmpty()) {
          Object extInput = ToolInputParser.INSTANCE.parseAndValidate(
            extArgs,
            ext.getRequiredInputClass()
          );
          result.addExtensionInput(ext.getNamespace(), extInput);
        }
      }
    }

    return result;
  }

  @Override
  public String getName() {
    return "list-commits";
  }

  @Override
  public String getDescription() {
    return "List commits for a revision";
  }

  public static class CompositeInput {
    private final ListCommitsInput baseInput;
    private final Map<String, Object> extensionInputs = new HashMap<>();

    CompositeInput(ListCommitsInput baseInput) {
      this.baseInput = baseInput;
    }

    public ListCommitsInput getBaseInput() {
      return baseInput;
    }

    public Object getExtensionInput(String namespace) {
      return extensionInputs.get(namespace);
    }

    void addExtensionInput(String namespace, Object extensionInput) {
      extensionInputs.put(namespace, extensionInput);
    }
  }
}
