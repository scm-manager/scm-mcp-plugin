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

public enum FrontendLinkParameter {
  NAMESPACE("namespace"),
  NAME("name"),
  REVISION("revision"),
  PATH("path"),
  ID("id"),
  LINE("line");

  private final String fieldName;

  FrontendLinkParameter(String fieldName) {
    this.fieldName = fieldName;
  }

  public String getFieldName() {
    return fieldName;
  }

  boolean isSet(CreateFrontendLinkInput input) {
    return switch (this) {
      case NAMESPACE -> !Strings.isNullOrEmpty(input.getNamespace());
      case NAME -> !Strings.isNullOrEmpty(input.getName());
      case REVISION -> !Strings.isNullOrEmpty(input.getRevision());
      case PATH -> !Strings.isNullOrEmpty(input.getPath());
      case ID -> !Strings.isNullOrEmpty(input.getId());
      case LINE -> input.getLine() != null;
    };
  }
}
