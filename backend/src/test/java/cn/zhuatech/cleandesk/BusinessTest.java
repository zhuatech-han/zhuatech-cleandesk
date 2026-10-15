// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 实际事务中的周期、派工、照片、客户验收和人员结算回归。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupport.TimeConfig.class)
@Transactional
class BusinessTest extends TestSupport {
  @BeforeEach
  void prepare() {
    fixture();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void denied(String code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(Problem.class, action).getMessage());
  }

  @Test
  void fourWeeklyVisitsHaveStableSeparateIdentity() {
    var b = book(4, 1);
    var rows = core.visits(b.id);
    assertEquals(4, rows.size());
    assertEquals(
        7, java.time.Duration.between(rows.get(0).startsAt, rows.get(1).startsAt).toDays());
    assertEquals(new BigDecimal("120.00"), b.unitVisitPrice);
    assertEquals(4, rows.stream().map(v -> v.id).distinct().count());
  }

  @Test
  void quoteSnapshotSurvivesRateAndSiteEdits() {
    var b = book(1, 0);
    auth("admin");
    rate.unitPrice = new BigDecimal("999.00");
    site.address = "changed QA";
    db.flush();
    assertEquals(new BigDecimal("120.00"), b.unitVisitPrice);
    assertTrue(b.siteSnapshot.contains("QA simulated address"));
    assertFalse(b.siteSnapshot.contains("changed QA"));
  }

