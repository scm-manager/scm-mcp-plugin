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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;

class ToolListCommitsFilterEnhancementTest {

  @Test
  void shouldReturnCorrectInput() {
    ToolListCommits.CompositeInput input = new ToolListCommits.CompositeInput(new ListCommitsInput());
    input.addExtensionInput("hog", 42);

    ToolListCommitsFilterEnhancement extension = new ToolListCommitsFilterEnhancement() {
      @Override
      public Optional<Class<?>> getInputClass() {
        return of(Integer.class);
      }

      @Override
      public String getNamespace() {
        return "hog";
      }
    };

    Integer extensionInput = extension.getExtensionInput(input);

    assertThat(extensionInput).isEqualTo(42);
  }

  @Test
  void shouldHandleNotAvailableInput() {
    ToolListCommits.CompositeInput input = new ToolListCommits.CompositeInput(new ListCommitsInput());

    ToolListCommitsFilterEnhancement extension = new ToolListCommitsFilterEnhancement() {
      @Override
      public Optional<Class<?>> getInputClass() {
        return of(Integer.class);
      }

      @Override
      public String getNamespace() {
        return "hog";
      }
    };

    Integer extensionInput = extension.getExtensionInput(input);

    assertThat(extensionInput).isNull();
  }

  @Test
  void shouldPreventWrongInput() {
    ToolListCommits.CompositeInput input = new ToolListCommits.CompositeInput(new ListCommitsInput());
    input.addExtensionInput("hog", 42);

    ToolListCommitsFilterEnhancement extension = new ToolListCommitsFilterEnhancement() {
      @Override
      public Optional<Class<?>> getInputClass() {
        return of(String.class);
      }

      @Override
      public String getNamespace() {
        return "hog";
      }
    };

    String extensionInput = extension.getExtensionInput(input);

    assertThat(extensionInput).isNull();
  }
}
