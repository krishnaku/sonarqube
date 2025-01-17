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
package org.sonar.server.rule.ws;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.rule.RuleDao;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.rule.RuleDto.Format;
import org.sonar.db.rule.RuleParamDto;
import org.sonar.db.rule.RuleTesting;
import org.sonar.server.rule.NewRule;
import org.sonar.server.rule.RuleService;
import org.sonar.server.tester.ServerTester;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;

import static com.google.common.collect.Sets.newHashSet;

public class ShowActionMediumTest {

  @ClassRule
  public static ServerTester tester = new ServerTester().withEsIndexes();

  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.forServerTester(tester).login()
    .setGlobalPermissions(GlobalPermissions.QUALITY_PROFILE_ADMIN);

  WsTester wsTester;

  RuleService ruleService;
  RuleDao ruleDao;
  DbSession session;

  @Before
  public void setUp() {
    tester.clearDbAndIndexes();
    wsTester = tester.get(WsTester.class);
    ruleService = tester.get(RuleService.class);
    ruleDao = tester.get(RuleDao.class);
    session = tester.get(DbClient.class).openSession(false);
  }

  @After
  public void after() {
    session.close();
  }

  @Test
  public void show_rule() throws Exception {
    RuleDto ruleDto = RuleTesting.newDto(RuleKey.of("java", "S001"))
      .setName("Rule S001")
      .setDescription("Rule S001 <b>description</b>")
      .setDescriptionFormat(Format.HTML)
      .setSeverity(Severity.MINOR)
      .setStatus(RuleStatus.BETA)
      .setConfigKey("InternalKeyS001")
      .setLanguage("xoo")
      .setTags(newHashSet("tag1", "tag2"))
      .setSystemTags(newHashSet("systag1", "systag2"))
      .setType(RuleType.BUG);
    ruleDao.insert(session, ruleDto);
    RuleParamDto param = RuleParamDto.createFor(ruleDto).setName("regex").setType("STRING").setDescription("Reg *exp*").setDefaultValue(".*");
    ruleDao.insertRuleParam(session, ruleDto, param);
    session.commit();
    session.clearCache();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", ruleDto.getKey().toString());
    request.execute().assertJson(getClass(), "show_rule.json");
  }

  @Test
  public void show_rule_with_default_debt_infos() throws Exception {
    RuleDto ruleDto = RuleTesting.newDto(RuleKey.of("java", "S001"))
      .setName("Rule S001")
      .setDescription("Rule S001 <b>description</b>")
      .setSeverity(Severity.MINOR)
      .setStatus(RuleStatus.BETA)
      .setConfigKey("InternalKeyS001")
      .setLanguage("xoo")
      .setDefaultRemediationFunction("LINEAR_OFFSET")
      .setDefaultRemediationGapMultiplier("5d")
      .setDefaultRemediationBaseEffort("10h")
      .setRemediationFunction(null)
      .setRemediationGapMultiplier(null)
      .setRemediationBaseEffort(null);
    ruleDao.insert(session, ruleDto);
    session.commit();
    session.clearCache();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", ruleDto.getKey().toString());
    WsTester.Result response = request.execute();

    response.assertJson(getClass(), "show_rule_with_default_debt_infos.json");
  }

  @Test
  public void show_rule_with_overridden_debt() throws Exception {
    RuleDto ruleDto =
      RuleTesting.newDto(RuleKey.of("java", "S001"))
        .setName("Rule S001")
        .setDescription("Rule S001 <b>description</b>")
        .setSeverity(Severity.MINOR)
        .setStatus(RuleStatus.BETA)
        .setConfigKey("InternalKeyS001")
        .setLanguage("xoo")
        .setDefaultRemediationFunction(null)
        .setDefaultRemediationGapMultiplier(null)
        .setDefaultRemediationBaseEffort(null)
        .setRemediationFunction("LINEAR_OFFSET")
        .setRemediationGapMultiplier("5d")
        .setRemediationBaseEffort("10h");
    ruleDao.insert(session, ruleDto);
    session.commit();
    session.clearCache();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", ruleDto.getKey().toString());
    request.execute().assertJson(getClass(), "show_rule_with_overridden_debt_infos.json");
  }

