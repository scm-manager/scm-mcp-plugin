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

import sonia.scm.repository.Changeset;
import sonia.scm.repository.ChangesetPagingResult;
import sonia.scm.repository.api.LogCommandBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

class ChangesetStreamer {
  private final LogCommandBuilder logCommand;
  private final int internalChunkSize; // How many raw commits to fetch at once

  public ChangesetStreamer(LogCommandBuilder logCommand, int internalChunkSize) {
    this.logCommand = logCommand;
    this.internalChunkSize = internalChunkSize;
  }

  FilterResult fetchFiltered(Predicate<Changeset> filter, int limit) throws IOException {
    List<Changeset> filteredMatches = new ArrayList<>();
    int currentStart = 0;
    int totalRawSearched = 0;
    int overallCount = -1;
    boolean exhausted = false;

    while (filteredMatches.size() < limit && !exhausted) {
      ChangesetPagingResult result = logCommand
        .setPagingStart(currentStart)
        .setPagingLimit(internalChunkSize)
        .getChangesets();

      List<Changeset> chunk = result.getChangesets();
      if (overallCount < 0) {
        overallCount = result.getTotal();
      }

      if (chunk.isEmpty()) {
        exhausted = true;
        break;
      }

      for (Changeset changeset : chunk) {
        totalRawSearched++;
        if (filter.test(changeset)) {
          filteredMatches.add(changeset);
        }

        if (filteredMatches.size() >= limit) {
          break;
        }
      }

      currentStart += chunk.size();

      if (chunk.size() < internalChunkSize) {
        exhausted = true;
      }
    }

    return new FilterResult(filteredMatches, totalRawSearched, exhausted, overallCount);
  }

  record FilterResult(
    List<Changeset> matches,
    int totalSearched,
    boolean endOfHistory,
    int overallCount
  ) {}
}
