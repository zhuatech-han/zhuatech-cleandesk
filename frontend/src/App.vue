<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { api, resetApi, download } from "./api.js";
import { createCommandRunner } from "./command-runner.js";
import {
  t,
  language,
  errorMessage,
  ask,
  confirmation,
  requestId,
  dateTime,
  stateName,
} from "./ui.js";
import { definition, submission } from "./forms.js";
import { dialogFocus } from "./dialog-focus.js";
import BusinessBoard from "./components/BusinessBoard.vue";
import FormPanel from "./components/FormPanel.vue";
import VisitDetail from "./components/VisitDetail.vue";
import AdminPanel from "./components/AdminPanel.vue";
import RecordTable from "./components/RecordTable.vue";
const empty = () => ({
  customers: [],
  sites: [],
  services: [],
  extras: [],
  workers: [],
  availability: [],
  timeOff: [],
  bookings: [],
  visits: [],
  settlements: [],
  categories: [],
  organizations: [],
  timezone: "Asia/Shanghai",
  currency: "CNY",
});
const me = ref(null),
  data = ref(empty()),
  bindings = ref([]),
  section = ref(""),
  busy = ref(false),
  reading = ref(false),
  available = ref(false),
  message = ref(""),
  errorCode = ref(""),
  username = ref(""),
  password = ref(""),
  form = ref(null),
  detail = ref(null),
  adminRef = ref(null),
  about = ref(false),
  passwordForm = ref(false),
  oldPassword = ref(""),
  newPassword = ref(""),
  unknown = ref(false),
  report = ref(null),
  reportLoading = ref(false),
  month = ref(new Date().toISOString().slice(0, 7)),
  reportCustomer = ref(""),
  auditRows = ref([]);
let generation = 0,
  detailGeneration = 0;