  @Test
  void exactCommandReplayDoesNotCreateAnotherBooking() {
    auth("qa-client");
    var request =
        body(
            "siteId",
            site.id,
            "serviceId",
            rate.id,
            "units",
            "1",
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
    assertEquals(cmd("booking.request", request), cmd("booking.request", request));
    assertEquals(1, db.all(Booking.class).size());
  }

  @Test
  void changedCommandPayloadWithSameKeyIsRejected() {
    auth("qa-client");
    var b =
        body(
            "siteId",
            site.id,
            "serviceId",
            rate.id,
            "units",
            "1",
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
    cmd("booking.request", b);
    b.put("occurrences", 2);
    b.put("intervalWeeks", 1);
    denied("IDEMPOTENCY_CONFLICT", () -> cmd("booking.request", b));
  }

  @Test
  void customerCannotReadAnotherCustomersOrder() {
    var b = book(1, 0);
    auth("qa-other-client");
    denied("OUT_OF_SCOPE", () -> queries.booking(b.id));
  }

  @Test
  void workerCannotReadUnassignedVisit() {
    var v = confirmed();
    auth("qa-worker");
    denied("OUT_OF_SCOPE", () -> queries.visit(v.id));
  }

  @Test
  void quoteCannotBeConfirmedByOfficeWithoutExternalEvidence() {
    var b = book(1, 0);
    auth("admin");
    cmd("booking.quote", body("id", b.id, "version", b.version, "discount", "0", "note", ""));
    denied("INVALID_INPUT", () -> cmd("booking.confirm", body("id", b.id, "version", b.version)));
  }

  @Test
  void sameWorkerCannotBeAssignedToTwoOverlappingJobs() {
    var a = confirmed();
    var b = confirmed();
    assign(a, worker);
    denied("TIME_CONFLICT", () -> assign(b, worker));
  }

  @Test
  void rescheduleCancelsOldRoundAndFreesItsSlot() {
    var a = confirmed();
    assign(a, worker);
    var old = core.currentRound(a);
    auth("qa-client");
    cmd(
        "visit.reschedule",
        body(
            "id",
            a.id,
            "version",
            a.version,
            "localStart",
            "2026-10-17T11:00",
            "reason",
            "QA change"));
    assertEquals("CANCELLED", old.status);
    assertEquals("READY", a.status);
    var b = confirmed();
    assign(b, worker);
    assertEquals("ASSIGNED", b.status);
  }

  @Test
  void cancellationKeepsPaidLedgerAndRequiresActualRefund() {
    var v = confirmed();
    auth("admin");
    cmd(
        "money.record",
        body("visitId", v.id, "kind", "RECEIPT", "amount", "50", "reference", "QA-PAY-1"));
    auth("qa-client");
    cmd("visit.cancel", body("id", v.id, "version", v.version, "reason", "QA cancel"));
    assertEquals(new BigDecimal("50.00"), core.paid(v.id));
    assertEquals(new BigDecimal("0.00"), v.charge);
    auth("admin");
    cmd(
        "money.record",
        body("visitId", v.id, "kind", "REFUND", "amount", "50", "reference", "QA-REFUND-1"));
    assertEquals(new BigDecimal("0.00"), core.paid(v.id));
  }

  @Test
  void overpaymentIsRejected() {
    var v = confirmed();
    auth("admin");
    denied(
        "PAYMENT_EXCEEDS_DUE",
        () ->
            cmd(
                "money.record",
                body(
                    "visitId",
                    v.id,
                    "kind",
                    "RECEIPT",
                    "amount",
                    "120.01",
                    "reference",
                    "QA-OVERPAY")));
  }

  @Test
  void beforePhotoIsRequiredForStarting() {
    var v = confirmed();
    assign(v, worker);
    clock.value = v.startsAt;
    auth("qa-worker");
    denied(
        "BEFORE_PHOTO_REQUIRED", () -> cmd("visit.start", body("id", v.id, "version", v.version)));
  }

  @Test
  void uploadedImageIsProtectedAndDuplicateDigestIsSingleRecord() throws Exception {
    var v = confirmed();
    assign(v, worker);
    auth("qa-worker");
    var round = core.currentRound(v);
    photo(round, "BEFORE");
    photo(round, "BEFORE");
    assertEquals(1, db.all(Photo.class).size());
    Long pid = db.all(Photo.class).getFirst().id;
    assertArrayEquals(image(), photos.read(pid));
    auth("qa-other-client");
    denied("OUT_OF_SCOPE", () -> photos.read(pid));
  }

  @Test
  void actualWorkMustBeAcceptedBeforeSettlement() throws Exception {
    var v = confirmed();
    run(v);
    auth("admin");
    denied(
        "NOTHING_TO_SETTLE",
        () ->
            cmd(
                "settlement.create",
                body("workerId", worker.id, "fromDate", "2026-10-01", "toDate", "2026-10-31")));
  }

  @Test
  void completedAcceptedWorkIsSettledOnceAndNeedsWorkerAcknowledgement() throws Exception {
    var v = confirmed();
    run(v);
    auth("qa-client");
    cmd("visit.accept", body("id", v.id, "version", v.version, "note", "QA accepted"));
    auth("admin");
    var s =
        db.get(
            Settlement.class,
            created(
                "settlement.create",
                body("workerId", worker.id, "fromDate", "2026-10-01", "toDate", "2026-10-31")));
    assertEquals(new BigDecimal("30.00"), s.amount);
    auth("qa-worker");
    cmd("settlement.confirm", body("id", s.id, "version", s.version));
    auth("admin");
    cmd("settlement.pay", body("id", s.id, "version", s.version, "reference", "QA-STAFF-PAID"));
    assertEquals("PAID", s.status);
    denied(
        "NOTHING_TO_SETTLE",
        () ->
            cmd(
                "settlement.create",
                body("workerId", worker.id, "fromDate", "2026-10-01", "toDate", "2026-10-31")));
  }

  @Test
  void reworkKeepsOriginalRoundAndDoesNotAddCustomerCharge() throws Exception {
    var v = confirmed();
    run(v);
    var original = core.currentRound(v);
    auth("qa-client");
    cmd(
        "visit.rework",
        body(
            "id",
            v.id,
            "version",
            v.version,
            "localStart",
            "2026-10-17T10:00",
            "reason",
            "QA missed area"));
    assertEquals("REJECTED", original.status);
    run(v);
    assertEquals(2, core.rounds(v.id).size());
    assertEquals(new BigDecimal("120.00"), v.charge);
    auth("qa-client");
    cmd("visit.accept", body("id", v.id, "version", v.version));
    auth("admin");
    var s =
        db.get(
            Settlement.class,
            created(
                "settlement.create",
                body("workerId", worker.id, "fromDate", "2026-10-01", "toDate", "2026-10-31")));
    assertEquals(new BigDecimal("60.00"), s.amount);
  }

  @Test
  void cancelledUnpaidStatementReleasesItsExactLines() throws Exception {
    var v = confirmed();
    run(v);
    auth("qa-client");
    cmd("visit.accept", body("id", v.id, "version", v.version));
    auth("admin");
    var body = body("workerId", worker.id, "fromDate", "2026-10-01", "toDate", "2026-10-31");
    var s = db.get(Settlement.class, created("settlement.create", body));
    cmd("settlement.cancel", body("id", s.id, "version", s.version, "reason", "QA replace"));
    var replacement = db.get(Settlement.class, created("settlement.create", body));
    assertEquals(s.amount, replacement.amount);
    assertNotEquals(s.id, replacement.id);
  }

  @Test
  void personnelPayIsAbsentFromCustomerDetail() throws Exception {
    var v = confirmed();
    run(v);
    auth("qa-client");
    String json = jsonMapper.writeValueAsString(queries.visit(v.id));
    assertFalse(json.contains("hourlyRate"));
    assertFalse(json.contains("\"cost\""));
    assertFalse(json.contains("passwordHash"));
  }

  @Test
  void unconfirmedQuoteIsNotAnAccountsReceivable() {
    book(1, 0);
    auth("qa-client");
    var report = (Map<?, ?>) queries.report("2026-10", null);
    assertEquals(0, ((List<?>) report.get("rows")).size());
    assertEquals(0, ((BigDecimal) report.get("charge")).signum());
  }

  @Test
  void workerHistoryDoesNotLeakQuoteOrCustomerPaymentNotes() throws Exception {
    var v = confirmed();
    run(v);
    auth("admin");
    cmd(
        "money.record",
        body(
            "visitId",
            v.id,
            "kind",
            "RECEIPT",
            "amount",
            "10",
            "reference",
            "QA-PRIVATE-LEDGER",
            "note",
            "QA private finance note"));
    auth("qa-worker");
    var data = (Map<?, ?>) queries.visit(v.id);
    var events = (List<VisitEvent>) data.get("events");
    assertTrue(
        events.stream()
            .noneMatch(
                e ->
                    Set.of("QUOTED", "RECEIPT", "REFUND", "CUSTOMER_CONFIRMED")
                        .contains(e.action)));
    assertTrue(events.stream().anyMatch(e -> e.action.equals("COMPLETED")));
  }
}
