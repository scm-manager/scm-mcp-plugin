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

import sonia.scm.plugin.Extension;
import sonia.scm.repository.api.DiffResultCommandBuilder;

@Extension
class SimpleRevisionToolDiffExtension implements ToolDiffExtensionPoint {

  @Override
  public String usageDescription() {
    return "Single Revision: Provide a commit hash, branch name, or tag (e.g., 'a1b2c3d', 'main', or 'v1.0.0').";
  }

  @Override
  public boolean canHandleExpression(ToolDiffInput input) {
    String target = input.getDiffTarget();
    return !target.contains("#") && !target.contains("...");
  }

  @Override
  public void handleExpression(ToolDiffInput input, DiffResultCommandBuilder commandBuilder) {
    commandBuilder.setRevision(input.getDiffTarget());
  }

  @Override
  public int getPriority() {
    return 1;
  }
}
