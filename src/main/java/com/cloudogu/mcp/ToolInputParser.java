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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class ToolInputParser {

  private final ObjectMapper objectMapper;
  private final Validator validator;

  static final ToolInputParser INSTANCE = new ToolInputParser(createObjectMapper());

  private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  ToolInputParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    // Build the Jakarta Validator (usually done once as a singleton)
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      this.validator = factory.getValidator();
    }
  }

  /**
   * Converts a raw map of arguments into a validated Java object.
   * Throws an exception with a clear message if validation fails.
   */
  <T> T parseAndValidate(Map<String, Object> arguments, Class<T> targetClass) {
    // 1. Convert (Map -> POJO)
    // This handles type coercion (e.g., JSON integer to Java int) automatically.
    T input;
    try {
      input = objectMapper.convertValue(arguments, targetClass);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Failed to map arguments: " + e.getMessage(), e);
    }

    // 2. Validate (POJO Annotations)
    Set<ConstraintViolation<T>> violations = validator.validate(input);

    if (!violations.isEmpty()) {
      // Join all errors into a single readable string
      String errorMessage = violations.stream()
        .map(v -> String.format("Field '%s' %s", v.getPropertyPath(), v.getMessage()))
        .collect(Collectors.joining("; "));

      throw new IllegalArgumentException("Validation failed: " + errorMessage);
    }

    return input;
  }
}
