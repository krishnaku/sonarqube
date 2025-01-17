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
package org.sonar.batch.cpd;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Settings;

@Phase(name = Phase.Name.POST)
public class CpdSensor implements Sensor {

  private static final Logger LOG = LoggerFactory.getLogger(CpdSensor.class);

  private CpdIndexer sonarEngine;
  private CpdIndexer sonarBridgeEngine;
  private Settings settings;
  private FileSystem fs;

  public CpdSensor(JavaCpdBlockIndexer sonarEngine, DefaultCpdBlockIndexer sonarBridgeEngine, Settings settings, FileSystem fs) {
    this.sonarEngine = sonarEngine;
    this.sonarBridgeEngine = sonarBridgeEngine;
    this.settings = settings;
    this.fs = fs;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("CPD Sensor");
  }

  @VisibleForTesting
  CpdIndexer getEngine(String language) {
    if (sonarEngine.isLanguageSupported(language)) {
      return sonarEngine;
    }
    return sonarBridgeEngine;
  }

  @VisibleForTesting
  boolean isSkipped(String language) {
    String key = "sonar.cpd." + language + ".skip";
    if (settings.hasKey(key)) {
      return settings.getBoolean(key);
    }
    return settings.getBoolean(CoreProperties.CPD_SKIP_PROPERTY);
  }

  @Override
  public void execute(SensorContext context) {
    if (settings.hasKey(CoreProperties.CPD_SKIP_PROPERTY)) {
      LOG.warn("\"sonar.cpd.skip\" property is deprecated and will be removed. Please set \"sonar.cpd.exclusions=**\" instead to disable duplication mechanism.");
    }

    for (String language : fs.languages()) {
      if (settings.hasKey("sonar.cpd." + language + ".skip")) {
        LOG
          .warn("\"sonar.cpd." + language + ".skip\" property is deprecated and will be removed. Please set \"sonar.cpd.exclusions=**\" instead to disable duplication mechanism.");
      }

      if (isSkipped(language)) {
        LOG.info("Detection of duplicated code is skipped for {}", language);
        continue;
      }

      CpdIndexer engine = getEngine(language);
      if (!engine.isLanguageSupported(language)) {
        LOG.debug("Detection of duplicated code is not supported for {}", language);
        continue;
      }
      LOG.info("{} is used for {}", engine, language);
      engine.index(language);
    }
  }

}
