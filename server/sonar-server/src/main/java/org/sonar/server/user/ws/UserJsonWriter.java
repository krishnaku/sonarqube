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
package org.sonar.server.user.ws;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.user.UserDto;
import org.sonar.server.user.UserSession;

import static org.sonar.server.ws.JsonWriterUtils.isFieldNeeded;
import static org.sonar.server.ws.JsonWriterUtils.writeIfNeeded;

public class UserJsonWriter {

  private static final String FIELD_LOGIN = "login";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_EMAIL = "email";
  private static final String FIELD_SCM_ACCOUNTS = "scmAccounts";
  private static final String FIELD_GROUPS = "groups";
  private static final String FIELD_ACTIVE = "active";
  private static final String FIELD_TOKENS_COUNT = "tokensCount";
  private static final String FIELD_LOCAL = "local";

  public static final Set<String> FIELDS = ImmutableSet.of(FIELD_NAME, FIELD_EMAIL, FIELD_SCM_ACCOUNTS, FIELD_GROUPS, FIELD_ACTIVE, FIELD_LOCAL);
  private static final Set<String> CONCISE_FIELDS = ImmutableSet.of(FIELD_NAME, FIELD_EMAIL, FIELD_ACTIVE);

  private final UserSession userSession;

  public UserJsonWriter(UserSession userSession) {
    this.userSession = userSession;
  }

  /**
   * Serializes a user to the passed JsonWriter.
   */
  public void write(JsonWriter json, UserDto user, Collection<String> groups, @Nullable Collection<String> fields) {
    write(json, user, null, groups, fields);
  }

  /**
   * Serializes a user to the passed JsonWriter.
   */
  public void write(JsonWriter json, UserDto user, @Nullable Integer tokensCount, Collection<String> groups, @Nullable Collection<String> fields) {
    json.beginObject();
    json.prop(FIELD_LOGIN, user.getLogin());
    writeIfNeeded(json, user.getName(), FIELD_NAME, fields);
    writeIfNeeded(json, user.getEmail(), FIELD_EMAIL, fields);
    writeIfNeeded(json, user.isActive(), FIELD_ACTIVE, fields);
    writeIfNeeded(json, user.isLocal(), FIELD_LOCAL, fields);
    writeGroupsIfNeeded(json, groups, fields);
    writeScmAccountsIfNeeded(json, fields, user);
    writeTokensCount(json, tokensCount);
    json.endObject();
  }

  /**
   * A shortcut to {@link #write(JsonWriter, UserDto, Collection, Collection)} with preselected fields and without group information
   */
  public void write(JsonWriter json, @Nullable UserDto user) {
    if (user == null) {
      json.beginObject().endObject();
    } else {
      write(json, user, Collections.<String>emptySet(), CONCISE_FIELDS);
    }
  }

  private void writeGroupsIfNeeded(JsonWriter json, Collection<String> groups, @Nullable Collection<String> fields) {
    if (isFieldNeeded(FIELD_GROUPS, fields) && userSession.hasPermission(GlobalPermissions.SYSTEM_ADMIN)) {
      json.name(FIELD_GROUPS).beginArray();
      for (String groupName : groups) {
        json.value(groupName);
      }
      json.endArray();
    }
  }

  private static void writeScmAccountsIfNeeded(JsonWriter json, Collection<String> fields, UserDto user) {
    if (isFieldNeeded(FIELD_SCM_ACCOUNTS, fields)) {
      json.name(FIELD_SCM_ACCOUNTS)
        .beginArray()
        .values(user.getScmAccountsAsList())
        .endArray();
    }
  }

  private static void writeTokensCount(JsonWriter json, @Nullable Integer tokenCount) {
    if (tokenCount == null) {
      return;
    }

    json.prop(FIELD_TOKENS_COUNT, tokenCount);
  }
}
