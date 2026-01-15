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

import org.hibernate.validator.internal.util.StringHelper;
import sonia.scm.plugin.ExtensionPoint;
import sonia.scm.search.Hit;

import java.util.HashMap;
import java.util.Map;

@ExtensionPoint
public interface ToolSearchExtension {

  String getSearchType();

  String getSummary();

  String getDescription();

  String[] tableColumnHeader();

  String[] transformHitToTableFields(Hit hit);

  default Map<String, Object> transformHitToStructuredAnswer(Hit hit) {
    Map<String, Object> transformedFields = new HashMap<>(hit.getFields().size());
    hit.getFields().forEach(
      (fieldName, field) -> transformedFields.put(fieldName, extractValue(field))
    );
    return transformedFields;
  }

  default String extractValue(Hit hit, String fieldName) {
    return extractValue(hit.getFields().get(fieldName));
  }

  default String extractValue(Hit.Field field) {
    if (field instanceof Hit.ValueField valueField) {
      return valueField.getValue().toString();
    } else if (field instanceof Hit.HighlightedField highlightedField) {
      return StringHelper.join(highlightedField.getFragments(), " [...] ");
    }
    return "";
  }
}