const runner = createCommandRunner(api, requestId);
const has = (c) => me.value?.permissions.includes(c);
const disabled = computed(
  () =>
    busy.value ||
    reading.value ||
    reportLoading.value ||
    unknown.value ||
    !available.value,
);
const title = computed(() => {
  const m = me.value?.menus.find((m) => m.code === section.value);
  return m
    ? language.value === "en"
      ? m.nameEn
      : m.name
    : section.value === "reports"
      ? t("月度服务对账", "Monthly service statement")
      : "CleanDesk";
});
function fail(e) {
  errorCode.value = e.message;
  message.value = errorMessage(e);
  if (e.status === 401) {
    me.value = null;
    data.value = empty();
    detail.value = null;
    detailGeneration++;
    form.value = null;
    passwordForm.value = false;
    report.value = null;
    auditRows.value = [];
    unknown.value = false;
    runner.clear();
    if (confirmation.value) {
      confirmation.value.resolve(false);
      confirmation.value = null;
    }
    available.value = false;
    generation++;
    resetApi();
  }
}
function recover() {
  if (errorCode.value === "NETWORK_ERROR") {
    errorCode.value = "";
    message.value = "";
  }
}
async function load() {
  if (!me.value) return;
  const seq = ++generation;
  reading.value = true;
  available.value = false;
  try {
    const results = await Promise.all([
      api("/workspace"),
      has("customers") || has("workers")
        ? api("/binding-options")
        : Promise.resolve([]),
    ]);
    if (seq !== generation) return;
    data.value = results[0];
    if (data.value.profile) {
      me.value = data.value.profile;
      if (
        !me.value.menus.some((m) => m.code === section.value) &&
        !(section.value === "reports" && has("portal"))
      )
        section.value = me.value.menus[0]?.code || "";
    }
    bindings.value = results[1];
    available.value = true;
    recover();
  } catch (e) {
    if (seq === generation) fail(e);
  } finally {
    if (seq === generation) reading.value = false;
  }
}
async function login() {
  busy.value = true;
  message.value = "";
  try {
    const profile = await api("/auth/login", {
      method: "POST",
      body: { username: username.value, password: password.value },
    });
    password.value = "";
    resetApi();
    me.value = profile;
    section.value = profile.menus[0]?.code || "";
    await load();
  } catch (e) {
    password.value = "";
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function leaveForm() {
  if (busy.value || unknown.value) return false;
  if (
    form.value &&
    !(await ask(t("放弃尚未保存的修改？", "Discard this unsaved form?")))
  )
    return false;
  form.value = null;
  return true;
}
async function changeSection(code) {
  if (
    disabled.value ||
    !(await leaveForm()) ||
    (adminRef.value && !(await adminRef.value.canLeave()))
  )
    return;
  detail.value = null;
  detailGeneration++;
  section.value = code;
  message.value = "";
  if (code === "audit")
    try {
      auditRows.value = await api("/audit");
    } catch (e) {
      fail(e);
    }
  if (code === "reports") await loadReport();
}
async function logout() {
  if (
    busy.value ||
    unknown.value ||
    !(await leaveForm()) ||
    (adminRef.value && !(await adminRef.value.canLeave()))
  )
    return;
  busy.value = true;
  try {
    await api("/auth/logout", { method: "POST" });
    me.value = null;
    data.value = empty();
    bindings.value = [];
    detail.value = null;
    generation++;
    available.value = false;
    resetApi();
    message.value = "";
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
function openForm(action, record = {}) {
  if (disabled.value) return;
  message.value = "";
  form.value = definition(action, data.value, bindings.value, record, me.value);
}
async function removeAvailability() {
  if (
    !(await ask(
      t(
        "删除此工作时段？已有派工占用时无法删除。",
        "Remove this availability? Existing assignments must be rearranged first.",
      ),
    ))
  )
    return;
  const result = await write("availability.remove", {
    id: form.value.initial.id,
    version: form.value.initial.version,
  });
  if (result) form.value = null;
}
async function refreshDetail() {
  if (!detail.value) return;
  await openDetail(detail.value.kind, detail.value.id);
}
async function openDetail(kind, id) {
  const seq = ++detailGeneration;
  try {
    const value = await api("/" + kind + "/" + id);
    if (seq !== detailGeneration) return;
    detail.value = { kind, id, data: value };
  } catch (e) {
    if (seq === detailGeneration) fail(e);
  }
}
async function write(action, body) {
  if (busy.value || unknown.value) return null;
  busy.value = true;
  message.value = "";
  try {
    const result = await runner.send(action, body);
    await load();
    await refreshDetail();
    return result;
  } catch (e) {
    unknown.value = !!runner.pending;
    fail(e);
    return null;
  } finally {
    busy.value = false;
  }
}
async function submit(values) {
  const action = form.value.action;
  const result = await write(action, submission(action, values, data.value));
  if (result) form.value = null;
}
async function adminWrite(path, method, body) {
  const parts = path.split("/");
  return write("admin.save", {
    ...body,
    kind: parts[2],
    ...(method === "PUT" ? { id: Number(parts[3]) } : {}),
  });
}
async function resolveUnknown(retry = false) {
  if (busy.value) return;
  busy.value = true;
  try {
    const result = retry ? await runner.retry() : await runner.check();
    unknown.value = !!runner.pending;
    if (result) {
      message.value = t("已确认保存结果。", "Saved result confirmed.");
      form.value = null;
      await load();
      await refreshDetail();
    } else
      message.value = t(
        "尚未找到保存结果，可稍后再核对，或原样重发同一请求。",
        "No saved result found yet. Check later or resend the same request.",
      );
  } catch (e) {
    unknown.value = !!runner.pending;
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function uploadPhoto(payload) {
  if (disabled.value) return;
  busy.value = true;
  message.value = "";
  try {
    const fd = new FormData();
    fd.append("phase", payload.phase);
    fd.append("caption", payload.caption);
    fd.append("file", payload.file);
    await api("/rounds/" + payload.id + "/photos", {
      method: "POST",
      body: fd,
    });
    await refreshDetail();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function loadReport() {
  if (!me.value || reportLoading.value) return;
  reportLoading.value = true;
  report.value = null;
  try {
    report.value = await api(
      "/reports?month=" +
        encodeURIComponent(month.value) +
        (reportCustomer.value
          ? "&customerId=" + encodeURIComponent(reportCustomer.value)
          : ""),
    );
  } catch (e) {
    fail(e);
  } finally {
    reportLoading.value = false;
  }
}
async function refreshPage() {
  if (adminRef.value && !(await adminRef.value.canLeave())) return;
  await load();
  if (!available.value) return;
  if (adminRef.value) await adminRef.value.refresh();
  if (section.value === "reports") await loadReport();
  if (section.value === "audit") {
    try {
      auditRows.value = await api("/audit");
    } catch (e) {
      fail(e);
    }
  }
  await refreshDetail();
}
async function exportReport() {
  try {
    await download(
      "/reports.csv?month=" +
        encodeURIComponent(month.value) +
        (reportCustomer.value
          ? "&customerId=" + encodeURIComponent(reportCustomer.value)
          : ""),
      "cleaning-" + month.value + ".csv",
    );
  } catch (e) {
    fail(e);
  }
}
async function changePassword() {
  busy.value = true;
  message.value = "";
  try {
    await api("/auth/password", {
      method: "POST",
      body: { oldPassword: oldPassword.value, newPassword: newPassword.value },
    });
    passwordForm.value = false;
    me.value = null;
    resetApi();
    message.value = t(
      "密码已更改，请重新登录。",
      "Password changed. Sign in again.",
    );
  } catch (e) {
    fail(e);
  } finally {
    oldPassword.value = "";
    newPassword.value = "";
    busy.value = false;
  }
}
function closeDetail() {
  if (disabled.value) return;
  detail.value = null;
  detailGeneration++;
}
function toggleLanguage() {
  language.value = language.value === "zh" ? "en" : "zh";
  localStorage.setItem("cleandesk-language", language.value);
}
let disposeFocus;
onMounted(async () => {
  disposeFocus = dialogFocus(() => {
    if (confirmation.value) {
      confirmation.value.resolve(false);
      confirmation.value = null;
    } else if (form.value) leaveForm();
    else if (!busy.value) {
      about.value = false;
      passwordForm.value = false;
      closeDetail();
    }
  });
  try {
    me.value = await api("/auth/me");
    section.value = me.value.menus[0]?.code || "";
    await load();
  } catch (e) {
    if (e.status !== 401) fail(e);
  }
});
onBeforeUnmount(() => {
  generation++;
  detailGeneration++;
  disposeFocus?.();
});
const bookingData = computed(() =>
  detail.value?.kind === "bookings" ? detail.value.data : null,
);
const settlementData = computed(() =>
  detail.value?.kind === "settlements" ? detail.value.data : null,
);
const date = (v) => dateTime(v, data.value.timezone);
const reportColumns = computed(() => [
  {
    key: "startsAt",
    label: t("上门时间", "Visit"),
    render: (r) => date(r.startsAt),
  },
  { key: "customerName", label: t("客户", "Customer") },
  { key: "status", label: t("状态", "Status") },
  { key: "charge", label: t("应收", "Charge") },
  { key: "paid", label: t("净收款", "Net receipts") },
  { key: "due", label: t("待收", "Due") },
  { key: "refundDue", label: t("待退", "Refund due") },
]);
</script>
<template>
  <div v-if="!me" class="login-layout">
    <main class="login-card">
      <img src="/brand/logo.jpg" width="44" height="44" alt="知华科技" />
      <h1>CleanDesk</h1>
      <p class="muted">
        {{ t("保洁预约与服务管理", "Cleaning booking & service management") }}
      </p>
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <form autocomplete="off" @submit.prevent="login">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="username"
            required
            maxlength="60"
            autocomplete="off"
            :disabled="busy" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="password"
            type="password"
            required
            maxlength="72"
            autocomplete="off"
            :disabled="busy" /></label
        ><button class="primary full" :disabled="busy">
          {{ busy ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
      <footer>
        <button class="text-button" @click="toggleLanguage">
          {{ language === "zh" ? "English" : "中文" }}</button
        ><button class="text-button" @click="about = true">
          {{ t("关于与授权", "About & license") }}
        </button>
      </footer>
    </main>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img
          src="/brand/logo.jpg"
          width="34"
          height="34"
          alt="知华科技"
        /><strong>CleanDesk</strong>
      </div>
      <nav :aria-label="t('业务导航', 'Navigation')">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: section === m.code }"
          :disabled="busy || reading || unknown"
          @click="changeSection(m.code)"
        >
          {{ language === "zh" ? m.name : m.nameEn }}
        </button>
      </nav>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技", "ZhiHua Technology")
        }}</a
        ><button class="text-button" @click="about = true">
          {{ t("关于与授权", "About & license") }}
        </button>
      </footer>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <span>{{ title }}</span>
        <div class="actions">
          <button
            class="text-button"
            :disabled="busy || !!form"
            @click="toggleLanguage"
          >
            {{ language === "zh" ? "English" : "中文" }}</button
          ><span>{{ me.displayName }}</span
          ><button :disabled="busy || unknown" @click="passwordForm = true">
            {{ t("修改密码", "Password") }}</button
          ><button :disabled="busy || unknown" @click="logout">
            {{ t("退出", "Sign out") }}
          </button>
        </div>
      </header>
      <main class="content">
        <div class="refresh-row">
          <span class="muted">{{ data.timezone }} · {{ data.currency }}</span
          ><button :disabled="busy || reading" @click="refreshPage">
            {{ reading ? t("读取中…", "Loading…") : t("刷新", "Refresh") }}
          </button>
        </div>
        <div
          v-if="message"
          class="notice"
          :class="{ error: errorCode }"
          role="alert"
        >
          <p>{{ message }}</p>
          <div v-if="unknown" class="actions">
            <button :disabled="busy" @click="resolveUnknown(false)">
              {{ t("核对保存结果", "Check saved result") }}</button
            ><button :disabled="busy" @click="resolveUnknown(true)">
              {{ t("原样重发同一请求", "Resend this same request") }}
            </button>
          </div>
        </div>
        <AdminPanel
          v-if="['users', 'roles', 'settings'].includes(section)"
          ref="adminRef"
          :section="section"
          :pending="disabled"
          :message="message"
          :perform="adminWrite"
          @error="fail"
          @recovered="recover"
        />
        <section v-else-if="section === 'reports'">
          <header class="page-heading">
            <div>
              <h1>{{ t("月度服务对账", "Monthly service statement") }}</h1>
              <p class="muted">
                {{
                  t(
                    "按上门日期归月，展示当前已登记的收退款。",
                    "Grouped by visit date, showing currently recorded receipts and refunds.",
                  )
                }}
              </p>
            </div>
            <button :disabled="disabled || !report" @click="exportReport">
              {{ t("导出CSV", "Export CSV") }}
            </button>
          </header>
          <form class="filter-strip" @submit.prevent="loadReport">
            <label
              >{{ t("月份", "Month")
              }}<input
                v-model="month"
                type="month"
                required
                :disabled="reportLoading"
                @change="report = null" /></label
            ><label v-if="!has('portal')"
              >{{ t("客户", "Customer")
              }}<select
                v-model="reportCustomer"
                :disabled="reportLoading"
                @change="report = null"
              >
                <option value="">{{ t("全部客户", "All customers") }}</option>
                <option v-for="c in data.customers" :key="c.id" :value="c.id">
                  {{ c.name }}
                </option>
              </select></label
            ><button class="primary" :disabled="disabled">
              {{ t("查询", "View statement") }}
            </button>
          </form>
          <p v-if="reportLoading" role="status">
            {{ t("正在查询对账…", "Loading statement…") }}
          </p>
          <template v-if="report"
            ><div class="metrics">
              <div>
                <span>{{ t("应收", "Charges") }}</span
                ><strong>{{ data.currency }} {{ report.charge }}</strong>
              </div>
              <div>
                <span>{{ t("已登记净收款", "Net receipts") }}</span
                ><strong>{{ report.paid }}</strong>
              </div>
              <div>
                <span>{{ t("待收", "Balance due") }}</span
                ><strong>{{ report.due }}</strong>
              </div>
              <div>
                <span>{{ t("待退", "Refund due") }}</span
                ><strong>{{ report.refundDue }}</strong>
              </div>
            </div>
            <RecordTable
              :rows="report.rows"
              :columns="reportColumns"
              :busy="disabled"
              @open="(r) => openDetail('visits', r.id)"
          /></template>
          <p class="footnote">
            {{
              t(
                "收付款凭证由工作人员登记，本系统不核验银行到账，也不生成税务发票。",
                "Staff record external transaction references. This system does not verify bank settlement or issue tax invoices.",
              )
            }}
          </p>
        </section>
        <section v-else-if="section === 'audit'">
          <header class="page-heading">
            <h1>{{ t("操作审计", "Audit history") }}</h1>
          </header>
          <RecordTable
            :rows="auditRows"
            :busy="disabled"
            :columns="[
              {
                key: 'createdAt',
                label: t('时间', 'Time'),
                render: (r) => date(r.createdAt),
              },
              { key: 'actor', label: t('操作账号', 'Actor') },
              { key: 'action', label: t('动作', 'Action') },
              { key: 'objectId', label: t('记录', 'Record') },
            ]"
            :openable="false"
          />
        </section>
        <BusinessBoard
          v-else-if="available"
          :section="section"
          :data="data"
          :profile="me"
          :busy="disabled"
          @form="openForm"
          @booking="(id) => openDetail('bookings', id)"
          @visit="(id) => openDetail('visits', id)"
          @settlement="(id) => openDetail('settlements', id)"
          @statement="changeSection('reports')"
        />
        <div v-else class="empty">
          {{
            reading
              ? t("正在读取工作资料…", "Loading your workspace…")
              : t(
                  "工作资料尚未读取，请刷新重试。",
                  "Workspace unavailable. Refresh to try again.",
                )
          }}
        </div>
      </main>
    </div>
  </div>
  <VisitDetail
    v-if="detail?.kind === 'visits'"
    :data="detail.data"
    :profile="me"
    :busy="disabled"
    :zone="data.timezone"
    :currency="data.currency"
    :message="message"
    @close="closeDetail"
    @action="openForm"
    @photo="uploadPhoto"
  />
  <div v-if="bookingData" class="modal-backdrop detail-backdrop">
    <section
      class="modal detail-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('预约订单', 'Booking details')"
    >
      <header>
        <h2>{{ t("预约订单", "Booking") }} #{{ bookingData.booking.id }}</h2>
        <button :disabled="disabled" @click="closeDetail">
          {{ t("关闭", "Close") }}
        </button>
      </header>
      <div class="detail-body">
        <h3>{{ bookingData.booking.serviceName }}</h3>
        <p>
          {{ bookingData.customer.name }} ·
          <span class="badge">{{ stateName(bookingData.booking.status) }}</span>
        </p>
        <p>
          {{ t("每次原价", "Original charge per visit") }} {{ data.currency }}
          {{ bookingData.booking.unitVisitPrice }} ·
          {{ t("每次优惠", "Discount per visit") }}
          {{ bookingData.booking.discount }}
        </p>
        <p>
          {{ t("人数", "Crew size") }}
          {{ bookingData.booking.workersRequired }} ·
          {{ t("每次分钟", "Minutes per visit") }}
          {{ bookingData.booking.durationMinutes }} ·
          {{ t("总次数", "Visits") }} {{ bookingData.booking.occurrences }}
        </p>
        <p class="muted">{{ bookingData.booking.quoteNote }}</p>
        <div class="actions">
          <button
            v-if="
              has('bookings') &&
              ['SUBMITTED', 'QUOTED'].includes(bookingData.booking.status)
            "
            class="primary"
            :disabled="disabled"
            @click="openForm('booking.quote', bookingData.booking)"
          >
            {{ t("编辑并发送报价", "Edit & send quote") }}</button
          ><button
            v-if="
              (has('portal') || has('bookings')) &&
              bookingData.booking.status === 'QUOTED'
            "
            class="primary"
            :disabled="disabled"
            @click="openForm('booking.confirm', bookingData.booking)"
          >
            {{
              has("portal")
                ? t("确认报价", "Confirm quote")
                : t("登记客户线下确认", "Record customer offline confirmation")
            }}
          </button>
        </div>
        <RecordTable
          :rows="bookingData.visits"
          :columns="reportColumns"
          :busy="disabled"
          @open="(r) => openDetail('visits', r.id)"
        />
      </div>
    </section>
  </div>
  <div v-if="settlementData" class="modal-backdrop detail-backdrop">
    <section
      class="modal detail-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('人员结算单', 'Worker statement')"
    >
      <header>
        <h2>
          {{ t("人员结算单", "Worker statement") }} #{{
            settlementData.settlement.id
          }}
        </h2>
        <button :disabled="disabled" @click="closeDetail">
          {{ t("关闭", "Close") }}
        </button>
      </header>
      <div class="detail-body">
        <h3>{{ settlementData.settlement.workerName }}</h3>
        <p>
          {{ settlementData.settlement.fromDate }} —
          {{ settlementData.settlement.toDate }}
        </p>
        <p>
          <span class="badge">{{
            stateName(settlementData.settlement.status)
          }}</span>
          ·
          <strong
            >{{ data.currency }} {{ settlementData.settlement.amount }}</strong
          >
        </p>
        <p v-if="settlementData.settlement.paymentReference">
          {{ t("付款凭证", "Payment reference") }}：{{
            settlementData.settlement.paymentReference
          }}
        </p>
        <div class="actions">
          <button
            v-if="has('work') && settlementData.settlement.status === 'DRAFT'"
            :disabled="disabled"
            class="primary"
            @click="openForm('settlement.confirm', settlementData.settlement)"
          >
            {{ t("本人确认结算金额", "Confirm my statement") }}</button
          ><button
            v-if="
              has('finance') && settlementData.settlement.status === 'CONFIRMED'
            "
            :disabled="disabled"
            class="primary"
            @click="openForm('settlement.pay', settlementData.settlement)"
          >
            {{ t("登记已完成付款", "Record completed payment") }}</button
          ><button
            v-if="
              has('finance') &&
              ['DRAFT', 'CONFIRMED'].includes(settlementData.settlement.status)
            "
            :disabled="disabled"
            @click="openForm('settlement.cancel', settlementData.settlement)"
          >
            {{ t("撤销未付单", "Cancel unpaid statement") }}
          </button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("上门", "Visit") }}</th>
                <th>{{ t("轮次", "Round") }}</th>
                <th>{{ t("完成", "Completed") }}</th>
                <th>{{ t("分钟", "Minutes") }}</th>
                <th>{{ t("时薪", "Rate") }}</th>
                <th>{{ t("金额", "Amount") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in settlementData.lines" :key="a.id">
                <td>#{{ a.visitId }}</td>
                <td>{{ a.roundNo }}</td>
                <td>{{ date(a.completedAt) }}</td>
                <td>{{ a.actualMinutes }}</td>
                <td>{{ a.hourlyRate }}</td>
                <td>{{ a.cost }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
  <FormPanel
    v-if="form"
    :key="form.action + '-' + (form.initial.id || 'new')"
    :title="form.title"
    :fields="form.fields"
    :initial="form.initial"
    :busy="busy || unknown"
    :removable="form.action === 'availability.save' && !!form.initial.id"
    :message="message"
    @remove="removeAvailability"
    @submit="submit"
    @close="leaveForm"
  />
  <div v-if="passwordForm" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('修改密码', 'Change password')"
    >
      <header>
        <h2>{{ t("修改密码", "Change password") }}</h2>
        <button :disabled="busy" @click="passwordForm = false">
          {{ t("关闭", "Close") }}
        </button>
      </header>
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <form @submit.prevent="changePassword">
        <label
          >{{ t("原密码", "Current password")
          }}<input
            v-model="oldPassword"
            type="password"
            required
            maxlength="72"
            autocomplete="off"
            :disabled="busy" /></label
        ><label
          >{{ t("新密码", "New password")
          }}<input
            v-model="newPassword"
            type="password"
            required
            minlength="12"
            maxlength="72"
            autocomplete="off"
            :disabled="busy"
        /></label>
        <p class="muted">
          {{
            t(
              "12–72字节，包含大写、小写字母和数字。",
              "12–72 bytes, uppercase, lowercase and a digit.",
            )
          }}
        </p>
        <footer class="actions">
          <button class="primary" :disabled="busy">
            {{ t("保存并重新登录", "Save & sign in again") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="about" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('关于与授权', 'About & license')"
    >
      <header>
        <h2>CleanDesk 1.0.0</h2>
        <button @click="about = false">{{ t("关闭", "Close") }}</button>
      </header>
      <p>
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
          )
        }}
      </p>
      <p>
        {{
          t(
            "公开源码学习版／非商业使用。仅限个人学习交流，未经公司书面授权不得商用。",
            "Source-available for personal non-commercial learning. Commercial use requires written authorization.",
          )
        }}
      </p>
      <p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >https://www.zhuatech.cn/</a
        >
      </p>
      <template v-if="language === 'zh'"
        ><p>
          商业授权或深度定制开发请联系知华科技，微信 zhuatech / zhuatech2。
        </p>
        <div class="contact-qr">
          <figure>
            <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
            <figcaption>zhuatech</figcaption>
          </figure>
          <figure>
            <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
            <figcaption>zhuatech2</figcaption>
          </figure>
        </div></template
      >
      <p v-else>
        <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a><br /><a
          href="mailto:jack@zhuatech.cn"
          >jack@zhuatech.cn</a
        ><br /><a href="https://wa.me/8617521234993"
          >WhatsApp +86 17521234993</a
        >
      </p>
    </section>
  </div>
  <div v-if="unknown" class="modal-backdrop unknown-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('核对保存结果', 'Check saved result')"
    >
      <h2>{{ t("提交结果待确认", "Submission result is uncertain") }}</h2>
      <p>{{ message }}</p>
      <footer class="actions">
        <button :disabled="busy" @click="resolveUnknown(false)">
          {{ t("核对保存结果", "Check saved result") }}</button
        ><button class="primary" :disabled="busy" @click="resolveUnknown(true)">
          {{ t("原样重发同一请求", "Resend this same request") }}
        </button>
      </footer>
    </section>
  </div>
  <div v-if="confirmation" class="modal-backdrop confirm-backdrop">
    <section
      class="modal confirm-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('确认操作', 'Confirm action')"
    >
      <h2>{{ t("确认操作", "Confirm action") }}</h2>
      <p>{{ confirmation.message }}</p>
      <footer class="actions">
        <button
          @click="
            confirmation.resolve(false);
            confirmation = null;
          "
        >
          {{ t("返回", "Back") }}</button
        ><button
          class="primary"
          @click="
            confirmation.resolve(true);
            confirmation = null;
          "
        >
          {{ t("确认", "Confirm") }}
        </button>
      </footer>
    </section>
  </div>
</template>
