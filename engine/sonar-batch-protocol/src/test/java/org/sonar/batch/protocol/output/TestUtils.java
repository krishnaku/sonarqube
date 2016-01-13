/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.protocol.output;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public final class TestUtils {

  private TestUtils() {
  }

  /**
   * Asserts that all constructors are private, usually for helper classes with
   * only static methods. If a constructor does not have any parameters, then
   * it's instantiated.
   */
  public static boolean hasOnlyPrivateConstructors(Class clazz) {
    boolean ok = true;
    for (Constructor constructor : clazz.getDeclaredConstructors()) {
      ok &= Modifier.isPrivate(constructor.getModifiers());
      if (constructor.getParameterTypes().length == 0) {
        constructor.setAccessible(true);
        try {
          constructor.newInstance();
        } catch (Exception e) {
          throw new IllegalStateException(String.format("Fail to instantiate %s", clazz), e);
        }
      }
    }
    return ok;
  }

}
