/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.api.batch.sensor.cpd.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.internal.DefaultStorable;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.duplications.internal.pmd.TokensLine;

public class DefaultCpdTokens extends DefaultStorable implements NewCpdTokens {

  private final ImmutableList.Builder<TokensLine> result = ImmutableList.builder();
  private DefaultInputFile inputFile;
  private int startLine = Integer.MIN_VALUE;
  private int startIndex = 0;
  private int currentIndex = 0;
  private StringBuilder sb = new StringBuilder();
  private TextRange lastRange;

  public DefaultCpdTokens(SensorStorage storage) {
    super(storage);
  }

  @Override
  public DefaultCpdTokens onFile(InputFile inputFile) {
    Preconditions.checkNotNull(inputFile, "file can't be null");
    this.inputFile = (DefaultInputFile) inputFile;
    return this;
  }

  public InputFile inputFile() {
    return inputFile;
  }

  @Override
  public NewCpdTokens addToken(TextRange range, String image) {
    Preconditions.checkNotNull(range, "Range should not be null");
    Preconditions.checkState(inputFile != null, "Call onFile() first");
    Preconditions.checkState(lastRange == null || lastRange.end().compareTo(range.start()) >= 0,
      "Tokens of file %s should be provided in order. \nPrevious token: %s\nLast token: %s", inputFile, lastRange, range);

    String value = image;

    int line = range.start().line();
    if (line != startLine) {
      addNewTokensLine(result, startIndex, currentIndex, startLine, sb);
      startIndex = currentIndex + 1;
      startLine = line;
    }
    currentIndex++;
    sb.append(value);

    return this;
  }

  public List<TokensLine> getTokenLines() {
    return result.build();
  }

  private static void addNewTokensLine(ImmutableList.Builder<TokensLine> result, int startUnit, int endUnit, int startLine, StringBuilder sb) {
    if (sb.length() != 0) {
      result.add(new TokensLine(startUnit, endUnit, startLine, sb.toString()));
      sb.setLength(0);
    }
  }

  @Override
  protected void doSave() {
    Preconditions.checkState(inputFile != null, "Call onFile() first");
    addNewTokensLine(result, startIndex, currentIndex, startLine, sb);
    storage.store(this);
  }
}
