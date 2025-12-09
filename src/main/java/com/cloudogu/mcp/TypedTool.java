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

import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sonia.scm.plugin.ExtensionPoint;

import java.lang.reflect.Constructor;

/**
 * An implementation for an MCP tool. In contrast to {@link Tool}, this offers convenience methods for schema
 * generation and input parsing.
 *
 * @param <I> The class for the input.
 */
public interface TypedTool<I> extends Tool {

  Logger LOGGER = LoggerFactory.getLogger(TypedTool.class);
  SchemaGenerator GENERATOR = createSchemaGenerator();

  /**
   * The input class that will be used to create the JSON schema for this tool and to extract the input from the request.
   */
  Class<? extends I> getInputClass();

  /**
   * Default implementation, parsing the input object using the request and calling
   * {@link #execute( Object)} with this object.
   */
  default McpSchema.CallToolResult execute(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
    ToolResult result = execute(parse(request));
    if (result.isError()) {
      LOGGER.trace("request failed: {}", result.getMessage());
      return new McpSchema.CallToolResult(
        result.getMessage(),
        true
      );
    } else {
      LOGGER.trace("request successful");
      return new McpSchema.CallToolResult(
        result.getContent().stream().map(McpSchema.TextContent::new).map(tc -> (McpSchema.Content) tc).toList(),
        false,
        result.getStructuredContent()
      );
    }
  }

  /**
   * The core function for this tool that will be executed on each call creating a result that will be returned to the
   * AI.
   *
   * @param input The parsed input object extracted from the request.
   */
  ToolResult execute(I input);

  /**
   * Extracts the input from the request.
   */
  default I parse(McpSchema.CallToolRequest request) {
    try {
      return ToolInputParser.INSTANCE.parseAndValidate(request.arguments(), getInputClass());
    } catch (RuntimeException e) {
      LOGGER.debug("failed to parse and validate request", e);
      throw e;
    }
  }

  /**
   * Default implementation creating the schema from {@link #getInputClass()}.
   */
  default String getInputSchema() {
    var schemaNode = GENERATOR.generateSchema(getInputClass());
    schemaNode.put("id", "tools/" + getName());
    String schema = schemaNode.toPrettyString();
    LOGGER.trace("created json schema for class {}:\n{}", getInputClass(), schema);
    return schema;
  }

  private static SchemaGenerator createSchemaGenerator() {
    // Use Jackson to read @JsonPropertyDescription
    JacksonModule jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);

    // Use Jakarta to read @Pattern, @NotNull
    JakartaValidationModule jakartaModule = new JakartaValidationModule(
      JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
      JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS
    );

    // Build configuration
    SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
      SchemaVersion.DRAFT_2020_12,
      OptionPreset.PLAIN_JSON
    );

    configBuilder.with(jacksonModule);
    configBuilder.with(jakartaModule);

    // Read "default" values directly from Java field initializers
    configBuilder.forFields().withDefaultResolver(TypedTool::determineDefaultValue);

    return new SchemaGenerator(configBuilder.build());
  }

  @SuppressWarnings("java:S3011") // We want to increase accessibility here
  private static Object determineDefaultValue(FieldScope scope) {
    try {
      // 1. Get the class that owns the field
      Class<?> declaringType = scope.getMember().getDeclaringType().getErasedType();
      // 2. Create a temporary instance of that class (requires no-arg constructor)
      Constructor<?> constructor = declaringType.getDeclaredConstructor();
      constructor.setAccessible(true);
      Object instance = constructor.newInstance();
      // 3. access the field
      java.lang.reflect.Field field = declaringType.getDeclaredField(scope.getName());
      field.setAccessible(true);
      // 4. Get the value (e.g., "Change by MCP server")
      return field.get(instance);
    } catch (Exception e) {
      // If we can't instantiate or access, ignore default
      LOGGER.warn("Couldn't instantiate {} or read field to determine default value for {}", scope.getMember().getDeclaringType(), scope.getName(), e);
      return null;
    }
  }
}
