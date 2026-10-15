// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 三端查询使用相同归属边界，客户与员工响应不含其他人员薪酬。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(readOnly = true)
public class QueryService {
  final Store db;
  final CleaningService core;
  final AccessService access;
  final tools.jackson.databind.json.JsonMapper json;

  /** 注入受限业务读模型。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public QueryService(
      Store db,
      CleaningService core,
      AccessService access,
      tools.jackson.databind.json.JsonMapper json) {
    this.db = db;
    this.core = core;
    this.access = access;
    this.json = json;
  }

  Map<String, Object> map(Object o) {
    return new LinkedHashMap<>(json.convertValue(o, Map.class));
  }

  boolean workerOnly() {
    return access.has("work") && !core.office() && !access.has("portal");
  }

  boolean canVisit(Visit v) {
    var b = db.get(Booking.class, v.bookingId);
    if (core.office()) return access.department(b.departmentId);
    if (access.has("portal")) return Objects.equals(core.portalCustomer().id, b.customerId);
    if (access.has("work")) {
      var w = core.workerSelf();
      return core.rounds(v.id).stream().anyMatch(r -> core.assignedTo(r, w.id));
    }
    return false;
  }

  Map<String, Object> summary(Visit v) {
    var b = db.get(Booking.class, v.bookingId);
    var c = db.get(Customer.class, b.customerId);
    Map<String, Object> out = map(v);
    out.put("charge", v.charge);
    out.putAll(
        CleaningService.row(
            "departmentId",
            b.departmentId,
            "timezone",
            db.get(Department.class, b.departmentId).zone,
            "customerId",
            c.id,
            "customerName",
            c.name,
            "serviceName",
            b.serviceName,
            "site",
            json.readValue(b.siteSnapshot, Map.class),
            "workersRequired",
            b.workersRequired,
            "orderStatus",
            b.status,
            "durationMinutes",
            b.durationMinutes));
    var rounds = core.rounds(v.id);
    var latest = rounds.isEmpty() ? null : rounds.getLast();
    if (latest != null) {
      out.put("roundId", latest.id);
      out.put("leaderId", latest.leaderId);
      out.put(
          "crew",
          core.assigned(latest.id).stream()
              .map(
                  a ->
                      CleaningService.row(
                          "id", a.workerId, "name", db.get(Worker.class, a.workerId).name))
              .toList());
    } else out.put("crew", List.of());
    if (workerOnly()) {
      out.remove("charge");
      out.put("isLeader", latest != null && latest.leaderId.equals(core.workerSelf().id));
    } else {
      var paid = core.paid(v.id);
      out.put("paid", paid);
      out.put("due", v.charge.subtract(paid).max(BigDecimal.ZERO));
      out.put("refundDue", paid.subtract(v.charge).max(BigDecimal.ZERO));
    }
    return out;
  }

  /** 门户、员工和办公室分别生成真实可见工作集合，不返回未授权账号目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object workspace() {
    var me = access.current();
    var rights = access.role().permissions;
    var customers =
        core.office()
            ? db.all(Customer.class).stream()
                .filter(c -> access.department(c.departmentId))
                .toList()
            : rights.contains("portal") ? List.of(core.portalCustomer()) : List.<Customer>of();
    Set<Long> customerIds = new HashSet<>();
    customers.forEach(c -> customerIds.add(c.id));
    Set<Long> depts = new HashSet<>();
    customers.forEach(c -> depts.add(c.departmentId));
    if (!rights.contains("portal")) depts.add(me.departmentId);
    var visits =
        db.all(Visit.class).stream()
            .filter(this::canVisit)
            .sorted(Comparator.comparing((Visit v) -> v.startsAt).thenComparing(v -> v.id))
            .toList();
    var bookings =
        db.all(Booking.class).stream()
            .filter(b -> customerIds.contains(b.customerId))
            .sorted(Comparator.comparing((Booking b) -> b.createdAt).reversed())
            .toList();
    var workers =
        db.all(Worker.class).stream()
            .filter(
                w ->
                    core.office()
                        ? access.department(w.departmentId)
                        : rights.contains("work") && w.accountId.equals(me.id))
            .toList();
    Set<Long> workerIds = new HashSet<>();
    workers.forEach(w -> workerIds.add(w.id));
    boolean payRates = rights.contains("workers") || rights.contains("finance") || workerOnly();
    var crew =
        workers.stream()
            .map(
                w -> {
                  var out = map(w);
                  if (!payRates) {
                    out.remove("hourlyRate");
                    out.remove("accountId");
                  }
                  return out;
                })
            .toList();
    var settlements =
        db.all(Settlement.class).stream()
            .filter(
                s ->
                    rights.contains("finance")
                        ? access.department(s.departmentId)
                        : workerOnly() && db.get(Worker.class, s.workerId).accountId.equals(me.id))
            .map(this::settlementSummary)
            .toList();
    return CleaningService.row(
        "profile",
        access.profile(),
        "customers",
        customers,
        "sites",
        db.all(Site.class).stream().filter(s -> customerIds.contains(s.customerId)).toList(),
        "services",
        db.all(ServiceRate.class).stream()
            .filter(
                s ->
                    rights.contains("portal")
                        ? s.enabled && depts.contains(s.departmentId)
                        : rights.contains("catalog")
                            ? access.department(s.departmentId)
                            : core.office() && access.department(s.departmentId))
            .toList(),
        "extras",
        db.all(ServiceExtra.class).stream()
            .filter(
                s ->
                    rights.contains("portal")
                        ? s.enabled && depts.contains(s.departmentId)
                        : core.office() && access.department(s.departmentId))
            .toList(),
        "workers",
        crew,
        "availability",
        db.all(Availability.class).stream().filter(a -> workerIds.contains(a.workerId)).toList(),
        "timeOff",
        db.all(TimeOff.class).stream().filter(a -> workerIds.contains(a.workerId)).toList(),
        "bookings",
        bookings,
        "visits",
        visits.stream().map(this::summary).toList(),
        "settlements",
        settlements,
        "categories",
        db.all(DictionaryEntry.class).stream()
            .filter(c -> c.enabled && c.type.equals("CATEGORY"))
            .toList(),
        "timezone",
        db.get(Department.class, me.departmentId).zone,
        "cancelHours",
        Integer.parseInt(
            db.query(SystemSetting.class, "from SystemSetting where code='cancelHours'")
                .getFirst()
                .value),
        "currency",
        db.query(SystemSetting.class, "from SystemSetting where code='currency'").getFirst().value,
        "organizations",
        core.office()
            ? db.all(Department.class).stream()
                .filter(d -> access.department(d.id))
                .map(d -> CleaningService.row("id", d.id, "name", d.name, "zone", d.zone))
                .toList()
            : List.of());
  }

  /** 一个订单的独立上门、报价快照和客户决定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object booking(Long id) {
    var b = core.visibleBooking(id);
    return CleaningService.row(
        "booking",
        b,
        "customer",
        core.customer(b.customerId),
        "visits",
        core.visits(id).stream().map(this::summary).toList());
  }

  /** 作业轮次与照片按实际指派筛选，员工看不到客户收付款及其他人结算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object visit(Long id) {
    var v = core.visibleVisit(id);
    var b = db.get(Booking.class, v.bookingId);
    boolean limited = workerOnly();
    Long wid = limited ? core.workerSelf().id : null;
    var rounds =
        core.rounds(id).stream()
            .filter(r -> !limited || core.assignedTo(r, wid))
            .map(
                r -> {
                  var out = map(r);
                  out.put(
                      "photos",
                      db.query(Photo.class, "from Photo where roundId=?1", r.id).stream()
                          .map(PhotoService::metadata)
                          .toList());
                  out.put(
                      "assignments",
                      core.assigned(r.id).stream()
                          .filter(a -> !limited || a.workerId.equals(wid))
                          .map(
                              a -> {
                                var x = map(a);
                                x.put("workerName", db.get(Worker.class, a.workerId).name);
                                if (!access.has("finance") && !access.has("workers") && !limited) {
                                  x.remove("hourlyRate");
                                  x.remove("cost");
                                  x.remove("settlementId");
                                }
                                return x;
                              })
                          .toList());
                  return out;
                })
            .toList();
    Map<String, Object> order = map(b);
    if (limited) {
      for (String field : List.of("unitVisitPrice", "discount", "quoteNote", "extrasSnapshot"))
        order.remove(field);
    }
    return CleaningService.row(
        "visit",
        summary(v),
        "booking",
        order,
        "rounds",
        rounds,
        "events",
        db
            .query(VisitEvent.class, "from VisitEvent where visitId=?1 order by createdAt,id", id)
            .stream()
            .filter(
                e ->
                    !limited
                        || Set.of(
                                "ASSIGNED",
                                "RESCHEDULED",
                                "STARTED",
                                "COMPLETED",
                                "ACCEPTED",
                                "OFFLINE_ACCEPTANCE_RECORDED",
                                "REWORK_REQUESTED",
                                "CANCELLED",
                                "SKIPPED")
                            .contains(e.action))
            .toList(),
        "money",
        limited
            ? List.of()
            : db.query(
                MoneyEntry.class, "from MoneyEntry where visitId=?1 order by createdAt,id", id));
  }

  Map<String, Object> settlementSummary(Settlement s) {
    var m = map(s);
    m.put("workerName", db.get(Worker.class, s.workerId).name);
    return m;
  }

  /** 人员本人可复核每笔工时成本，资金仍由外部完成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object settlement(Long id) {
    var s = core.visibleSettlement(id);
    var lines =
        db.query(Assignment.class, "from Assignment where settlementId=?1", id).stream()
            .map(
                a -> {
                  var r = db.get(WorkRound.class, a.roundId);
                  return CleaningService.row(
                      "id",
                      a.id,
                      "visitId",
                      r.visitId,
                      "roundNo",
                      r.roundNo,
                      "completedAt",
                      r.completedAt,
                      "actualMinutes",
                      a.actualMinutes,
                      "hourlyRate",
                      a.hourlyRate,
                      "cost",
                      a.cost);
                })
            .toList();
    return CleaningService.row("settlement", settlementSummary(s), "lines", lines);
  }

  /** 月度对账按部署组织时区归属，明确区分应收、登记到账及待退款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object report(String month, Long customerId) {
    YearMonth ym = YearMonth.parse(month);
    boolean portal = access.has("portal");
    if (!portal) access.require("reports");
    Long own = portal ? core.portalCustomer().id : customerId;
    if (own != null) core.customer(own);
    var rows =
        db.all(Visit.class).stream()
            .filter(this::canVisit)
            .filter(
                v -> {
                  var b = db.get(Booking.class, v.bookingId);
                  return b.status.equals("CONFIRMED")
                      && (own == null || own.equals(b.customerId))
                      && YearMonth.from(
                              v.startsAt.atZone(
                                  ZoneId.of(db.get(Department.class, b.departmentId).zone)))
                          .equals(ym);
                })
            .map(this::summary)
            .toList();
    BigDecimal charge = BigDecimal.ZERO,
        paid = BigDecimal.ZERO,
        due = BigDecimal.ZERO,
        refund = BigDecimal.ZERO;
    for (var x : rows) {
      charge = charge.add((BigDecimal) x.get("charge"));
      paid = paid.add((BigDecimal) x.get("paid"));
      due = due.add((BigDecimal) x.get("due"));
      refund = refund.add((BigDecimal) x.get("refundDue"));
    }
    return CleaningService.row(
        "month",
        month,
        "rows",
        rows,
        "charge",
        charge,
        "paid",
        paid,
        "due",
        due,
        "refundDue",
        refund,
        "note",
        "收付为外部凭证登记；按上门日期归月，不是银行对账或税务发票。");
  }

  /** 审计读取同组织，不暴露密码、照片和完整请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(e -> access.department(e.departmentId))
        .sorted(Comparator.comparing((AuditEvent e) -> e.createdAt).reversed())
        .toList();
  }
}
