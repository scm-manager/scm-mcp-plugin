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
class BranchComparisonToolDiffExtension implements ToolDiffExtensionPoint {

  @Override
  public String usageDescription() {
    return "Branch Comparison: Use triple-dot syntax 'base...head' to show changes in 'head' since its common ancestor with 'base' (e.g., 'main...feature-branch').";
  }

  @Override
  public boolean canHandleExpression(ToolDiffInput input) {
    return input.getDiffTarget().contains("...");
  }

  @Override
  public void handleExpression(ToolDiffInput input, DiffResultCommandBuilder commandBuilder) {
    String[] revisions = input.getDiffTarget().split("\\.\\.\\.", 2);
    commandBuilder.setRevision(revisions[1]);
    commandBuilder.setAncestorChangeset(revisions[0]);
  }

  @Override
  public int getPriority() {
    return 2;
  }
}
