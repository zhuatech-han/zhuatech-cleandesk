// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 独立数据库与时钟测试夹具，不进入生产构建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class TestSupport {
  @Autowired Store db;
  @Autowired CleaningService core;
  @Autowired AdminService admin;
  @Autowired QueryService queries;
  @Autowired PhotoService photos;
  @Autowired TestClock clock;
  @Autowired tools.jackson.databind.json.JsonMapper jsonMapper;
  Customer customer;
  Site site;
  ServiceRate rate;
  Worker worker, otherWorker;
  Account client, otherClient;

  static class TestClock extends Clock {
    Instant value = Instant.parse("2026-10-15T02:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId z) {
      return this;
    }

    @Override
    public Instant instant() {
      return value;
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  void auth(String username) {
    var a = db.query(Account.class, "from Account where username=?1", username).getFirst();
    var token = UsernamePasswordAuthenticationToken.authenticated(username, null, List.of());
    token.setDetails(a.passwordHash);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  Account account(String name, String role) {
    var a = new Account();
    a.username = name;
    a.displayName = "QA " + name;
    a.passwordHash =
        db.query(Account.class, "from Account where username='admin'").getFirst().passwordHash;
    a.departmentId = 1L;
    a.roleId = db.query(AccessRole.class, "from AccessRole where name=?1", role).getFirst().id;
    db.save(a);
    return a;
  }

  Map<String, Object> body(Object... kv) {
    Map<String, Object> b = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) b.put((String) kv[i], kv[i + 1]);
    return b;
  }

  Map<?, ?> cmd(String action, Map<String, Object> b) {
    var copy = new LinkedHashMap<>(b);
    copy.putIfAbsent("requestKey", UUID.randomUUID().toString());
    return (Map<?, ?>) core.command(action, copy);
  }

  Long created(String a, Map<String, Object> b) {
    return ((Number) cmd(a, b).get("id")).longValue();
  }

  void fixture() {
    clock.value = Instant.parse("2026-10-15T02:00:00Z");
    auth("admin");
    client = account("qa-client", "客户");
    otherClient = account("qa-other-client", "客户");
    var a = account("qa-worker", "保洁人员");
    var b = account("qa-other-worker", "保洁人员");
    customer =
        db.get(
            Customer.class,
            created(
                "customer.save",
                body(
                    "accountId",
                    client.id,
                    "name",
                    "QA householder",
                    "contact",
                    "QA only",
                    "billingType",
                    "MONTHLY",
                    "enabled",
                    true)));
    site =
        db.get(
            Site.class,
            created(
                "site.save",
                body(
                    "customerId",
                    customer.id,
                    "name",
                    "QA living room",
                    "address",
                    "QA simulated address",
                    "contact",
                    "QA",
                    "instructions",
                    "QA access notes",
                    "enabled",
                    true)));
    rate =
        db.get(
            ServiceRate.class,
            created(
                "service.save",
                body(
                    "name",
                    "QA hourly cleaning",
                    "nameEn",
                    "QA cleaning",
                    "categoryCode",
                    "HOME",
                    "unit",
                    "HOURLY",
                    "basePrice",
                    "20.00",
                    "unitPrice",
                    "100.00",
                    "minutesPerUnit",
                    60,
                    "workersRequired",
                    1,
                    "enabled",
                    true)));
    worker = worker(a);
    otherWorker = worker(b);
    db.flush();
  }

  Worker worker(Account a) {
    var w =
        db.get(
            Worker.class,
            created(
                "worker.save",
                body(
                    "accountId",
                    a.id,
                    "name",
                    a.displayName,
                    "hourlyRate",
                    "30.00",
                    "enabled",
                    true)));
    for (int day = 1; day <= 7; day++)
      cmd(
          "availability.save",
          body("workerId", w.id, "weekday", day, "startTime", "00:00", "endTime", "23:59"));
    return w;
  }

  Booking book(int count, int weeks) {
    auth("qa-client");
    return db.get(
        Booking.class,
        created(
            "booking.request",
            body(
                "siteId",
                site.id,
                "serviceId",
                rate.id,
                "units",
                "1.00",
                "extraIds",
                List.of(),
                "localStart",
                "2026-10-16T10:00",
                "occurrences",
                count,
                "intervalWeeks",
                weeks,
                "note",
                "QA")));
  }

  Visit confirmed() {
    var b = book(1, 0);
    auth("admin");
    cmd(
        "booking.quote",
        body("id", b.id, "version", b.version, "discount", "0.00", "note", "QA quote"));
    auth("qa-client");
    cmd("booking.confirm", body("id", b.id, "version", b.version));
    return core.visits(b.id).getFirst();
  }

  void assign(Visit v, Worker w) {
    auth("admin");
    cmd(
        "visit.assign",
        body(
            "id",
            v.id,
            "version",
            v.version,
            "workerIds",
            List.of(w.id),
            "leaderId",
            w.id,
            "note",
            "QA dispatch"));
  }

  void run(Visit v) throws Exception {
    assign(v, worker);
    clock.value = v.startsAt;
    auth("qa-worker");
    var r = core.currentRound(v);
    photo(r, "BEFORE");
    cmd("visit.start", body("id", v.id, "version", v.version));
    photo(r, "AFTER");
    cmd(
        "visit.complete",
        body(
            "id",
            v.id,
            "version",
            v.version,
            "minutes",
            Map.of(worker.id.toString(), 60),
            "note",
            "QA cleaned"));
  }

  byte[] image() throws Exception {
    var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  void photo(WorkRound r, String phase) throws Exception {
    photos.upload(
        r.id,
        phase,
        "QA",
        new org.springframework.mock.web.MockMultipartFile("file", "qa.png", "image/png", image()));
  }
}