  @Test
  public void show_rule_with_default_and_overridden_debt_infos() throws Exception {
    RuleDto ruleDto = RuleTesting.newDto(RuleKey.of("java", "S001"))
      .setName("Rule S001")
      .setDescription("Rule S001 <b>description</b>")
      .setSeverity(Severity.MINOR)
      .setStatus(RuleStatus.BETA)
      .setConfigKey("InternalKeyS001")
      .setLanguage("xoo")
      .setDefaultRemediationFunction("LINEAR")
      .setDefaultRemediationGapMultiplier("5min")
      .setDefaultRemediationBaseEffort(null)
      .setRemediationFunction("LINEAR_OFFSET")
      .setRemediationGapMultiplier("5d")
      .setRemediationBaseEffort("10h");
    ruleDao.insert(session, ruleDto);
    session.commit();
    session.clearCache();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", ruleDto.getKey().toString());
    request.execute().assertJson(getClass(), "show_rule_with_default_and_overridden_debt_infos.json");
  }

  @Test
  public void show_rule_with_no_default_and_no_overridden_debt() throws Exception {
    RuleDto ruleDto = RuleTesting.newDto(RuleKey.of("java", "S001"))
      .setName("Rule S001")
      .setDescription("Rule S001 <b>description</b>")
      .setDescriptionFormat(Format.HTML)
      .setSeverity(Severity.MINOR)
      .setStatus(RuleStatus.BETA)
      .setConfigKey("InternalKeyS001")
      .setLanguage("xoo")
      .setDefaultRemediationFunction(null)
      .setDefaultRemediationGapMultiplier(null)
      .setDefaultRemediationBaseEffort(null)
      .setRemediationFunction(null)
      .setRemediationGapMultiplier(null)
      .setRemediationBaseEffort(null);
    ruleDao.insert(session, ruleDto);
    session.commit();
    session.clearCache();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", ruleDto.getKey().toString());
    request.execute().assertJson(getClass(), "show_rule_with_no_default_and_no_overridden_debt.json");
  }

  @Test
  public void encode_html_description_of_custom_rule() throws Exception {
    // Template rule
    RuleDto templateRule = RuleTesting.newTemplateRule(RuleKey.of("java", "S001"));
    ruleDao.insert(session, templateRule);
    session.commit();

    // Custom rule
    NewRule customRule = NewRule.createForCustomRule("MY_CUSTOM", templateRule.getKey())
      .setName("My custom")
      .setSeverity(Severity.MINOR)
      .setStatus(RuleStatus.READY)
      .setMarkdownDescription("<div>line1\nline2</div>");
    RuleKey customRuleKey = ruleService.create(customRule);
    session.clearCache();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", customRuleKey.toString());
    request.execute().assertJson(getClass(), "encode_html_description_of_custom_rule.json");
  }

  @Test
  public void encode_html_description_of_manual_rule() throws Exception {
    // Manual rule
    NewRule manualRule = NewRule.createForManualRule("MY_MANUAL")
      .setName("My manual")
      .setSeverity(Severity.MINOR)
      .setMarkdownDescription("<div>line1\nline2</div>");
    RuleKey customRuleKey = ruleService.create(manualRule);
    session.clearCache();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", customRuleKey.toString());
    request.execute().assertJson(getClass(), "encode_html_description_of_manual_rule.json");
  }

  @Test
  public void show_deprecated_rule_rem_function_fields() throws Exception {
    RuleDto ruleDto = RuleTesting.newDto(RuleKey.of("java", "S001"))
      .setName("Rule S001")
      .setDescription("Rule S001 <b>description</b>")
      .setSeverity(Severity.MINOR)
      .setStatus(RuleStatus.BETA)
      .setConfigKey("InternalKeyS001")
      .setLanguage("xoo")
      .setDefaultRemediationFunction("LINEAR_OFFSET")
      .setDefaultRemediationGapMultiplier("6d")
      .setDefaultRemediationBaseEffort("11h")
      .setRemediationFunction("LINEAR_OFFSET")
      .setRemediationGapMultiplier("5d")
      .setRemediationBaseEffort("10h");
    ruleDao.insert(session, ruleDto);
    session.commit();

    WsTester.TestRequest request = wsTester.newGetRequest("api/rules", "show")
      .setParam("key", ruleDto.getKey().toString());
    request.execute().assertJson(getClass(), "show_deprecated_rule_rem_function_fields.json");
  }

}
