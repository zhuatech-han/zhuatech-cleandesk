// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 独立连接同时提交，验证幂等和人员排他；测试库与常规用例隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest(
    properties =
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:cleandesk_concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=0;LOCK_TIMEOUT=30000}")
@ActiveProfiles("test")
@Import(TestSupport.TimeConfig.class)
@DirtiesContext
class ConcurrencyTest extends TestSupport {
  @Autowired PlatformTransactionManager tx;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @AfterEach
  void removeCommittedFixtures() {
    // Only the dedicated test database; each committed concurrency fixture is removed before other
    // test classes.
    for (String table :
        List.of(
            "photo_data",
            "work_photo",
            "visit_event",
            "money_entry",
            "work_assignment",
            "worker_settlement",
            "work_round",
            "clean_visit",
            "clean_booking",
            "clean_site",
            "clean_customer",
            "worker_availability",
            "worker_time_off",
            "clean_worker",
            "service_extra",
            "service_rate",
            "command_receipt",
            "audit_event")) jdbc.update("delete from " + table);
    jdbc.update("delete from account where username like 'qa-%'");
  }

  Object transaction(String actor, Supplier<Object> task) {
    return new TransactionTemplate(tx)
        .execute(
            t -> {
              try {
                auth(actor);
                return task.get();
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
  }

  List<Object> race(String actor, java.util.function.IntFunction<Object> task) throws Exception {
    var ready = new CountDownLatch(2);
    var go = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Future<Object>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        int n = i;
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  try {
                    return transaction(actor, () -> task.apply(n));
                  } catch (Problem p) {
                    return p.getMessage();
                  }
                }));
      }
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      go.countDown();
      List<Object> results = new ArrayList<>();
      for (var f : futures) results.add(f.get(30, TimeUnit.SECONDS));
      return results;
    }
  }

  @Test
  void duplicateRequestsAndConflictingAssignmentsRemainAtomic() throws Exception {
    transaction(
        "admin",
        () -> {
          fixture();
          return null;
        });
    var body =
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
            1,
            "intervalWeeks",
            0,
            "requestKey",
            UUID.randomUUID().toString());
    var repeated = race("qa-client", i -> core.command("booking.request", body));
    assertEquals(repeated.get(0), repeated.get(1));
    Long[] vids =
        (Long[])
            transaction(
                "admin",
                () -> {
                  var a = confirmed();
                  var b = confirmed();
                  return new Long[] {a.id, b.id};
                });
    var assigned =
        race(
            "admin",
            i -> {
              var v = db.get(Visit.class, vids[i]);
              return cmd(
                  "visit.assign",
                  body(
                      "id",
                      v.id,
                      "version",
                      v.version,
                      "workerIds",
                      List.of(worker.id),
                      "leaderId",
                      worker.id,
                      "note",
                      "QA simultaneous dispatch"));
            });
    assertEquals(1, assigned.stream().filter(Map.class::isInstance).count());
    assertEquals(1, assigned.stream().filter("TIME_CONFLICT"::equals).count());
    transaction(
        "admin",
        () -> {
          assertEquals(1, db.all(WorkRound.class).size());
          return null;
        });
  }
}
