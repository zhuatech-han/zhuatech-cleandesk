// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { t, localized } from "./ui.js";
const field = (key, zh, en, extra = {}) => ({
  key,
  label: t(zh, en),
  ...extra,
});
const options = (rows, label = (r) => r.name) =>
  rows.map((r) => ({ value: r.id, label: label(r) }));
const select = (key, zh, en, rows, extra = {}) =>
  field(key, zh, en, { type: "select", options: rows, ...extra });
const enabled = () =>
  field("enabled", "启用", "Enabled", { type: "checkbox", required: false });
const cash = (key, zh, en) =>
  field(key, zh, en, { type: "number", min: 0, step: "0.01" });
const textarea = (key, zh, en, required = false) =>
  field(key, zh, en, { type: "textarea", required });
/** 将服务器时刻显示为业务时区的输入值，不用浏览器时区修改预约。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localInput(value, zone = "Asia/Shanghai") {
  const p = Object.fromEntries(
    new Intl.DateTimeFormat("en-CA", {
      timeZone: zone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    })
      .formatToParts(new Date(value))
      .filter((x) => x.type !== "literal")
      .map((x) => [x.type, x.value]),
  );
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}
/** 基于当前真实目录生成表单，归属字段由后台再次校验。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function definition(
  kind,
  data,
  bindings = [],
  record = {},
  profile = {},
) {
  const copy = JSON.parse(JSON.stringify(record));
  const d = data;
  const org = profile.departmentId;
  let fields = [],
    initial = { ...copy },
    title = "",
    action = kind;
  const zone = copy.timezone || d.timezone || "Asia/Shanghai";
  const actives = (rows) => rows.filter((r) => r.enabled || r.id === copy.id);
  const cust = options(
    (d.customers || []).filter((c) => c.enabled || c.id === copy.customerId),
  );
  const customerDepartment = (f) =>
    d.customers.find((c) => c.id == f.customerId)?.departmentId;
  const bookingZone = (f) =>
    d.organizations?.find((o) => o.id === customerDepartment(f))?.zone || zone;
  const accounts = (type) =>
    options(
      bindings.filter((a) => (a.enabled || a.id === copy.accountId) && a[type]),
      (a) => `${a.displayName} (${a.username})`,
    );
  const defaults = (values) => (initial = { ...values, ...copy });
  switch (kind) {
    case "customer.save":
      title = t("客户资料", "Customer");
      defaults({
        name: "",
        contact: "",
        accountId: "",
        billingType: "PER_VISIT",
        enabled: true,
        departmentId: org,
      });
      fields = [
        field("name", "客户名称", "Customer name"),
        field("contact", "联系信息", "Contact"),
        select(
          "accountId",
          "客户登录账号",
          "Customer account",
          [
            { value: "", label: t("暂不绑定", "Not linked") },
            ...accounts("portal"),
          ],
          { required: false, readonly: !!copy.accountId },
        ),
        select("billingType", "结算方式", "Billing preference", [
          { value: "PER_VISIT", label: t("按次结算", "Per visit") },
          { value: "MONTHLY", label: t("企业月结", "Monthly statement") },
        ]),
        enabled(),
      ];
      break;
    case "site.save":
      title = t("服务场所", "Service site");
      defaults({
        customerId: profile.permissions?.includes("portal")
          ? d.customers[0]?.id
          : "",
        name: "",
        address: "",
        contact: "",
        instructions: "",
        enabled: true,
      });
      fields = [
        select("customerId", "所属客户", "Customer", cust, {
          readonly: !!copy.id,
        }),
        field("name", "场所名称", "Site name"),
        field("address", "详细地址", "Address", { max: 500 }),
        field("contact", "现场联系人", "Site contact"),
        textarea(
          "instructions",
          "服务说明（请勿填写门锁密码）",
          "Instructions (do not enter access codes)",
        ),
        enabled(),
      ];
      break;
    case "service.save":
      title = t("服务计价", "Service pricing");
      defaults({
        name: "",
        nameEn: "",
        categoryCode: d.categories[0]?.code || "",
        unit: "FIXED",
        basePrice: "0.00",
        unitPrice: "100.00",
        minutesPerUnit: 120,
        workersRequired: 1,
        enabled: true,
        departmentId: org,
      });
      fields = [
        field("name", "中文名称", "Chinese name"),
        field("nameEn", "英文名称", "English name"),
        select(
          "categoryCode",
          "服务类别",
          "Category",
          (d.categories || []).map((c) => ({
            value: c.code,
            label: localized(c),
          })),
        ),
        select("unit", "计价单位", "Pricing unit", [
          { value: "FIXED", label: t("每次", "Per visit") },
          { value: "AREA", label: t("每平方米", "Per square metre") },
          { value: "HOURLY", label: t("班组每小时", "Per crew hour") },
        ]),
        cash("basePrice", "基础费用", "Base fee"),
        cash("unitPrice", "单位价格", "Unit price"),
        field("minutesPerUnit", "每单位班组作业分钟", "Crew minutes per unit", {
          type: "number",
          min: 1,
          maxValue: 720,
          step: 1,
        }),
        field("workersRequired", "每次所需人数", "Workers per visit", {
          type: "number",
          min: 1,
          maxValue: 8,
          step: 1,
        }),
        enabled(),
      ];
      break;
    case "extra.save":
      title = t("服务增项", "Service extra");
      defaults({
        name: "",
        nameEn: "",
        price: "0.00",
        minutes: 0,
        enabled: true,
        departmentId: org,
      });
      fields = [
        field("name", "中文名称", "Chinese name"),
        field("nameEn", "英文名称", "English name"),
        cash("price", "每次追加费用", "Extra fee per visit"),
        field("minutes", "每次追加分钟", "Extra crew minutes", {
          type: "number",
          min: 0,
          maxValue: 360,
          step: 1,
        }),
        enabled(),
      ];
      break;
    case "worker.save":
      title = t("保洁人员", "Cleaning worker");
      defaults({
        name: "",
        accountId: "",
        hourlyRate: "30.00",
        enabled: true,
        departmentId: org,
      });
      fields = [
        field("name", "人员姓名", "Worker name"),
        select(
          "accountId",
          "员工任务账号",
          "Worker account",
          accounts("work"),
          { readonly: !!copy.id },
        ),
        cash("hourlyRate", "每小时结算单价", "Settlement rate per hour"),
        enabled(),
      ];
      break;
    case "availability.save":
      title = t("每周工作时段", "Weekly availability");
      defaults({
        workerId: "",
        weekday: 1,
        startTime: "09:00",
        endTime: "18:00",
      });
      fields = [
        select("workerId", "人员", "Worker", options(d.workers), {
          readonly: !!copy.id,
        }),
        select(
          "weekday",
          "星期",
          "Weekday",
          [1, 2, 3, 4, 5, 6, 7].map((n) => ({
            value: n,
            label: t(
              ["", "一", "二", "三", "四", "五", "六", "日"][n],
              [
                "",
                "Monday",
                "Tuesday",
                "Wednesday",
                "Thursday",
                "Friday",
                "Saturday",
                "Sunday",
              ][n],
            ),
          })),
        ),
        field("startTime", "开始时间", "Start", { type: "time" }),
        field("endTime", "结束时间", "End", { type: "time", hint: zone }),
      ];
      break;
    case "timeoff.save":
      title = t("人员请假／不可安排", "Worker time off");
      defaults({
        workerId: "",
        localStart: localInput(Date.now() + 86400000, zone),
        localEnd: localInput(Date.now() + 90000000, zone),
        reason: "",
        enabled: true,
      });
      if (copy.startsAt) {
        initial.localStart = localInput(copy.startsAt, zone);
        initial.localEnd = localInput(copy.endsAt, zone);
      }
      fields = [
        select("workerId", "人员", "Worker", options(d.workers), {
          readonly: !!copy.id,
        }),
        field("localStart", "开始", "Start", {
          type: "datetime-local",
          hint: zone,
        }),
        field("localEnd", "结束", "End", { type: "datetime-local" }),
        field("reason", "原因", "Reason", { max: 500 }),
        enabled(),
      ];
      break;
    case "booking.request": {
      title = t("申请保洁服务", "Request cleaning");
      defaults({
        customerId: profile.permissions?.includes("portal")
          ? d.customers[0]?.id
          : "",
        siteId: "",
        serviceId: "",
        units: "1.00",
        extraIds: [],
        localStart: localInput(Date.now() + 86400000, zone),
        intervalWeeks: 0,
        occurrences: 4,
        note: "",
      });
      fields = [
        ...(!profile.permissions?.includes("portal")
          ? [select("customerId", "客户", "Customer", cust)]
          : []),
        select("siteId", "服务场所", "Service site", [], {
          getOptions: (f) =>
            options(
              actives(d.sites.filter((s) => s.customerId == f.customerId)),
            ),
        }),
        select("serviceId", "服务", "Service", [], {
          getOptions: (f) =>
            options(
              actives(d.services).filter(
                (s) => s.departmentId === customerDepartment(f),
              ),
              localized,
            ),
        }),
        field("units", "面积或班组小时数", "Area or crew hours", {
          type: "number",
          min: 0.01,
          maxValue: 10000,
          step: 0.01,
          show: (f) =>
            !!f.serviceId &&
            d.services.find((s) => s.id == f.serviceId)?.unit !== "FIXED",
        }),
        field("extraIds", "增项（每次计费）", "Extras (charged per visit)", {
          type: "multi",
          show: (f) =>
            d.extras.some(
              (e) => e.enabled && e.departmentId === customerDepartment(f),
            ),
          options: [],
          getOptions: (f) =>
            options(
              actives(d.extras).filter(
                (e) => e.departmentId === customerDepartment(f),
              ),
              (e) => `${localized(e)} · ${e.price}`,
            ),
          required: false,
        }),
        field("localStart", "首次希望上门时间", "First requested visit", {
          type: "datetime-local",
          getHint: (f) =>
            bookingZone(f) +
            " · " +
            t(
              "需报价确认和人员安排后生效",
              "Subject to quote confirmation and crew assignment",
            ),
        }),
        select("intervalWeeks", "服务频率", "Frequency", [
          { value: 0, label: t("单次", "One-off") },
          { value: 1, label: t("每周", "Weekly") },
          { value: 2, label: t("每两周", "Fortnightly") },
        ]),
        field("occurrences", "总次数", "Total visits", {
          type: "number",
          min: 2,
          maxValue: 12,
          step: 1,
          show: (f) => Number(f.intervalWeeks) !== 0,
        }),
        textarea("note", "服务需求", "Service requirements"),
      ];
      break;
    }
    case "booking.quote":
      title = t("发送报价", "Send quote");
      defaults({ discount: "0.00", note: "" });
      fields = [
        cash("discount", "每次上门优惠金额", "Discount per visit"),
        textarea("note", "报价说明", "Quote note"),
      ];
      break;
    case "booking.confirm":
      title = t("确认报价", "Confirm quote");
      fields = profile.permissions?.includes("portal")
        ? []
        : [
            field(
              "confirmationRef",
              "客户线下确认凭证",
              "Customer offline confirmation reference",
              { max: 300 },
            ),
          ];
      break;
    case "visit.cancel":
      title = t("取消／跳过此次上门", "Cancel or skip this visit");
      defaults({ reason: "", skip: false });
      fields = [
        field("reason", "原因", "Reason", { max: 500 }),
        field(
          "skip",
          "这是周期中的一次跳过",
          "Skip this occurrence of a recurring booking",
          { type: "checkbox", required: false },
        ),
      ];
      break;
    case "visit.reschedule":
      title = t("改期并重新安排人员", "Reschedule and release the old crew");
      defaults({
        localStart: localInput(copy.startsAt || Date.now() + 86400000, zone),
        reason: "",
      });
      fields = [
        field("localStart", "新的希望上门时间", "New requested visit", {
          type: "datetime-local",
          hint: zone,
        }),
        field("reason", "改期原因", "Reason", { max: 500 }),
      ];
      break;
    case "visit.assign":
      title = t("安排人员／替班", "Assign or replace crew");
      defaults({
        workerIds: (copy.crew || []).map((w) => w.id),
        leaderId: copy.leaderId || "",
        note: "",
      });
      fields = [
        field(
          "workerIds",
          t(`选择${copy.workersRequired}名人员`, "选择人员"),
          `Choose ${copy.workersRequired} workers`,
          {
            type: "multi",
            options: options(
              actives(d.workers).filter(
                (w) =>
                  !copy.departmentId || w.departmentId === copy.departmentId,
              ),
            ),
            required: true,
          },
        ),
        select(
          "leaderId",
          "本轮负责人",
          "Visit lead",
          options(
            actives(d.workers).filter(
              (w) => !copy.departmentId || w.departmentId === copy.departmentId,
            ),
          ),
        ),
        field("note", "派工说明", "Dispatch note", { max: 500 }),
      ];
      break;
    case "visit.start":
      title = t("开始此次作业", "Start this work round");
      fields = [];
      break;
    case "visit.complete":
      title = t("提交完工", "Submit completed work");
      defaults({ note: "" });
      fields = (copy.crew || []).map((w) =>
        field(
          "minutes_" + w.id,
          `${w.name} 实际分钟`,
          `${w.name} actual minutes`,
          { type: "number", min: 1, maxValue: 1440, step: 1 },
        ),
      );
      fields.push(textarea("note", "完成说明", "Completion note", true));
      break;
    case "visit.accept":
      title = t("验收通过", "Accept completed service");
      defaults({ note: "" });
      fields = [
        textarea("note", "验收意见", "Acceptance note"),
        ...(!profile.permissions?.includes("portal")
          ? [
              field(
                "confirmationRef",
                "客户线下验收凭证",
                "Customer offline acceptance reference",
                { max: 300 },
              ),
            ]
          : []),
      ];
      break;
    case "visit.rework":
      title = t("要求返工", "Request rework");
      defaults({
        localStart: localInput(Date.now() + 86400000, zone),
        reason: "",
      });
      fields = [
        field("localStart", "希望返工时间", "Requested rework time", {
          type: "datetime-local",
          hint: zone,
        }),
        field("reason", "需要返工的内容", "Rework required", { max: 500 }),
        ...(!profile.permissions?.includes("portal")
          ? [
              field(
                "confirmationRef",
                "客户线下反馈凭证",
                "Customer offline feedback reference",
                { max: 300 },
              ),
            ]
          : []),
      ];
      break;
    case "money.record":
      title =
        copy.kind === "REFUND"
          ? t("登记已完成退款", "Record completed refund")
          : t("登记已完成收款", "Record completed receipt");
      defaults({ kind: "RECEIPT", amount: "", reference: "", note: "" });
      fields = [
        cash("amount", "实际金额", "Actual amount"),
        field(
          "reference",
          "外部交易／收据凭证号",
          "External transaction or receipt reference",
          { max: 120 },
        ),
        textarea("note", "说明", "Note"),
      ];
      break;
    case "settlement.create":
      title = t("生成人员结算单", "Create worker statement");
      defaults({
        workerId: "",
        fromDate: new Date().toISOString().slice(0, 7) + "-01",
        toDate: new Date().toISOString().slice(0, 10),
        note: "",
      });
      fields = [
        select("workerId", "人员", "Worker", options(d.workers)),
        field("fromDate", "作业完成日期自", "Work completed from", {
          type: "date",
        }),
        field("toDate", "至", "To", { type: "date" }),
        textarea("note", "说明", "Note"),
      ];
      break;
    case "settlement.confirm":
      title = t("本人确认结算金额", "Confirm my settlement statement");
      fields = [];
      break;
    case "settlement.pay":
      title = t("登记已完成人员付款", "Record completed worker payment");
      fields = [
        field("reference", "外部付款凭证号", "External payment reference", {
          max: 120,
        }),
      ];
      break;
    case "settlement.cancel":
      title = t("撤销未付结算单", "Cancel unpaid statement");
      fields = [field("reason", "撤销原因", "Reason", { max: 500 })];
      break;
    default:
      throw Error("Unknown form");
  }
  if (
    profile.scope === "ALL" &&
    ["customer.save", "service.save", "extra.save", "worker.save"].includes(
      kind,
    )
  )
    fields.unshift(
      select(
        "departmentId",
        "所属组织",
        "Organization",
        options(d.organizations || []),
        { readonly: !!copy.id },
      ),
    );
  for (const f of fields)
    if (f.key === "accountId") {
      f.getOptions = (form) =>
        f.options.filter(
          (o) =>
            o.value === "" ||
            bindings.find((a) => a.id === o.value)?.departmentId ==
              form.departmentId,
        );
    }
  return { title, fields, initial, action };
}
/** 只发送业务字段；界面隐藏的按次数量和单次重复次数在这里明确固定。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function submission(kind, form, data) {
  const b = { ...form };
  if (kind === "booking.request") {
    if (Number(b.intervalWeeks) === 0) b.occurrences = 1;
    if (data.services.find((s) => s.id == b.serviceId)?.unit === "FIXED")
      b.units = "1.00";
  }
  if (kind === "visit.complete") {
    b.minutes = Object.fromEntries(
      Object.entries(b)
        .filter(([k]) => k.startsWith("minutes_"))
        .map(([k, v]) => [k.slice(8), v]),
    );
    for (const k of Object.keys(b)) if (k.startsWith("minutes_")) delete b[k];
  }
  return b;
}
