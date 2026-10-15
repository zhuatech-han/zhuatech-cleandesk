// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 使用真实会话、CSRF及后台账号检查权限撤销和脱敏。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupport.TimeConfig.class)
@AutoConfigureMockMvc
@Transactional
class HttpSecurityTest extends TestSupport {
  @Autowired MockMvc mvc;

  @BeforeEach
  void setup() {
    fixture();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  MockHttpSession login(String name) throws Exception {
    var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    var token = jsonMapper.readValue(csrf.getResponse().getContentAsString(), Map.class);
    return (MockHttpSession)
        mvc.perform(
                post("/api/auth/login")
                    .session((MockHttpSession) csrf.getRequest().getSession())
                    .header(token.get("header").toString(), token.get("token"))
                    .contentType("application/json")
                    .content(
                        jsonMapper.writeValueAsString(
                            body("username", name, "password", "TestOnlyAdmin9Strong"))))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession();
  }

  @Test
  void anonymousAndMissingCsrfAreRejected() throws Exception {
    mvc.perform(get("/api/workspace")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/commands/booking.request")
                .session(login("qa-client"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void disabledAccountRevokesExistingSession() throws Exception {
    var session = login("qa-client");
    client.enabled = false;
    db.flush();
    mvc.perform(get("/api/workspace").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void changedPasswordHashRevokesExistingSession() throws Exception {
    var session = login("qa-client");
    client.passwordHash = "revoked-test-hash";
    db.flush();
    mvc.perform(get("/api/workspace").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void customerCannotReadOtherCustomersVisitOrAdminDirectory() throws Exception {
    var v = confirmed();
    SecurityContextHolder.clearContext();
    var session = login("qa-other-client");
    mvc.perform(get("/api/visits/" + v.id).session(session)).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
  }

  @Test
  void monthlyReportAndWorkerDetailKeepTheirDifferentScopes() throws Exception {
    var v = confirmed();
    run(v);
    auth("qa-client");
    cmd("visit.accept", body("id", v.id, "version", v.version));
    SecurityContextHolder.clearContext();
    var customer = login("qa-client");
    String report =
        mvc.perform(get("/api/reports?month=2026-10").session(customer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(report.contains("120.00"));
    String detail =
        mvc.perform(get("/api/visits/" + v.id).session(customer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(detail.contains("hourlyRate"));
    assertFalse(detail.contains("settlementId"));
    var w = login("qa-worker");
    String work =
        mvc.perform(get("/api/visits/" + v.id).session(w))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(work.contains("unitVisitPrice"));
    assertFalse(work.contains("\"charge\""));
    mvc.perform(get("/api/reports?month=2026-10").session(w)).andExpect(status().isForbidden());
  }
}
