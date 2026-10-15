<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, ref, watch } from "vue";
import { t, stateName } from "../ui.js";
const props = defineProps({
  rows: { type: Array, required: true },
  columns: { type: Array, required: true },
  busy: Boolean,
  openable: { type: Boolean, default: true },
  searchLabel: { type: String, default: "" },
});
defineEmits(["open"]);
const query = ref("");
const page = ref(1);
const descending = ref(true);
const sort = ref(props.columns[0]?.sort || props.columns[0]?.key || "id");
watch(query, () => (page.value = 1));
watch(
  () => props.rows,
  () => (page.value = 1),
);
const filtered = computed(() =>
  props.rows
    .filter((r) =>
      JSON.stringify(r).toLowerCase().includes(query.value.toLowerCase()),
    )
    .sort((a, b) => {
      const av = a[sort.value] ?? "",
        bv = b[sort.value] ?? "";
      return (
        (typeof av === "number" && typeof bv === "number"
          ? av - bv
          : String(av).localeCompare(String(bv), undefined, {
              numeric: true,
            })) * (descending.value ? -1 : 1)
      );
    }),
);
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
function sortBy(key) {
  if (sort.value === key) descending.value = !descending.value;
  else {
    sort.value = key;
    descending.value = false;
  }
}
</script>
<template>
  <section class="panel record-panel">
    <div class="toolbar">
      <label class="search"
        ><input
          v-model="query"
          :aria-label="searchLabel || t('搜索记录', 'Search records')"
          :placeholder="
            searchLabel || t('搜索记录', 'Search records')
          " /></label
      ><span class="muted">{{ filtered.length }} {{ t("条", "records") }}</span>
    </div>
    <div class="record-cards">
      <article v-for="r in shown" :key="r.id" class="record-card">
        <dl>
          <template v-for="c in columns" :key="c.key"
            ><dt>{{ c.label }}</dt>
            <dd>
              <span
                v-if="c.key === 'status'"
                class="badge"
                :class="r.status.toLowerCase()"
                >{{ stateName(r.status) }}</span
              ><template v-else>{{
                c.render ? c.render(r) : (r[c.key] ?? "—")
              }}</template>
            </dd></template
          >
        </dl>
        <footer v-if="openable">
          <button :disabled="busy" @click="$emit('open', r)">
            {{ t("查看详情", "View details") }}
          </button>
        </footer>
      </article>
      <p v-if="!shown.length" class="empty">
        {{ t("暂无匹配记录", "No matching records") }}
      </p>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th v-for="c in columns" :key="c.key">
              <button class="sort-button" @click="sortBy(c.sort || c.key)">
                {{ c.label }}
                <span v-if="sort === (c.sort || c.key)">{{
                  descending ? "↓" : "↑"
                }}</span>
              </button>
            </th>
            <th v-if="openable"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in shown" :key="r.id">
            <td v-for="c in columns" :key="c.key">
              <span
                v-if="c.key === 'status'"
                class="badge"
                :class="r.status.toLowerCase()"
                >{{ stateName(r.status) }}</span
              ><template v-else>{{
                c.render ? c.render(r) : (r[c.key] ?? "—")
              }}</template>
            </td>
            <td v-if="openable">
              <button :disabled="busy" @click="$emit('open', r)">
                {{ t("查看", "Open") }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!shown.length" class="empty">
        {{ t("暂无匹配记录", "No matching records") }}
      </p>
    </div>
    <footer class="pagination">
      <span
        >{{ page }} / {{ Math.max(1, Math.ceil(filtered.length / 15)) }}</span
      ><button :disabled="page <= 1" @click="page--">
        {{ t("上一页", "Previous") }}</button
      ><button :disabled="page * 15 >= filtered.length" @click="page++">
        {{ t("下一页", "Next") }}
      </button>
    </footer>
  </section>
</template>
