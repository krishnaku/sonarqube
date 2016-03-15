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
package org.sonar.server.computation.monitoring;

import java.util.LinkedHashMap;
import org.sonar.ce.monitoring.CEQueueStatus;
import org.sonar.server.computation.configuration.CeConfiguration;
import org.sonar.ce.queue.CeQueue;
import org.sonar.server.platform.monitoring.BaseMonitorMBean;

public class ComputeEngineQueueMonitor extends BaseMonitorMBean implements ComputeEngineQueueMonitorMBean {
  private final CEQueueStatus queueStatus;
  private final CeConfiguration ceConfiguration;

  public ComputeEngineQueueMonitor(CEQueueStatus queueStatus,
    // ReportQueue initializes CEQueueStatus and is therefor a dependency of
    // ComputeEngineQueueMonitor.
    // Do not remove this parameter, it ensures start order of components
    CeQueue ceQueue,
    CeConfiguration ceConfiguration) {
    this.queueStatus = queueStatus;
    this.ceConfiguration = ceConfiguration;
  }

  @Override
  public String name() {
    return "ComputeEngine";
  }

  @Override
  public LinkedHashMap<String, Object> attributes() {
    LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("Received", getReceivedCount());
    attributes.put("Pending", getPendingCount());
    attributes.put("In progress", getInProgressCount());
    attributes.put("Successfully processed", getSuccessCount());
    attributes.put("Processed with error", getErrorCount());
    attributes.put("Processing time", getProcessingTime());
    attributes.put("Worker count", getWorkerCount());
    return attributes;
  }

  @Override
  public long getReceivedCount() {
    return queueStatus.getReceivedCount();
  }

  @Override
  public long getPendingCount() {
    return queueStatus.getPendingCount();
  }

  @Override
  public long getInProgressCount() {
    return queueStatus.getInProgressCount();
  }

  @Override
  public long getErrorCount() {
    return queueStatus.getErrorCount();
  }

  @Override
  public long getSuccessCount() {
    return queueStatus.getSuccessCount();
  }

  @Override
  public long getProcessingTime() {
    return queueStatus.getProcessingTime();
  }

  @Override
  public int getWorkerCount() {
    return ceConfiguration.getWorkerCount();
  }
}
