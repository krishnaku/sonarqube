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
package org.sonar.ce.queue;

import com.google.common.base.Optional;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.internal.TestSystem2;
import org.sonar.ce.monitoring.CEQueueStatus;
import org.sonar.server.computation.monitoring.CEQueueStatusImpl;
import org.sonar.core.util.UuidFactory;
import org.sonar.core.util.UuidFactoryImpl;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.ComponentDto;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class CeQueueImplTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  System2 system2 = new TestSystem2().setNow(1_450_000_000_000L);

  @Rule
  public DbTester dbTester = DbTester.create(system2);
  DbSession session = dbTester.getSession();

  UuidFactory uuidFactory = UuidFactoryImpl.INSTANCE;
  CEQueueStatus queueStatus = new CEQueueStatusImpl();
  CeQueueListener listener = mock(CeQueueListener.class);
  CeQueue underTest = new CeQueueImpl(dbTester.getDbClient(), uuidFactory, queueStatus, new CeQueueListener[] {listener});

  @Before
  public void setUp() throws Exception {
    queueStatus.initPendingCount(0);
  }

  @Test
  public void submit_returns_task_populated_from_CeTaskSubmit_and_creates_CeQueue_row() {
    CeTaskSubmit taskSubmit = createTaskSubmit(CeTaskTypes.REPORT, "PROJECT_1", "rob");
    CeTask task = underTest.submit(taskSubmit);

    verifyCeTask(taskSubmit, task, null);
    verifyCeQueueDtoForTaskSubmit(taskSubmit);
  }

  @Test
  public void submit_increments_receivedCount_of_QueueStatus() {
    underTest.submit(createTaskSubmit(CeTaskTypes.REPORT, "PROJECT_1", "rob"));

    assertThat(queueStatus.getReceivedCount()).isEqualTo(1L);

    underTest.submit(createTaskSubmit(CeTaskTypes.REPORT, "PROJECT_2", "rob"));

    assertThat(queueStatus.getReceivedCount()).isEqualTo(2L);
  }

  @Test
  public void submit_populates_component_name_and_key_of_CeTask_if_component_exists() {
    ComponentDto componentDto = insertComponent(newComponentDto("PROJECT_1"));
    CeTaskSubmit taskSubmit = createTaskSubmit(CeTaskTypes.REPORT, componentDto.uuid(), null);

    CeTask task = underTest.submit(taskSubmit);

    verifyCeTask(taskSubmit, task, componentDto);
  }

  @Test
  public void submit_returns_task_without_component_info_when_submit_has_none() {
    CeTaskSubmit taskSubmit = createTaskSubmit("not cpt related");

    CeTask task = underTest.submit(taskSubmit);

    verifyCeTask(taskSubmit, task, null);
  }

  @Test
  public void submit_fails_with_ISE_if_paused() {
    underTest.pauseSubmit();

    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Compute Engine does not currently accept new tasks");

    submit(CeTaskTypes.REPORT, "PROJECT_1");
  }

  @Test
  public void massSubmit_returns_tasks_for_each_CeTaskSubmit_populated_from_CeTaskSubmit_and_creates_CeQueue_row_for_each() {
    CeTaskSubmit taskSubmit1 = createTaskSubmit(CeTaskTypes.REPORT, "PROJECT_1", "rob");
    CeTaskSubmit taskSubmit2 = createTaskSubmit("some type");

    List<CeTask> tasks = underTest.massSubmit(asList(taskSubmit1, taskSubmit2));

    assertThat(tasks).hasSize(2);
    verifyCeTask(taskSubmit1, tasks.get(0), null);
    verifyCeTask(taskSubmit2, tasks.get(1), null);
    verifyCeQueueDtoForTaskSubmit(taskSubmit1);
    verifyCeQueueDtoForTaskSubmit(taskSubmit2);
  }

  @Test
  public void massSubmit_populates_component_name_and_key_of_CeTask_if_component_exists() {
    ComponentDto componentDto1 = insertComponent(newComponentDto("PROJECT_1"));
    CeTaskSubmit taskSubmit1 = createTaskSubmit(CeTaskTypes.REPORT, componentDto1.uuid(), null);
    CeTaskSubmit taskSubmit2 = createTaskSubmit("something", "non existing component uuid", null);

    List<CeTask> tasks = underTest.massSubmit(asList(taskSubmit1, taskSubmit2));

    assertThat(tasks).hasSize(2);
    verifyCeTask(taskSubmit1, tasks.get(0), componentDto1);
    verifyCeTask(taskSubmit2, tasks.get(1), null);
  }

  @Test
  public void massSubmit_increments_receivedCount_of_QueueStatus() {
    underTest.massSubmit(asList(createTaskSubmit("type 1"), createTaskSubmit("type 2")));

    assertThat(queueStatus.getReceivedCount()).isEqualTo(2L);

    underTest.massSubmit(asList(createTaskSubmit("a"), createTaskSubmit("a"), createTaskSubmit("b")));

    assertThat(queueStatus.getReceivedCount()).isEqualTo(5L);
  }


  @Test
  public void cancel_pending() throws Exception {
    CeTask task = submit(CeTaskTypes.REPORT, "PROJECT_1");

    // ignore
    boolean canceled = underTest.cancel("UNKNOWN");
    assertThat(canceled).isFalse();
    verifyZeroInteractions(listener);

    canceled = underTest.cancel(task.getUuid());
    assertThat(canceled).isTrue();
    Optional<CeActivityDto> activity = dbTester.getDbClient().ceActivityDao().selectByUuid(dbTester.getSession(), task.getUuid());
    assertThat(activity.isPresent()).isTrue();
    assertThat(activity.get().getStatus()).isEqualTo(CeActivityDto.Status.CANCELED);
    verify(listener).onRemoved(task, CeActivityDto.Status.CANCELED);
  }

  @Test
  public void fail_to_cancel_if_in_progress() throws Exception {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage(startsWith("Task is in progress and can't be canceled"));

    CeTask task = submit(CeTaskTypes.REPORT, "PROJECT_1");

    dbTester.getDbClient().ceQueueDao().peek(session);

    underTest.cancel(task.getUuid());
  }

  @Test
  public void cancelAll_pendings_but_not_in_progress() throws Exception {
    CeTask inProgressTask = submit(CeTaskTypes.REPORT, "PROJECT_1");
    CeTask pendingTask1 = submit(CeTaskTypes.REPORT, "PROJECT_2");
    CeTask pendingTask2 = submit(CeTaskTypes.REPORT, "PROJECT_3");

    dbTester.getDbClient().ceQueueDao().peek(session);

    int canceledCount = underTest.cancelAll();
    assertThat(canceledCount).isEqualTo(2);

    Optional<CeActivityDto> history = dbTester.getDbClient().ceActivityDao().selectByUuid(dbTester.getSession(), pendingTask1.getUuid());
    assertThat(history.get().getStatus()).isEqualTo(CeActivityDto.Status.CANCELED);
    history = dbTester.getDbClient().ceActivityDao().selectByUuid(dbTester.getSession(), pendingTask2.getUuid());
    assertThat(history.get().getStatus()).isEqualTo(CeActivityDto.Status.CANCELED);
    history = dbTester.getDbClient().ceActivityDao().selectByUuid(dbTester.getSession(), inProgressTask.getUuid());
    assertThat(history.isPresent()).isFalse();

    verify(listener).onRemoved(pendingTask1, CeActivityDto.Status.CANCELED);
    verify(listener).onRemoved(pendingTask2, CeActivityDto.Status.CANCELED);
  }

  @Test
  public void pause_and_resume_submits() throws Exception {
    assertThat(underTest.isSubmitPaused()).isFalse();
    underTest.pauseSubmit();
    assertThat(underTest.isSubmitPaused()).isTrue();
    underTest.resumeSubmit();
    assertThat(underTest.isSubmitPaused()).isFalse();
  }

  private void verifyCeTask(CeTaskSubmit taskSubmit, CeTask task, @Nullable ComponentDto componentDto) {
    assertThat(task.getUuid()).isEqualTo(taskSubmit.getUuid());
    assertThat(task.getComponentUuid()).isEqualTo(task.getComponentUuid());
    assertThat(task.getType()).isEqualTo(taskSubmit.getType());
    if (componentDto == null) {
      assertThat(task.getComponentKey()).isNull();
      assertThat(task.getComponentName()).isNull();
    } else {
      assertThat(task.getComponentKey()).isEqualTo(componentDto.key());
      assertThat(task.getComponentName()).isEqualTo(componentDto.name());
    }
    assertThat(task.getSubmitterLogin()).isEqualTo(taskSubmit.getSubmitterLogin());
  }

  private void verifyCeQueueDtoForTaskSubmit(CeTaskSubmit taskSubmit) {
    Optional<CeQueueDto> queueDto = dbTester.getDbClient().ceQueueDao().selectByUuid(dbTester.getSession(), taskSubmit.getUuid());
    assertThat(queueDto.isPresent()).isTrue();
    assertThat(queueDto.get().getTaskType()).isEqualTo(taskSubmit.getType());
    assertThat(queueDto.get().getComponentUuid()).isEqualTo(taskSubmit.getComponentUuid());
    assertThat(queueDto.get().getSubmitterLogin()).isEqualTo(taskSubmit.getSubmitterLogin());
    assertThat(queueDto.get().getCreatedAt()).isEqualTo(1_450_000_000_000L);
  }

  private static ComponentDto newComponentDto(String uuid) {
    return new ComponentDto().setUuid(uuid).setName("name_" + uuid).setKey("key_" + uuid);
  }

  private CeTask submit(String reportType, String componentUuid) {
    return underTest.submit(createTaskSubmit(reportType, componentUuid, null));
  }

  private CeTaskSubmit createTaskSubmit(String type) {
    return createTaskSubmit(type, null, null);
  }

  private CeTaskSubmit createTaskSubmit(String type, @Nullable String componentUuid, @Nullable String submitterLogin) {
    CeTaskSubmit.Builder submission = underTest.prepareSubmit();
    submission.setType(type);
    submission.setComponentUuid(componentUuid);
    submission.setSubmitterLogin(submitterLogin);
    return submission.build();
  }

  private CeTaskResult newTaskResult(Long snapshotId) {
    CeTaskResult taskResult = mock(CeTaskResult.class);
    when(taskResult.getSnapshotId()).thenReturn(snapshotId);
    return taskResult;
  }

  private ComponentDto insertComponent(ComponentDto componentDto) {
    dbTester.getDbClient().componentDao().insert(session, componentDto);
    session.commit();
    return componentDto;
  }
}
