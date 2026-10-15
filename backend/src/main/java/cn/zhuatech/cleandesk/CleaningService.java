// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 保洁预约、独立上门、人员冲突及精确结算的事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class CleaningService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final JsonMapper json;

  /** 注入持久化、身份及UTC时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CleaningService(
      Store db, AccessService access, AdminService admin, Clock clock, JsonMapper json) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
    this.json = json;
  }

  static Map<String, Object> row(Object... values) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) r.put((String) values[i], values[i + 1]);
    return r;
  }

  static String text(Map<String, Object> b, String k, int max) {
    return Rules.text(b.get(k), max, true);
  }

  static String note(Map<String, Object> b, String k) {
    return Rules.paragraph(b.get(k), 1000, false);
  }

  static Long id(Map<String, Object> b, String k) {
    return Rules.id(b.get(k));
  }

  static BigDecimal money(Map<String, Object> b, String k, boolean zero) {
    return Pricing.decimal(b.get(k), zero);
  }

  static void version(long v, Map<String, Object> b) {
    Rules.check(v == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  boolean office() {
    return access.role().permissions.stream()
        .anyMatch(Set.of("bookings", "dispatch", "finance", "reports", "customers")::contains);
  }

  void scope(Long dept, String code) {
    access.library(dept, code);
    Rules.check(db.get(Department.class, dept).enabled, "DEPARTMENT_DISABLED");
  }

  Customer customer(Long cid) {
    var c = db.get(Customer.class, cid);
    if (office()) {
      Rules.check(access.department(c.departmentId), "OUT_OF_SCOPE");
    } else {
      access.require("portal");
      Rules.check(Objects.equals(c.accountId, access.current().id) && c.enabled, "OUT_OF_SCOPE");
    }
    return c;
  }

  Customer portalCustomer() {
    access.require("portal");
    var found = db.query(Customer.class, "from Customer where accountId=?1", access.current().id);
    Rules.check(found.size() == 1 && found.getFirst().enabled, "CUSTOMER_NOT_LINKED");
    return customer(found.getFirst().id);
  }

  Worker workerSelf() {
    access.require("work");
    var list = db.query(Worker.class, "from Worker where accountId=?1", access.current().id);
    Rules.check(list.size() == 1 && list.getFirst().enabled, "WORKER_NOT_LINKED");
    var w = list.getFirst();
    Rules.check(w.departmentId.equals(access.current().departmentId), "OUT_OF_SCOPE");
    return w;
  }

  List<Visit> visits(Long booking) {
    return db.query(Visit.class, "from Visit where bookingId=?1 order by occurrenceNo", booking);
  }

  List<WorkRound> rounds(Long visit) {
    return db.query(WorkRound.class, "from WorkRound where visitId=?1 order by roundNo", visit);
  }

  List<Assignment> assigned(Long round) {
    return db.query(Assignment.class, "from Assignment where roundId=?1", round);
  }

  WorkRound currentRound(Visit v) {
    var list = rounds(v.id);
    Rules.check(!list.isEmpty(), "NOT_ASSIGNED");
    return list.getLast();
  }

  boolean assignedTo(WorkRound r, Long wid) {
    return !r.status.equals("CANCELLED")
        && assigned(r.id).stream().anyMatch(a -> a.workerId.equals(wid));
  }

  /** 每个详情、照片和列表共用的访问边界；不靠浏览器隐藏按钮。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Visit visibleVisit(Long vid) {
    var v = db.get(Visit.class, vid);
    var b = db.get(Booking.class, v.bookingId);
    if (office() || access.has("portal")) customer(b.customerId);
    else {
      var w = workerSelf();
      Rules.check(rounds(v.id).stream().anyMatch(r -> assignedTo(r, w.id)), "OUT_OF_SCOPE");
    }
    return v;
  }

  Booking visibleBooking(Long bid) {
    var b = db.get(Booking.class, bid);
    customer(b.customerId);
    return b;
  }

  void dispatch(Visit v) {
    var b = db.get(Booking.class, v.bookingId);
    scope(b.departmentId, "dispatch");
  }

  void event(Visit v, String action, String n) {
    var e = new VisitEvent();
    e.visitId = v.id;
    e.action = action;
    e.note = n;
    e.actor = access.current().displayName;
    e.createdAt = clock.instant();
    db.save(e);
  }

  Object canonical(Object value) {
    if (value instanceof Map<?, ?> m) {
      Map<String, Object> t = new TreeMap<>();
      m.forEach((k, v) -> t.put(String.valueOf(k), canonical(v)));
      return t;
    }
    if (value instanceof List<?> l) return l.stream().map(this::canonical).toList();
    return value;
  }

  /** 幂等命令只存请求摘要和对象标识，绝不持久化密码或整个请求正文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(String action, Map<String, Object> body) {
    admin.lock();
    var actor = access.current();
    String key = text(body, "requestKey", 36);
    try {
      Rules.check(UUID.fromString(key).toString().equals(key), "INVALID_INPUT");
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "INVALID_INPUT");
    }
    String hash = sha(json.writeValueAsBytes(canonical(body)));
    var existing =
        db.query(
            CommandReceipt.class,
            "from CommandReceipt where accountId=?1 and requestKey=?2",
            actor.id,
            key);
    if (!existing.isEmpty()) {
      var x = existing.getFirst();
      Rules.check(x.action.equals(action) && x.fingerprint.equals(hash), "IDEMPOTENCY_CONFLICT");
      return json.readValue(x.resultJson, Map.class);
    }
    Object entity =
        switch (action) {
          case "customer.save" -> saveCustomer(body);
          case "site.save" -> saveSite(body);
          case "service.save" -> saveService(body);
          case "extra.save" -> saveExtra(body);
          case "worker.save" -> saveWorker(body);
          case "availability.save" -> saveAvailability(body);
          case "availability.remove" -> removeAvailability(body);
          case "timeoff.save" -> saveTimeOff(body);
          case "booking.request" -> requestBooking(body);
          case "booking.quote" -> quote(body);
          case "booking.confirm" -> confirm(body);
          case "visit.cancel" -> cancel(body);
          case "visit.reschedule" -> reschedule(body);
          case "visit.assign" -> assign(body);
          case "visit.start" -> start(body);
          case "visit.complete" -> complete(body);
          case "visit.accept" -> accept(body);
          case "visit.rework" -> rework(body);
          case "money.record" -> recordMoney(body);
          case "settlement.create" -> createSettlement(body);
          case "settlement.confirm" -> confirmSettlement(body);
          case "settlement.pay" -> paySettlement(body);
          case "settlement.cancel" -> cancelSettlement(body);
          case "admin.save" ->
              admin.save(
                  text(body, "kind", 30), body.get("id") == null ? null : id(body, "id"), body);
          default -> throw new Problem(404, "NOT_FOUND");
        };
    db.flush();
    Map<String, Object> result;
    try {
      result = row("id", entity.getClass().getField("id").get(entity));
      try {
        result.put("version", entity.getClass().getField("version").get(entity));
      } catch (NoSuchFieldException ignored) {
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Invalid command result");
    }
    var receipt = new CommandReceipt();
    receipt.accountId = actor.id;
    receipt.requestKey = key;
    receipt.action = action;
    receipt.fingerprint = hash;
    receipt.resultJson = json.writeValueAsString(result);
    receipt.createdAt = clock.instant();
    db.save(receipt);
    access.audit(action, result.get("id"), actor.departmentId);
    return json.readValue(receipt.resultJson, Map.class);
  }

  /** 仅当前账号可恢复自己的已提交命令；结果只含标识与版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object receipt(String key) {
    var list =
        db.query(
            CommandReceipt.class,
            "from CommandReceipt where accountId=?1 and requestKey=?2",
            access.current().id,
            key);
    if (list.isEmpty()) throw new Problem(404, "NOT_FOUND");
    return json.readValue(list.getFirst().resultJson, Map.class);
  }

  static String sha(byte[] bytes) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  Customer saveCustomer(Map<String, Object> b) {
    access.require("customers");
    Customer c = b.get("id") == null ? new Customer() : customer(id(b, "id"));
    if (c.id != null) version(c.version, b);
    Long dept =
        b.get("departmentId") == null ? access.current().departmentId : id(b, "departmentId");
    scope(dept, "customers");
    if (c.id != null) Rules.check(c.departmentId.equals(dept), "IDENTITY_LOCKED");
    Long aid =
        b.get("accountId") == null || b.get("accountId").equals("") ? null : id(b, "accountId");
    if (c.id != null && c.accountId != null)
      Rules.check(c.accountId.equals(aid), "IDENTITY_LOCKED");
    if (aid != null) {
      var a = db.get(Account.class, aid);
      Rules.check(
          (a.enabled || !Rules.flag(b.get("enabled")))
              && a.departmentId.equals(dept)
              && db.get(AccessRole.class, a.roleId).permissions.equals(Set.of("portal")),
          "CUSTOMER_ACCOUNT_INVALID");
    }
    c.departmentId = dept;
    c.accountId = aid;
    c.name = text(b, "name", 180);
    c.contact = text(b, "contact", 180);
    c.billingType = text(b, "billingType", 20);
    Rules.check(Set.of("PER_VISIT", "MONTHLY").contains(c.billingType), "INVALID_INPUT");
    c.enabled = Rules.flag(b.get("enabled"));
    if (c.id == null) db.save(c);
    else c.version++;
    return c;
  }

  Site saveSite(Map<String, Object> b) {
    Customer c = customer(id(b, "customerId"));
    Rules.check(c.enabled, "CUSTOMER_DISABLED");
    if (!access.has("portal")) scope(c.departmentId, "customers");
    var s = b.get("id") == null ? new Site() : db.get(Site.class, id(b, "id"));
    if (s.id != null) {
      version(s.version, b);
      Rules.check(s.customerId.equals(c.id), "IDENTITY_LOCKED");
    }
    s.customerId = c.id;
    s.name = text(b, "name", 180);
    s.address = text(b, "address", 500);
    s.contact = text(b, "contact", 180);
    s.instructions = note(b, "instructions");
    s.enabled = Rules.flag(b.get("enabled"));
    if (s.id == null) db.save(s);
    else s.version++;
    return s;
  }

  Long department(Map<String, Object> b, String code) {
    Long d = b.get("departmentId") == null ? access.current().departmentId : id(b, "departmentId");
    scope(d, code);
    return d;
  }

  ServiceRate saveService(Map<String, Object> b) {
    Long dept = department(b, "catalog");
    var x = b.get("id") == null ? new ServiceRate() : db.get(ServiceRate.class, id(b, "id"));
    if (x.id != null) {
      version(x.version, b);
      Rules.check(x.departmentId.equals(dept), "IDENTITY_LOCKED");
    }
    x.departmentId = dept;
    x.name = text(b, "name", 180);
    x.nameEn = text(b, "nameEn", 180);
    x.categoryCode = text(b, "categoryCode", 20);
    Rules.check(
        db.query(
                    DictionaryEntry.class,
                    "from DictionaryEntry where code=?1 and type='CATEGORY' and enabled=true",
                    x.categoryCode)
                .size()
            == 1,
        "CATEGORY_INVALID");
    x.unit = text(b, "unit", 20);
    Rules.check(Set.of("FIXED", "AREA", "HOURLY").contains(x.unit), "INVALID_INPUT");
    x.basePrice = money(b, "basePrice", true);
    x.unitPrice = money(b, "unitPrice", false);
    x.minutesPerUnit = Rules.integer(b.get("minutesPerUnit"), 1, 720);
    x.workersRequired = Rules.integer(b.get("workersRequired"), 1, 8);
    x.enabled = Rules.flag(b.get("enabled"));
    if (x.id == null) db.save(x);
    else x.version++;
    return x;
  }

  ServiceExtra saveExtra(Map<String, Object> b) {
    Long d = department(b, "catalog");
    var x = b.get("id") == null ? new ServiceExtra() : db.get(ServiceExtra.class, id(b, "id"));
    if (x.id != null) {
      version(x.version, b);
      Rules.check(x.departmentId.equals(d), "IDENTITY_LOCKED");
    }
    x.departmentId = d;
    x.name = text(b, "name", 180);
    x.nameEn = text(b, "nameEn", 180);
    x.price = money(b, "price", true);
    x.minutes = Rules.integer(b.get("minutes"), 0, 360);
    x.enabled = Rules.flag(b.get("enabled"));
    if (x.id == null) db.save(x);
    else x.version++;
    return x;
  }

  Worker saveWorker(Map<String, Object> b) {
    Long d = department(b, "workers");
    var w = b.get("id") == null ? new Worker() : db.get(Worker.class, id(b, "id"));
    if (w.id != null) {
      version(w.version, b);
      Rules.check(w.departmentId.equals(d), "IDENTITY_LOCKED");
    }
    var a = db.get(Account.class, id(b, "accountId"));
    Rules.check(
        (a.enabled || !Rules.flag(b.get("enabled")))
            && a.departmentId.equals(d)
            && db.get(AccessRole.class, a.roleId).permissions.contains("work"),
        "WORKER_ACCOUNT_INVALID");
    if (w.id != null) Rules.check(w.accountId.equals(a.id), "IDENTITY_LOCKED");
    w.departmentId = d;
    w.accountId = a.id;
    w.name = text(b, "name", 180);
    w.hourlyRate = money(b, "hourlyRate", false);
    w.enabled = Rules.flag(b.get("enabled"));
    if (w.id == null) db.save(w);
    else w.version++;
    return w;
  }

  Availability saveAvailability(Map<String, Object> b) {
    var w = db.get(Worker.class, id(b, "workerId"));
    scope(w.departmentId, "workers");
    int day = Rules.integer(b.get("weekday"), 1, 7);
    var startTime = LocalTime.parse(text(b, "startTime", 5));
    var endTime = LocalTime.parse(text(b, "endTime", 5));
    Rules.check(startTime.isBefore(endTime), "TIME_INVALID");
    var x = b.get("id") == null ? new Availability() : db.get(Availability.class, id(b, "id"));
    if (x.id != null) {
      version(x.version, b);
      Rules.check(x.workerId.equals(w.id), "IDENTITY_LOCKED");
    }
    for (var a :
        db.query(
            Availability.class, "from Availability where workerId=?1 and weekday=?2", w.id, day))
      if (!a.id.equals(x.id))
        Rules.check(
            !startTime.isBefore(LocalTime.parse(a.endTime))
                || !LocalTime.parse(a.startTime).isBefore(endTime),
            "TIME_CONFLICT");
    x.workerId = w.id;
    x.weekday = day;
    x.startTime = startTime.toString();
    x.endTime = endTime.toString();
    if (x.id == null) db.save(x);
    else x.version++;
    checkExistingHours(w);
    return x;
  }

  void checkExistingHours(Worker w) {
    db.flush();
    var zone = ZoneId.of(db.get(Department.class, w.departmentId).zone);
    for (var a : db.query(Assignment.class, "from Assignment where workerId=?1", w.id)) {
      var r = db.get(WorkRound.class, a.roundId);
      if (!Set.of("ASSIGNED", "IN_PROGRESS").contains(r.status)) continue;
      var start = r.startsAt.atZone(zone);
      var end = r.endsAt.atZone(zone);
      Rules.check(
          db
              .query(
                  Availability.class,
                  "from Availability where workerId=?1 and weekday=?2",
                  w.id,
                  start.getDayOfWeek().getValue())
              .stream()
              .anyMatch(
                  h ->
                      !start.toLocalTime().isBefore(LocalTime.parse(h.startTime))
                          && !end.toLocalTime().isAfter(LocalTime.parse(h.endTime))),
          "ASSIGNMENT_EXISTS");
    }
  }

  Availability removeAvailability(Map<String, Object> b) {
    var x = db.get(Availability.class, id(b, "id"));
    var w = db.get(Worker.class, x.workerId);
    scope(w.departmentId, "workers");
    version(x.version, b);
    db.delete(x);
    checkExistingHours(w);
    return x;
  }

  TimeOff saveTimeOff(Map<String, Object> b) {
    var w = db.get(Worker.class, id(b, "workerId"));
    scope(w.departmentId, "workers");
    var x = b.get("id") == null ? new TimeOff() : db.get(TimeOff.class, id(b, "id"));
    if (x.id != null) {
      version(x.version, b);
      Rules.check(x.workerId.equals(w.id), "IDENTITY_LOCKED");
      x.version++;
    }
    x.workerId = w.id;
    var zone = ZoneId.of(db.get(Department.class, w.departmentId).zone);
    x.startsAt =
        b.containsKey("localStart")
            ? Pricing.occurrences(LocalDateTime.parse(text(b, "localStart", 20)), 1, 0, zone)
                .getFirst()
            : Instant.parse(text(b, "startsAt", 40));
    x.endsAt =
        b.containsKey("localEnd")
            ? Pricing.occurrences(LocalDateTime.parse(text(b, "localEnd", 20)), 1, 0, zone)
                .getFirst()
            : Instant.parse(text(b, "endsAt", 40));
    Rules.check(
        x.startsAt.isBefore(x.endsAt) && Duration.between(x.startsAt, x.endsAt).toDays() <= 90,
        "TIME_INVALID");
    x.reason = text(b, "reason", 500);
    x.enabled = Rules.flag(b.get("enabled"));
    if (x.enabled)
      for (var a : db.query(Assignment.class, "from Assignment where workerId=?1", w.id)) {
        var round = db.get(WorkRound.class, a.roundId);
        if (Set.of("ASSIGNED", "IN_PROGRESS").contains(round.status))
          Rules.check(
              !Pricing.overlaps(x.startsAt, x.endsAt, round.startsAt, round.endsAt),
              "ASSIGNMENT_EXISTS");
      }
    if (x.id == null) db.save(x);
    return x;
  }

  Booking requestBooking(Map<String, Object> b) {
    Customer c = access.has("portal") ? portalCustomer() : customer(id(b, "customerId"));
    if (!access.has("portal")) scope(c.departmentId, "bookings");
    Rules.check(c.enabled, "CUSTOMER_DISABLED");
    var site = db.get(Site.class, id(b, "siteId"));
    Rules.check(site.customerId.equals(c.id) && site.enabled, "SITE_INVALID");
    var rate = db.get(ServiceRate.class, id(b, "serviceId"));
    Rules.check(rate.enabled && rate.departmentId.equals(c.departmentId), "SERVICE_INVALID");
    var units = money(b, "units", false);
    List<ServiceExtra> extras = new ArrayList<>();
    if (b.get("extraIds") instanceof List<?> ids) {
      Rules.check(ids.size() <= 12 && new HashSet<>(ids).size() == ids.size(), "INVALID_INPUT");
      for (var eid : ids) {
        var e = db.get(ServiceExtra.class, Rules.id(eid));
        Rules.check(e.enabled && e.departmentId.equals(c.departmentId), "SERVICE_INVALID");
        extras.add(e);
      }
    }
    var price = Pricing.price(rate, units, extras);
    int minutes = Pricing.minutes(rate, units, extras);
    int count = Rules.integer(b.get("occurrences"), 1, 12),
        weeks = Rules.integer(b.get("intervalWeeks"), 0, 2);
    var times =
        Pricing.occurrences(
            LocalDateTime.parse(text(b, "localStart", 20)),
            count,
            weeks,
            ZoneId.of(db.get(Department.class, c.departmentId).zone));
    Rules.check(
        times.getFirst().isAfter(clock.instant())
            && times.getLast().isBefore(clock.instant().plus(Duration.ofDays(180))),
        "BOOKING_TIME_INVALID");
    var x = new Booking();
    x.departmentId = c.departmentId;
    x.customerId = c.id;
    x.siteId = site.id;
    x.serviceId = rate.id;
    x.serviceName = rate.name;
    x.unit = rate.unit;
    x.units = units;
    x.extrasSnapshot =
        json.writeValueAsString(
            extras.stream()
                .map(e -> row("name", e.name, "price", e.price, "minutes", e.minutes))
                .toList());
    x.siteSnapshot =
        json.writeValueAsString(
            row(
                "name",
                site.name,
                "address",
                site.address,
                "contact",
                site.contact,
                "instructions",
                site.instructions));
    x.status = "SUBMITTED";
    x.occurrences = count;
    x.intervalWeeks = weeks;
    x.durationMinutes = minutes;
    x.workersRequired = rate.workersRequired;
    x.unitVisitPrice = price;
    x.discount = new BigDecimal("0.00");
    x.quoteNote = note(b, "note");
    x.createdAt = clock.instant();
    db.save(x);
    for (int i = 0; i < times.size(); i++) {
      var v = new Visit();
      v.bookingId = x.id;
      v.occurrenceNo = i + 1;
      v.startsAt = times.get(i);
      v.endsAt = v.startsAt.plusSeconds(minutes * 60L);
      v.status = "REQUESTED";
      v.charge = price;
      v.note = "";
      db.save(v);
      event(v, "REQUESTED", "");
    }
    return x;
  }

  Booking quote(Map<String, Object> b) {
    var x = visibleBooking(id(b, "id"));
    scope(x.departmentId, "bookings");
    version(x.version, b);
    Rules.check(Set.of("SUBMITTED", "QUOTED").contains(x.status), "STATE_INVALID");
    var discount = money(b, "discount", true);
    Rules.check(discount.compareTo(x.unitVisitPrice) < 0, "AMOUNT_INVALID");
    x.discount = discount;
    x.quoteNote = note(b, "note");
    x.status = "QUOTED";
    x.version++;
    for (var v : visits(x.id))
      if (!Set.of("CANCELLED", "SKIPPED").contains(v.status)) {
        v.charge = x.unitVisitPrice.subtract(discount);
        v.version++;
        event(v, "QUOTED", x.quoteNote);
      }
    return x;
  }

  Booking confirm(Map<String, Object> b) {
    var x = visibleBooking(id(b, "id"));
    version(x.version, b);
    Rules.check(x.status.equals("QUOTED"), "STATE_INVALID");
    String proof = "";
    if (!access.has("portal")) {
      scope(x.departmentId, "bookings");
      proof = text(b, "confirmationRef", 300);
    }
    Rules.check(
        visits(x.id).stream()
            .filter(v -> v.status.equals("REQUESTED"))
            .allMatch(v -> v.startsAt.isAfter(clock.instant())),
        "BOOKING_TIME_INVALID");
    x.status = "CONFIRMED";
    x.confirmedAt = clock.instant();
    x.version++;
    for (var v : visits(x.id))
      if (v.status.equals("REQUESTED")) {
        v.status = "READY";
        v.version++;
        event(
            v,
            access.has("portal") ? "CUSTOMER_CONFIRMED" : "OFFLINE_CONFIRMATION_RECORDED",
            proof);
      }
    return x;
  }

  void beforeChange(Visit v) {
    if (access.has("portal"))
      Rules.check(
          !v.startsAt.isBefore(clock.instant().plusSeconds(admin.setting("cancelHours") * 3600L)),
          "NOTICE_TOO_SHORT");
    else dispatch(v);
  }

  void cancelAssignment(Visit v) {
    for (var r : rounds(v.id)) if (r.status.equals("ASSIGNED")) r.status = "CANCELLED";
  }

  Visit cancel(Map<String, Object> b) {
    var v = visibleVisit(id(b, "id"));
    version(v.version, b);
    Rules.check(Set.of("REQUESTED", "READY", "ASSIGNED").contains(v.status), "STATE_INVALID");
    Rules.check(
        rounds(v.id).stream().allMatch(r -> r.completedAt == null), "SERVICE_ALREADY_PERFORMED");
    beforeChange(v);
    String why = text(b, "reason", 500);
    cancelAssignment(v);
    v.status = Boolean.TRUE.equals(b.get("skip")) ? "SKIPPED" : "CANCELLED";
    v.charge = new BigDecimal("0.00");
    v.note = why;
    v.version++;
    event(v, v.status, why);
    return v;
  }

  Visit reschedule(Map<String, Object> b) {
    var v = visibleVisit(id(b, "id"));
    version(v.version, b);
    Rules.check(Set.of("READY", "ASSIGNED").contains(v.status), "STATE_INVALID");
    beforeChange(v);
    var order = db.get(Booking.class, v.bookingId);
    var target =
        Pricing.occurrences(
                LocalDateTime.parse(text(b, "localStart", 20)),
                1,
                0,
                ZoneId.of(db.get(Department.class, order.departmentId).zone))
            .getFirst();
    Rules.check(
        target.isAfter(clock.instant())
            && target.isBefore(clock.instant().plus(Duration.ofDays(180))),
        "BOOKING_TIME_INVALID");
    String why = text(b, "reason", 500);
    String old = v.startsAt.toString();
    cancelAssignment(v);
    v.startsAt = target;
    v.endsAt = target.plusSeconds(order.durationMinutes * 60L);
    v.status = "READY";
    v.version++;
    event(v, "RESCHEDULED", old + " → " + target + "; " + why);
    return v;
  }

  void available(Worker w, Instant start, Instant end) {
    Rules.check(w.enabled && db.get(Account.class, w.accountId).enabled, "WORKER_DISABLED");
    var zone = ZoneId.of(db.get(Department.class, w.departmentId).zone);
    var a = start.atZone(zone);
    var z = end.atZone(zone);
    Rules.check(a.toLocalDate().equals(z.toLocalDate()), "OUTSIDE_AVAILABILITY");
    boolean fits =
        db
            .query(
                Availability.class,
                "from Availability where workerId=?1 and weekday=?2",
                w.id,
                a.getDayOfWeek().getValue())
            .stream()
            .anyMatch(
                x ->
                    !a.toLocalTime().isBefore(LocalTime.parse(x.startTime))
                        && !z.toLocalTime().isAfter(LocalTime.parse(x.endTime)));
    Rules.check(fits, "OUTSIDE_AVAILABILITY");
    for (var t : db.query(TimeOff.class, "from TimeOff where workerId=?1 and enabled=true", w.id))
      Rules.check(!Pricing.overlaps(start, end, t.startsAt, t.endsAt), "TIME_OFF");
    int buffer = admin.setting("travelBuffer");
    for (var x : db.query(Assignment.class, "from Assignment where workerId=?1", w.id)) {
      var round = db.get(WorkRound.class, x.roundId);
      if (round.status.equals("IN_PROGRESS")) {
        var busyEnd = round.endsAt.isAfter(clock.instant()) ? round.endsAt : clock.instant();
        Rules.check(
            !Pricing.overlaps(
                start,
                end,
                round.startsAt.minusSeconds(buffer * 60L),
                busyEnd.plusSeconds(buffer * 60L)),
            "WORKER_BUSY");
      }
      if (round.status.equals("ASSIGNED"))
        Rules.check(
            !Pricing.overlaps(
                start,
                end,
                round.startsAt.minusSeconds(buffer * 60L),
                round.endsAt.plusSeconds(buffer * 60L)),
            "TIME_CONFLICT");
    }
  }

  Visit assign(Map<String, Object> b) {
    var v = visibleVisit(id(b, "id"));
    dispatch(v);
    version(v.version, b);
    var order = db.get(Booking.class, v.bookingId);
    Rules.check(
        order.status.equals("CONFIRMED")
            && Set.of("READY", "REWORK", "ASSIGNED").contains(v.status)
            && v.startsAt.isAfter(clock.instant().minusSeconds(1800)),
        "STATE_INVALID");
    Rules.check(db.get(Customer.class, order.customerId).enabled, "CUSTOMER_DISABLED");
    if (!(b.get("workerIds") instanceof List<?> ids)) throw new Problem(400, "INVALID_INPUT");
    Rules.check(
        ids.size() == order.workersRequired && new HashSet<>(ids).size() == ids.size(),
        "CREW_SIZE_INVALID");
    Long leader = id(b, "leaderId");
    List<Worker> crew = ids.stream().map(Rules::id).map(i -> db.get(Worker.class, i)).toList();
    Rules.check(crew.stream().anyMatch(w -> w.id.equals(leader)), "LEADER_INVALID");
    cancelAssignment(v);
    for (var w : crew) {
      Rules.check(w.departmentId.equals(order.departmentId), "OUT_OF_SCOPE");
      available(w, v.startsAt, v.endsAt);
    }
    var round = new WorkRound();
    round.visitId = v.id;
    round.roundNo = ++v.roundNo;
    round.leaderId = leader;
    round.startsAt = v.startsAt;
    round.endsAt = v.endsAt;
    round.status = "ASSIGNED";
    round.completionNote = "";
    round.acceptanceNote = "";
    db.save(round);
    for (var w : crew) {
      var a = new Assignment();
      a.roundId = round.id;
      a.workerId = w.id;
      a.hourlyRate = w.hourlyRate;
      a.cost = new BigDecimal("0.00");
      db.save(a);
    }
    v.status = "ASSIGNED";
    v.version++;
    event(v, "ASSIGNED", text(b, "note", 500));
    return v;
  }

  void leader(WorkRound r) {
    var w = workerSelf();
    Rules.check(r.leaderId.equals(w.id) && assignedTo(r, w.id), "LEADER_ONLY");
  }

  long photoCount(WorkRound r, String phase) {
    return db.query(Photo.class, "from Photo where roundId=?1 and phase=?2", r.id, phase).size();
  }

  Visit start(Map<String, Object> b) {
    var v = visibleVisit(id(b, "id"));
    version(v.version, b);
    var r = currentRound(v);
    leader(r);
    Rules.check(v.status.equals("ASSIGNED") && r.status.equals("ASSIGNED"), "STATE_INVALID");
    Rules.check(
        !clock.instant().isBefore(r.startsAt.minusSeconds(1800))
            && clock.instant().isBefore(r.endsAt),
        "START_WINDOW_INVALID");
    Rules.check(photoCount(r, "BEFORE") > 0, "BEFORE_PHOTO_REQUIRED");
    for (var a : assigned(r.id))
      for (var other : db.query(Assignment.class, "from Assignment where workerId=?1", a.workerId))
        if (!other.roundId.equals(r.id))
          Rules.check(
              !db.get(WorkRound.class, other.roundId).status.equals("IN_PROGRESS"), "WORKER_BUSY");
    r.status = "IN_PROGRESS";
    r.startedAt = clock.instant();
    v.status = "IN_PROGRESS";
    v.version++;
    event(v, "STARTED", "");
    return v;
  }

  Visit complete(Map<String, Object> b) {
    var v = visibleVisit(id(b, "id"));
    version(v.version, b);
    var r = currentRound(v);
    leader(r);
    Rules.check(v.status.equals("IN_PROGRESS") && r.status.equals("IN_PROGRESS"), "STATE_INVALID");
    Rules.check(photoCount(r, "AFTER") > 0, "AFTER_PHOTO_REQUIRED");
    if (!(b.get("minutes") instanceof Map<?, ?> times)) throw new Problem(400, "INVALID_INPUT");
    var crew = assigned(r.id);
    Rules.check(times.size() == crew.size(), "HOURS_REQUIRED");
    for (var a : crew) {
      a.actualMinutes = Rules.integer(times.get(a.workerId.toString()), 1, 1440);
      a.cost =
          a.hourlyRate
              .multiply(BigDecimal.valueOf(a.actualMinutes))
              .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
    r.completionNote = text(b, "note", 1000);
    r.completedAt = clock.instant();
    r.status = "REVIEW";
    v.status = "REVIEW";
    v.version++;
    event(v, "COMPLETED", r.completionNote);
    return v;
  }

  void customerDecision(Visit v, Map<String, Object> b) {
    var order = db.get(Booking.class, v.bookingId);
    if (access.has("portal")) customer(order.customerId);
    else {
      scope(order.departmentId, "bookings");
      text(b, "confirmationRef", 300);
    }
  }

  Visit accept(Map<String, Object> b) {
    var v = visibleVisit(id(b, "id"));
    version(v.version, b);
    customerDecision(v, b);
    Rules.check(v.status.equals("REVIEW"), "STATE_INVALID");
    var r = currentRound(v);
    r.status = "ACCEPTED";
    r.acceptanceNote = note(b, "note");
    v.status = "ACCEPTED";
    v.version++;
    event(
        v,
        access.has("portal") ? "ACCEPTED" : "OFFLINE_ACCEPTANCE_RECORDED",
        r.acceptanceNote + (access.has("portal") ? "" : "; " + text(b, "confirmationRef", 300)));
    return v;
  }

  Visit rework(Map<String, Object> b) {
    var v = visibleVisit(id(b, "id"));
    version(v.version, b);
    customerDecision(v, b);
    Rules.check(v.status.equals("REVIEW"), "STATE_INVALID");
    var order = db.get(Booking.class, v.bookingId);
    var start =
        Pricing.occurrences(
                LocalDateTime.parse(text(b, "localStart", 20)),
                1,
                0,
                ZoneId.of(db.get(Department.class, order.departmentId).zone))
            .getFirst();
    Rules.check(
        start.isAfter(clock.instant())
            && start.isBefore(clock.instant().plus(Duration.ofDays(180))),
        "BOOKING_TIME_INVALID");
    var r = currentRound(v);
    r.status = "REJECTED";
    r.acceptanceNote = text(b, "reason", 500);
    v.startsAt = start;
    v.endsAt = start.plusSeconds(order.durationMinutes * 60L);
    v.status = "REWORK";
    v.version++;
    event(v, "REWORK_REQUESTED", r.acceptanceNote);
    return v;
  }

  BigDecimal paid(Long vid) {
    return db.query(MoneyEntry.class, "from MoneyEntry where visitId=?1", vid).stream()
        .map(x -> x.kind.equals("RECEIPT") ? x.amount : x.amount.negate())
        .reduce(new BigDecimal("0.00"), BigDecimal::add);
  }

  MoneyEntry recordMoney(Map<String, Object> b) {
    var v = visibleVisit(id(b, "visitId"));
    var order = db.get(Booking.class, v.bookingId);
    scope(order.departmentId, "finance");
    String kind = text(b, "kind", 12);
    Rules.check(Set.of("RECEIPT", "REFUND").contains(kind), "INVALID_INPUT");
    var amount = money(b, "amount", false);
    var net = paid(v.id);
    if (kind.equals("RECEIPT")) {
      Rules.check(
          order.status.equals("CONFIRMED")
              && !Set.of("CANCELLED", "SKIPPED").contains(v.status)
              && net.add(amount).compareTo(v.charge) <= 0,
          "PAYMENT_EXCEEDS_DUE");
    } else
      Rules.check(
          Set.of("CANCELLED", "SKIPPED").contains(v.status) && amount.compareTo(net) <= 0,
          "REFUND_EXCEEDS_PAID");
    var e = new MoneyEntry();
    e.visitId = v.id;
    e.departmentId = order.departmentId;
    e.kind = kind;
    e.amount = amount;
    e.reference = text(b, "reference", 120).toUpperCase(Locale.ROOT);
    e.note = Rules.text(b.get("note"), 500, false);
    e.actor = access.current().username;
    e.createdAt = clock.instant();
    db.save(e);
    event(v, kind, "外部凭证登记 / External record");
    return e;
  }

  Settlement visibleSettlement(Long sid) {
    var s = db.get(Settlement.class, sid);
    if (access.has("finance")) scope(s.departmentId, "finance");
    else Rules.check(workerSelf().id.equals(s.workerId), "OUT_OF_SCOPE");
    return s;
  }

  Settlement createSettlement(Map<String, Object> b) {
    var w = db.get(Worker.class, id(b, "workerId"));
    scope(w.departmentId, "finance");
    var from = LocalDate.parse(text(b, "fromDate", 10));
    var to = LocalDate.parse(text(b, "toDate", 10));
    Rules.check(!from.isAfter(to) && to.toEpochDay() - from.toEpochDay() <= 92, "TIME_INVALID");
    var zone = ZoneId.of(db.get(Department.class, w.departmentId).zone);
    var list =
        db
            .query(
                Assignment.class,
                "from Assignment where workerId=?1 and settlementId is null and actualMinutes>0",
                w.id)
            .stream()
            .filter(
                a -> {
                  var round = db.get(WorkRound.class, a.roundId);
                  if (round.completedAt == null
                      || !db.get(Visit.class, round.visitId).status.equals("ACCEPTED"))
                    return false;
                  var date = round.completedAt.atZone(zone).toLocalDate();
                  return !date.isBefore(from) && !date.isAfter(to);
                })
            .toList();
    Rules.check(!list.isEmpty(), "NOTHING_TO_SETTLE");
    var s = new Settlement();
    s.workerId = w.id;
    s.departmentId = w.departmentId;
    s.fromDate = from.toString();
    s.toDate = to.toString();
    s.amount = list.stream().map(a -> a.cost).reduce(new BigDecimal("0.00"), BigDecimal::add);
    s.status = "DRAFT";
    s.note = Rules.text(b.get("note"), 500, false);
    s.createdAt = clock.instant();
    db.save(s);
    list.forEach(a -> a.settlementId = s.id);
    return s;
  }

  Settlement confirmSettlement(Map<String, Object> b) {
    var s = visibleSettlement(id(b, "id"));
    version(s.version, b);
    Rules.check(workerSelf().id.equals(s.workerId), "OUT_OF_SCOPE");
    Rules.check(s.status.equals("DRAFT"), "STATE_INVALID");
    s.status = "CONFIRMED";
    s.confirmedAt = clock.instant();
    s.version++;
    return s;
  }

  Settlement paySettlement(Map<String, Object> b) {
    var s = visibleSettlement(id(b, "id"));
    scope(s.departmentId, "finance");
    version(s.version, b);
    Rules.check(s.status.equals("CONFIRMED"), "WORKER_CONFIRMATION_REQUIRED");
    s.paymentReference = text(b, "reference", 120).toUpperCase(Locale.ROOT);
    Rules.check(
        db.query(
                Settlement.class,
                "from Settlement where paymentReference=?1 and departmentId=?2 and id<>?3",
                s.paymentReference,
                s.departmentId,
                s.id)
            .isEmpty(),
        "DUPLICATE_REFERENCE");
    s.paidAt = clock.instant();
    s.status = "PAID";
    s.version++;
    return s;
  }

  Settlement cancelSettlement(Map<String, Object> b) {
    var s = visibleSettlement(id(b, "id"));
    scope(s.departmentId, "finance");
    version(s.version, b);
    Rules.check(Set.of("DRAFT", "CONFIRMED").contains(s.status), "STATE_INVALID");
    s.note = text(b, "reason", 500);
    s.status = "CANCELLED";
    s.version++;
    for (var a : db.query(Assignment.class, "from Assignment where settlementId=?1", s.id))
      a.settlementId = null;
    return s;
  }
}
