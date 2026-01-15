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

import sonia.scm.repository.Branch;
import sonia.scm.util.ValidationUtil;

class Validations {
  static final String REPOSITORY_NAME_REGEX = ValidationUtil.REGEX_REPOSITORYNAME;
  static final String REPOSITORY_NAMESPACE_REGEX = ValidationUtil.REGEX_NAME;
  static final String BRANCH_REGEX = Branch.VALID_BRANCH_NAMES;
}
