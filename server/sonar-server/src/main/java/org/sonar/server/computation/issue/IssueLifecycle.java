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
package org.sonar.server.computation.issue;

import com.google.common.annotations.VisibleForTesting;
import java.util.Date;
import javax.annotation.Nullable;
import org.sonar.api.issue.Issue;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.core.util.Uuids;
import org.sonar.server.computation.analysis.AnalysisMetadataHolder;
import org.sonar.server.issue.IssueUpdater;
import org.sonar.server.issue.workflow.IssueWorkflow;

/**
 * Sets the appropriate fields when an issue is :
 * <ul>
 *   <li>newly created</li>
 *   <li>merged the related base issue</li>
 *   <li>relocated (only manual issues)</li>
 * </ul>
 */
public class IssueLifecycle {

  private final IssueWorkflow workflow;
  private final IssueChangeContext changeContext;
  private final IssueUpdater updater;
  private final DebtCalculator debtCalculator;

  public IssueLifecycle(AnalysisMetadataHolder analysisMetadataHolder, IssueWorkflow workflow, IssueUpdater updater, DebtCalculator debtCalculator) {
    this(IssueChangeContext.createScan(new Date(analysisMetadataHolder.getAnalysisDate())), workflow, updater, debtCalculator);
  }

  @VisibleForTesting
  IssueLifecycle(IssueChangeContext changeContext, IssueWorkflow workflow, IssueUpdater updater, DebtCalculator debtCalculator) {
    this.workflow = workflow;
    this.updater = updater;
    this.debtCalculator = debtCalculator;
    this.changeContext = changeContext;
  }

  public void initNewOpenIssue(DefaultIssue issue) {
    issue.setKey(Uuids.create());
    issue.setCreationDate(changeContext.date());
    issue.setUpdateDate(changeContext.date());
    issue.setStatus(Issue.STATUS_OPEN);
    issue.setEffort(debtCalculator.calculate(issue));
  }

  public void mergeExistingOpenIssue(DefaultIssue raw, DefaultIssue base) {
    raw.setNew(false);
    raw.setKey(base.key());
    raw.setType(base.type());
    raw.setCreationDate(base.creationDate());
    raw.setUpdateDate(base.updateDate());
    raw.setCloseDate(base.closeDate());
    raw.setActionPlanKey(base.actionPlanKey());
    raw.setResolution(base.resolution());
    raw.setStatus(base.status());
    raw.setAssignee(base.assignee());
    raw.setAuthorLogin(base.authorLogin());
    raw.setTags(base.tags());
    raw.setAttributes(base.attributes());
    raw.setEffort(debtCalculator.calculate(raw));
    raw.setOnDisabledRule(base.isOnDisabledRule());
    if (base.manualSeverity()) {
      raw.setManualSeverity(true);
      raw.setSeverity(base.severity());
    } else {
      updater.setPastSeverity(raw, base.severity(), changeContext);
    }

    // fields coming from raw
    updater.setPastLine(raw, base.getLine());
    updater.setPastLocations(raw, base.getLocations());
    updater.setPastMessage(raw, base.getMessage(), changeContext);
    updater.setPastGap(raw, base.effortToFix(), changeContext);
    updater.setPastEffort(raw, base.debt(), changeContext);
    raw.setSelectedAt(base.selectedAt());
  }

  public void moveOpenManualIssue(DefaultIssue manualIssue, @Nullable Integer toLine) {
    updater.setLine(manualIssue, toLine);
  }

  public void doAutomaticTransition(DefaultIssue issue) {
    workflow.doAutomaticTransition(issue, changeContext);
  }
}
