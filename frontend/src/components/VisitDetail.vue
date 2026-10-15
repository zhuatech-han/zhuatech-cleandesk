<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, ref } from "vue";
import { t, dateTime, stateName, eventName } from "../ui.js";
const props = defineProps({
  data: { type: Object, required: true },
  profile: { type: Object, required: true },
  busy: Boolean,
  zone: { type: String, default: "" },
  currency: { type: String, default: "" },
  message: { type: String, default: "" },
});
const emit = defineEmits(["close", "action", "photo"]);
const phase = ref("BEFORE"),
  caption = ref(""),
  file = ref(null);
const v = computed(() => props.data.visit);
const visitZone = computed(() => v.value.timezone || props.zone);
const has = (code) => props.profile.permissions.includes(code);
const current = computed(() =>
  props.data.rounds.find((r) => r.id === v.value.roundId),
);
const canUpload = computed(
  () =>
    has("work") &&
    current.value &&
    ["ASSIGNED", "IN_PROGRESS"].includes(current.value.status),
);
const actions = computed(() => {
  const s = v.value.status,
    a = [];
  if (has("dispatch") && ["READY", "ASSIGNED", "REWORK"].includes(s))
    a.push(["visit.assign", t("安排人员／替班", "Assign / replace crew")]);
  if ((has("portal") || has("dispatch")) && ["READY", "ASSIGNED"].includes(s))
    a.push(["visit.reschedule", t("改期", "Reschedule")]);
  if (
    (has("portal") || has("dispatch")) &&
    ["REQUESTED", "READY", "ASSIGNED"].includes(s) &&
    !props.data.rounds.some((r) => r.completedAt)
  )
    a.push(["visit.cancel", t("取消此次上门", "Cancel this visit")]);
  if (has("work") && v.value.isLeader) {
    if (s === "ASSIGNED") a.push(["visit.start", t("开始作业", "Start work")]);
    if (s === "IN_PROGRESS")
      a.push(["visit.complete", t("提交完工", "Complete work")]);
  }
  if ((has("portal") || has("bookings")) && s === "REVIEW") {
    a.push([
      "visit.accept",
      has("portal")
        ? t("验收通过", "Accept service")
        : t("登记线下验收", "Record offline acceptance"),
    ]);
    a.push(["visit.rework", t("要求返工", "Request rework")]);
  }
  if (has("finance") && v.value.orderStatus === "CONFIRMED") {
    if (Number(v.value.due) > 0 && !["CANCELLED", "SKIPPED"].includes(s))
      a.push([
        "money.record",
        t("登记收款", "Record receipt"),
        { visitId: v.value.id, kind: "RECEIPT" },
      ]);
    if (Number(v.value.refundDue) > 0)
      a.push([
        "money.record",
        t("登记退款", "Record refund"),
        { visitId: v.value.id, kind: "REFUND" },
      ]);
  }
  return a;
});
function upload() {
  if (file.value)
    emit("photo", {
      id: v.value.roundId,
      phase: phase.value,
      caption: caption.value,
      file: file.value,
    });
}
</script>
<template>
  <div class="modal-backdrop detail-backdrop">
    <section
      class="modal detail-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('上门详情', 'Visit details')"
    >
      <header>
        <div>
          <p class="eyebrow">
            {{ t("上门记录", "VISIT") }} #{{ v.id }} ·
            {{ t("订单", "Booking") }} #{{ v.bookingId }}
          </p>
          <h2>{{ v.serviceName }}</h2>
        </div>
        <button :disabled="busy" @click="emit('close')">
          {{ t("关闭", "Close") }}
        </button>
      </header>
      <div class="detail-body">
        <p v-if="message" role="alert" class="form-error">{{ message }}</p>
        <div class="detail-summary">
          <span class="badge" :class="v.status.toLowerCase()">{{
            stateName(v.status)
          }}</span>
          <p>
            {{ dateTime(v.startsAt, visitZone) }} ({{ visitZone }}) —
            {{ dateTime(v.endsAt, visitZone) }}
          </p>
          <p>{{ v.customerName }} · {{ v.site.name }}</p>
          <p>{{ v.site.address }}</p>
          <p>{{ v.site.contact }}</p>
          <p v-if="v.site.instructions" class="muted">
            {{ v.site.instructions }}
          </p>
          <p>
            {{ t("班组", "Crew") }}：{{
              v.crew?.map((w) => w.name).join("、") ||
              t("等待安排", "Not yet assigned")
            }}
          </p>
          <div v-if="v.charge != null" class="amount-strip">
            <span>{{ t("应收", "Charge") }} {{ currency }} {{ v.charge }}</span
            ><span>{{ t("已登记净收款", "Net receipts") }} {{ v.paid }}</span
            ><strong v-if="Number(v.due) > 0"
              >{{ t("待收", "Due") }} {{ v.due }}</strong
            ><strong v-if="Number(v.refundDue) > 0"
              >{{ t("待退", "Refund due") }} {{ v.refundDue }}</strong
            >
          </div>
        </div>
        <div class="actions detail-actions">
          <button
            v-for="a in actions"
            :key="a[0]"
            :disabled="busy"
            :class="a[0] === 'visit.cancel' ? 'danger' : ''"
            @click="emit('action', a[0], { ...v, ...a[2] })"
          >
            {{ a[1] }}
          </button>
        </div>
        <section v-if="canUpload" class="work-upload">
          <h3>{{ t("上传现场照片", "Upload work photo") }}</h3>
          <form @submit.prevent="upload">
            <div class="form-grid">
              <label
                >{{ t("阶段", "Phase")
                }}<select v-model="phase" :disabled="busy">
                  <option value="BEFORE">
                    {{ t("作业前", "Before work") }}
                  </option>
                  <option
                    value="AFTER"
                    :disabled="current.status !== 'IN_PROGRESS'"
                  >
                    {{ t("作业后", "After work") }}
                  </option>
                </select></label
              ><label
                >{{ t("照片说明", "Caption")
                }}<input v-model="caption" maxlength="300" :disabled="busy"
              /></label>
            </div>
            <label
              >{{ t("PNG / JPEG，最大8MB", "PNG / JPEG, up to 8MB")
              }}<input
                type="file"
                accept="image/png,image/jpeg"
                required
                :disabled="busy"
                @change="file = $event.target.files[0] || null" /></label
            ><button class="primary" :disabled="busy || !file">
              {{ t("上传照片", "Upload photo") }}
            </button>
          </form>
        </section>
        <section
          v-for="r in [...data.rounds].reverse()"
          :key="r.id"
          class="round-record"
        >
          <h3>
            {{ t("作业轮次", "Work round") }} {{ r.roundNo }}
            <span class="badge">{{ stateName(r.status) }}</span>
          </h3>
          <p class="muted">
            {{ dateTime(r.startsAt, visitZone) }} · {{ t("开始", "Started") }}
            {{ dateTime(r.startedAt, visitZone) }} ·
            {{ t("完工", "Completed") }}
            {{ dateTime(r.completedAt, visitZone) }}
          </p>
          <p>{{ r.completionNote }}</p>
          <p v-if="r.acceptanceNote">
            {{ t("验收反馈", "Feedback") }}：{{ r.acceptanceNote }}
          </p>
          <div class="photo-grid">
            <figure
              v-for="p in [...r.photos].sort((a, b) =>
                a.phase === b.phase
                  ? a.id - b.id
                  : a.phase === 'BEFORE'
                    ? -1
                    : 1,
              )"
              :key="p.id"
            >
              <a :href="'/api/photos/' + p.id" target="_blank" rel="noopener"
                ><img
                  :src="'/api/photos/' + p.id"
                  :alt="
                    (p.phase === 'BEFORE'
                      ? t('作业前', 'Before')
                      : t('作业后', 'After')) +
                    ' ' +
                    p.caption
                  "
                  loading="lazy"
              /></a>
              <figcaption>
                {{
                  p.phase === "BEFORE"
                    ? t("作业前", "Before")
                    : t("作业后", "After")
                }}
                · {{ p.caption }}
              </figcaption>
            </figure>
          </div>
          <div class="table-wrap" v-if="r.assignments.length">
            <table>
              <thead>
                <tr>
                  <th>{{ t("人员", "Worker") }}</th>
                  <th>{{ t("实际分钟", "Actual minutes") }}</th>
                  <th v-if="r.assignments[0].cost != null">
                    {{ t("成本", "Cost") }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in r.assignments" :key="a.id">
                  <td>{{ a.workerName }}</td>
                  <td>{{ a.actualMinutes || "—" }}</td>
                  <td v-if="a.cost != null">{{ currency }} {{ a.cost }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section v-if="data.money.length">
          <h3>
            {{ t("外部收退款记录", "External receipt and refund records") }}
          </h3>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("类型", "Type") }}</th>
                  <th>{{ t("金额", "Amount") }}</th>
                  <th>{{ t("凭证", "Reference") }}</th>
                  <th>{{ t("登记时间", "Recorded") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in data.money" :key="m.id">
                  <td>
                    {{
                      m.kind === "RECEIPT"
                        ? t("收款", "Receipt")
                        : t("退款", "Refund")
                    }}
                  </td>
                  <td>{{ currency }} {{ m.amount }}</td>
                  <td>{{ m.reference }}</td>
                  <td>{{ dateTime(m.createdAt, visitZone) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section>
          <h3>{{ t("服务动态", "Service history") }}</h3>
          <ol class="timeline">
            <li v-for="e in [...data.events].reverse()" :key="e.id">
              <strong>{{ eventName(e.action) }}</strong
              ><span
                >{{ e.actor }} · {{ dateTime(e.createdAt, visitZone) }}</span
              >
              <p v-if="e.note">{{ e.note }}</p>
            </li>
          </ol>
        </section>
      </div>
    </section>
  </div>
</template>
