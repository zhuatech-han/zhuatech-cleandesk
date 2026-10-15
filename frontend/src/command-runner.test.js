// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { createCommandRunner } from "./command-runner.js";
const error = (status) => Object.assign(new Error("QA"), { status });
test("uncertain write never silently retries and blocks new writes", async () => {
  let n = 0;
  const r = createCommandRunner(
    async () => {
      n++;
      throw error(0);
    },
    () => "qa-key",
  );
  await assert.rejects(r.send("booking.request", { units: "1" }));
  await assert.rejects(r.send("money.record", {}));
  assert.equal(n, 1);
  assert.equal(r.pending.action, "booking.request");
});
test("explicit retry keeps original payload and key", async () => {
  const calls = [];
  const r = createCommandRunner(
    async (p, o) => {
      calls.push([p, structuredClone(o)]);
      if (calls.length === 1) throw error(0);
      return { id: 4 };
    },
    () => "qa-key",
  );
  const b = { crew: [1] };
  await assert.rejects(r.send("visit.assign", b));
  b.crew.push(2);
  assert.deepEqual(await r.retry(), { id: 4 });
  assert.deepEqual(calls[0], calls[1]);
  assert.equal(r.pending, null);
});
test("receipt 404 keeps pending command without resending", async () => {
  let n = 0;
  const r = createCommandRunner(
    async () => {
      n++;
      throw error(n === 1 ? 0 : 404);
    },
    () => "qa-key",
  );
  await assert.rejects(r.send("booking.request", {}));
  assert.equal(await r.check(), null);
  assert.ok(r.pending);
  assert.equal(n, 2);
});
test("known rejection permits a corrected new request", async () => {
  const r = createCommandRunner(
    async () => {
      throw error(409);
    },
    () => "qa-key",
  );
  await assert.rejects(r.send("visit.assign", {}));
  assert.equal(r.pending, null);
});

import { reactive } from "vue";
test("reactive form arrays serialize before a write reaches HTTP", async () => {
  let saved;
  const r = createCommandRunner(
    async (p, o) => {
      saved = o.body;
      return { id: 9 };
    },
    () => "qa-key",
  );
  await r.send(
    "booking.request",
    reactive({ extraIds: [], crew: [1], units: "1.00" }),
  );
  assert.deepEqual(saved, {
    extraIds: [],
    crew: [1],
    units: "1.00",
    requestKey: "qa-key",
  });
});
