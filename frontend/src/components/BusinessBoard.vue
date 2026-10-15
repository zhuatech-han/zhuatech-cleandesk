<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, watch } from "vue";
import RecordTable from "./RecordTable.vue";
import { t, localized, dateTime, stateName } from "../ui.js";
import { localInput } from "../forms.js";
const props = defineProps({
  section: { type: String, required: true },
  data: { type: Object, required: true },
  profile: { type: Object, required: true },
  busy: Boolean,
});
const emit = defineEmits([
  "form",
  "booking",
  "visit",
  "settlement",
  "statement",
]);
const tab = ref("");
const day = ref("");
const filter = ref("");
const workerFilter = ref("");
const has = (c) => props.profile.permissions.includes(c);
const zone = computed(() => props.data.timezone || "Asia/Shanghai");
watch(
  () => props.section,
  () => {
    tab.value = "";
    filter.value = "";
    workerFilter.value = "";
  },
  { immediate: true },
);
watch(
  zone,
  () => {
    if (!day.value) day.value = localInput(Date.now(), zone.value).slice(0, 10);
  },
  { immediate: true },
);
const cols = (defs) =>
  defs.map(([key, zh, en, render]) => ({ key, label: t(zh, en), render }));
const names = (rows, id) => rows.find((r) => r.id === id)?.name || "#" + id;
const title = computed(
  () =>
    ({
      bookings: t("预约与报价", "Bookings & quotes"),
      portal: t("我的保洁", "My cleaning"),
      work: t("我的任务", "My work"),
      dispatch: t("派工日历", "Dispatch calendar"),
      customers: t("客户与场所", "Customers & sites"),
      catalog: t("服务与计价", "Services & pricing"),
      workers: t("人员与时段", "Workers & availability"),
      finance: t("收付与结算", "Receipts & settlements"),
    })[props.section],
);
const collection = computed(() => {
  if (props.section === "customers")
    return tab.value === "sites" ? "sites" : "customers";
  if (props.section === "catalog")
    return tab.value === "extras" ? "extras" : "services";
  if (props.section === "workers")
    return tab.value === "availability"
      ? "availability"
      : tab.value === "timeOff"
        ? "timeOff"
        : "workers";
  if (props.section === "finance")
    return tab.value === "settlements" ? "settlements" : "visits";
  if (props.section === "portal")
    return tab.value === "sites"
      ? "sites"
      : tab.value === "visits"
        ? "visits"
        : "bookings";
  if (props.section === "work")
    return tab.value === "settlements" ? "settlements" : "visits";
  return props.section === "dispatch" ? "visits" : "bookings";
});
const createAction = computed(
  () =>
    ({
      customers: "customer.save",
      sites: "site.save",
      services: "service.save",
      extras: "extra.save",
      workers: "worker.save",
      availability: "availability.save",
      timeOff: "timeoff.save",
      bookings: "booking.request",
      settlements: "settlement.create",
    })[collection.value],
);
const allowCreate = computed(
  () =>
    !!createAction.value &&
    ((collection.value === "bookings" && (has("bookings") || has("portal"))) ||
      (collection.value === "sites" && (has("customers") || has("portal"))) ||
      (collection.value === "customers" && has("customers")) ||
      (["services", "extras"].includes(collection.value) && has("catalog")) ||
      (["workers", "availability", "timeOff"].includes(collection.value) &&
        has("workers")) ||
      (collection.value === "settlements" && has("finance"))),
);
const tabs = computed(
  () =>
    ({
      customers: [
        ["customers", t("客户", "Customers")],
        ["sites", t("场所", "Sites")],
      ],
      catalog: [
        ["services", t("服务价目", "Service rates")],
        ["extras", t("服务增项", "Extras")],
      ],
      workers: [
        ["workers", t("保洁人员", "Workers")],
        ["availability", t("每周时段", "Weekly hours")],
        ["timeOff", t("请假与不可安排", "Time off")],
      ],
      finance: [
        ["visits", t("上门收退款", "Visit balances")],
        ["settlements", t("人员结算", "Worker statements")],
      ],
      portal: [
        ["bookings", t("预约订单", "Bookings")],
        ["visits", t("每次上门", "Visits")],
        ["sites", t("服务场所", "Sites")],
      ],
      work: [
        ["visits", t("上门任务", "Visits")],
        ["settlements", t("我的结算", "My statements")],
      ],
    })[props.section] || [],
);
const rows = computed(() => {
  let all = props.data[collection.value] || [];
  if (collection.value === "visits") {
    if (filter.value) all = all.filter((v) => v.status === filter.value);
    if (props.section === "dispatch")
      all = all.filter(
        (v) =>
          localInput(v.startsAt, v.timezone || zone.value).slice(0, 10) ===
          day.value,
      );
    if (workerFilter.value)
      all = all.filter((v) =>
        v.crew.some((w) => String(w.id) === String(workerFilter.value)),
      );
  }
  return all;
});
const columns = computed(() => {
  switch (collection.value) {
    case "customers":
      return cols([
        ["name", "客户", "Customer"],
        ["contact", "联系方式", "Contact"],
        [
          "billingType",
          "结算方式",
          "Billing",
          (r) =>
            r.billingType === "MONTHLY"
              ? t("月结", "Monthly")
              : t("按次", "Per visit"),
        ],
        [
          "enabled",
          "状态",
          "Status",
          (r) => (r.enabled ? t("启用", "Active") : t("停用", "Disabled")),
        ],
      ]);
    case "sites":
      return cols([
        ["name", "场所", "Site"],
        [
          "customerId",
          "客户",
          "Customer",
          (r) => names(props.data.customers, r.customerId),
        ],
        ["address", "地址", "Address"],
        ["contact", "联系人", "Contact"],
      ]);
    case "services":
      return cols([
        ["name", "服务", "Service", localized],
        [
          "unit",
          "计价",
          "Unit",
          (r) =>
            ({
              FIXED: t("每次", "Per visit"),
              AREA: t("每平方米", "Per m²"),
              HOURLY: t("班组小时", "Crew hour"),
            })[r.unit],
        ],
        ["unitPrice", "单位价格", "Unit price"],
        ["basePrice", "基础费", "Base fee"],
        ["workersRequired", "人数", "Workers"],
        [
          "enabled",
          "状态",
          "Status",
          (r) => (r.enabled ? t("启用", "Active") : t("停用", "Disabled")),
        ],
      ]);
    case "extras":
      return cols([
        ["name", "增项", "Extra", localized],
        ["price", "价格", "Fee"],
        ["minutes", "追加分钟", "Extra minutes"],
        [
          "enabled",
          "状态",
          "Status",
          (r) => (r.enabled ? t("启用", "Active") : t("停用", "Disabled")),
        ],
      ]);
    case "workers":
      return cols([
        ["name", "人员", "Worker"],
        ["hourlyRate", "结算时薪", "Settlement rate"],
        [
          "enabled",
          "状态",
          "Status",
          (r) => (r.enabled ? t("启用", "Active") : t("停用", "Disabled")),
        ],
      ]);
    case "availability":
      return cols([
        [
          "workerId",
          "人员",
          "Worker",
          (r) => names(props.data.workers, r.workerId),
        ],
        ["weekday", "星期", "Weekday"],
        ["startTime", "开始", "From"],
        ["endTime", "结束", "To"],
      ]);
    case "timeOff":
      return cols([
        [
          "workerId",
          "人员",
          "Worker",
          (r) => names(props.data.workers, r.workerId),
        ],
        [
          "startsAt",
          "开始",
          "From",
          (r) =>
            dateTime(r.startsAt, r.timezone || zone.value) +
            (r.timezone ? " · " + r.timezone : ""),
        ],
        ["endsAt", "结束", "To", (r) => dateTime(r.endsAt, zone.value)],
        ["reason", "原因", "Reason"],
        [
          "enabled",
          "状态",
          "Status",
          (r) => (r.enabled ? t("生效", "Active") : t("已撤销", "Withdrawn")),
        ],
      ]);
    case "settlements":
      return cols([
        ["id", "结算单", "Statement", (r) => "#" + r.id],
        ["workerName", "人员", "Worker"],
        ["fromDate", "自", "From"],
        ["toDate", "至", "To"],
        ["amount", "金额", "Amount"],
        ["status", "状态", "Status"],
      ]);
    case "visits":
      return cols([
        [
          "startsAt",
          "上门时间",
          "Visit time",
          (r) =>
            dateTime(r.startsAt, r.timezone || zone.value) +
            (r.timezone ? " · " + r.timezone : ""),
        ],
        ["customerName", "客户", "Customer"],
        ["serviceName", "服务", "Service"],
        ["status", "状态", "Status"],
        [
          "crew",
          "人员",
          "Crew",
          (r) =>
            r.crew.map((w) => w.name).join(", ") || t("未派工", "Unassigned"),
        ],
        ...(has("finance") || has("portal")
          ? [
              ["due", "待收", "Due"],
              ["refundDue", "待退", "Refund due"],
            ]
          : []),
      ]);
    default:
      return cols([
        ["id", "订单", "Booking", (r) => "#" + r.id],
        ["serviceName", "服务", "Service"],
        [
          "customerId",
          "客户",
          "Customer",
          (r) => names(props.data.customers, r.customerId),
        ],
        ["occurrences", "次数", "Visits"],
        ["status", "状态", "Status"],
        [
          "createdAt",
          "申请时间",
          "Requested",
          (r) => dateTime(r.createdAt, zone.value),
        ],
      ]);
  }
});
function open(row) {
  if (collection.value === "bookings") emit("booking", row.id);
  else if (collection.value === "visits") emit("visit", row.id);
  else if (collection.value === "settlements") emit("settlement", row.id);
  else emit("form", createAction.value, row);
}
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p class="muted">
          {{
            section === "dispatch"
              ? zone
              : section === "portal"
                ? t(
                    "查看每次上门、报价及验收。",
                    "Track each visit, quote and acceptance.",
                  )
                : section === "work"
                  ? t(
                      "查看已指派任务及本人结算。",
                      "Your assigned visits and settlement statements.",
                    )
                  : data.currency
          }}
        </p>
      </div>
      <button
        v-if="section === 'portal'"
        :disabled="busy"
        @click="emit('statement')"
      >
        {{ t("月度对账", "Monthly statement") }}
      </button>
      <button
        v-if="allowCreate"
        class="primary"
        :disabled="busy"
        @click="emit('form', createAction, {})"
      >
        ＋
        {{
          collection === "bookings"
            ? t("预约保洁", "Request cleaning")
            : collection === "settlements"
              ? t("生成结算单", "Create statement")
              : t("新增", "Add")
        }}
      </button>
    </header>
    <nav v-if="tabs.length" class="tabs">
      <button
        v-for="[key, label] in tabs"
        :key="key"
        :class="{ active: collection === key }"
        :disabled="busy"
        @click="tab = key"
      >
        {{ label }}
      </button>
    </nav>
    <div v-if="collection === 'visits'" class="filter-strip">
      <label v-if="section === 'dispatch'"
        >{{ t("日期", "Date") }}<input v-model="day" type="date" /></label
      ><label
        >{{ t("状态", "Status")
        }}<select v-model="filter">
          <option value="">{{ t("全部状态", "All statuses") }}</option>
          <option
            v-for="s in [
              'REQUESTED',
              'READY',
              'ASSIGNED',
              'IN_PROGRESS',
              'REVIEW',
              'REWORK',
              'ACCEPTED',
              'CANCELLED',
              'SKIPPED',
            ]"
            :key="s"
            :value="s"
          >
            {{ stateName(s) }}
          </option>
        </select></label
      ><label v-if="section === 'dispatch'"
        >{{ t("人员", "Worker")
        }}<select v-model="workerFilter">
          <option value="">{{ t("全部人员", "All workers") }}</option>
          <option v-for="w in data.workers" :key="w.id" :value="w.id">
            {{ w.name }}
          </option>
        </select></label
      >
    </div>
    <RecordTable :rows="rows" :columns="columns" :busy="busy" @open="open" />
    <p
      v-if="
        !data.customers.length && has('customers') && section === 'bookings'
      "
      class="empty-guide"
    >
      {{
        t(
          "先在“客户与场所”建立客户和服务地址，再维护服务价目。",
          "Set up customers and service sites, then service rates, before requesting a booking.",
        )
      }}
    </p>
    <p v-if="section === 'finance'" class="footnote">
      {{
        t(
          "这里只登记外部已完成的收退款或付款，不会发起资金交易。",
          "These are records of externally completed transactions; no money is transferred here.",
        )
      }}
    </p>
  </section>
</template>
