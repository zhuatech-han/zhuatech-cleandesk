<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { reactive, watch } from "vue";
import { t } from "../ui.js";
const props = defineProps({
  title: { type: String, required: true },
  fields: { type: Array, required: true },
  initial: { type: Object, required: true },
  busy: Boolean,
  removable: Boolean,
  message: { type: String, default: "" },
});
const emit = defineEmits(["submit", "close", "remove"]);
const form = reactive(JSON.parse(JSON.stringify(props.initial || {})));
watch(
  () => [form.customerId, form.departmentId],
  () => {
    for (const f of props.fields) {
      if (!f.getOptions || f.readonly) continue;
      const allowed = f.getOptions(form).map((o) => String(o.value));
      if (Array.isArray(form[f.key]))
        form[f.key] = form[f.key].filter((v) => allowed.includes(String(v)));
      else if (
        form[f.key] !== undefined &&
        !allowed.includes(String(form[f.key]))
      )
        form[f.key] = "";
    }
  },
);
</script>
<template>
  <div class="modal-backdrop">
    <section class="modal" role="dialog" aria-modal="true" :aria-label="title">
      <header>
        <h2>{{ title }}</h2>
        <button :disabled="busy" @click="emit('close')">
          {{ t("关闭", "Close") }}
        </button>
      </header>
      <p v-if="message" role="alert" class="form-error">{{ message }}</p>
      <form @submit.prevent="emit('submit', form)">
        <template v-for="f in fields" :key="f.key"
          ><template v-if="!f.show || f.show(form)"
            ><fieldset v-if="f.type === 'multi'" class="choice-list">
              <legend>{{ f.label }}</legend>
              <label
                v-for="o in f.getOptions ? f.getOptions(form) : f.options"
                :key="o.value"
                class="check"
                ><input
                  v-model="form[f.key]"
                  type="checkbox"
                  :value="o.value"
                  :disabled="busy"
                />{{ o.label }}</label
              >
              <p
                v-if="!(f.getOptions ? f.getOptions(form) : f.options).length"
                class="muted"
              >
                {{
                  t(
                    "没有可选记录，请先维护基础资料。",
                    "No options available. Set up the required records first.",
                  )
                }}
              </p>
            </fieldset>
            <label v-else-if="f.type === 'checkbox'" class="check"
              ><input
                v-model="form[f.key]"
                type="checkbox"
                :disabled="busy"
              />{{ f.label }}</label
            ><label v-else
              >{{ f.label
              }}<span v-if="f.required !== false" class="required"> *</span
              ><select
                v-if="f.type === 'select'"
                v-model="form[f.key]"
                :required="f.required !== false"
                :disabled="busy || f.readonly"
              >
                <option v-if="f.required !== false" disabled value="">
                  {{ t("请选择", "Choose") }}
                </option>
                <option
                  v-for="o in f.getOptions ? f.getOptions(form) : f.options"
                  :key="o.value"
                  :value="o.value"
                >
                  {{ o.label }}
                </option></select
              ><textarea
                v-else-if="f.type === 'textarea'"
                v-model="form[f.key]"
                :required="f.required !== false"
                :maxlength="f.max || 1000"
                :disabled="busy"
                rows="3"
              ></textarea
              ><input
                v-else
                v-model="form[f.key]"
                :type="f.type || 'text'"
                :required="f.required !== false"
                :min="f.min"
                :max="f.maxValue"
                :step="f.step"
                :maxlength="f.max || 180"
                :disabled="busy"
                :readonly="f.readonly"
                autocomplete="off"
              /><small v-if="f.hint || f.getHint" class="muted">{{
                f.getHint ? f.getHint(form) : f.hint
              }}</small></label
            ></template
          ></template
        >
        <footer class="actions">
          <button
            v-if="removable"
            type="button"
            class="danger"
            :disabled="busy"
            @click="emit('remove')"
          >
            {{ t("删除此工作时段", "Remove this availability") }}
          </button>
          <button type="button" :disabled="busy" @click="emit('close')">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("保存中…", "Saving…") : t("保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
