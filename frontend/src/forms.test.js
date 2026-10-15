// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
globalThis.localStorage = { getItem: () => null };
const { definition, submission, localInput } = await import("./forms.js");
const data = {
  timezone: "Asia/Shanghai",
  customers: [
    { id: 1, departmentId: 1, enabled: true },
    { id: 2, departmentId: 2, enabled: true },
  ],
  sites: [
    { id: 1, customerId: 1, enabled: true },
    { id: 2, customerId: 2, enabled: true },
  ],
  services: [
    { id: 1, departmentId: 1, enabled: true, unit: "FIXED" },
    { id: 2, departmentId: 2, enabled: true, unit: "HOURLY" },
  ],
  extras: [
    { id: 1, departmentId: 1, enabled: true },
    { id: 2, departmentId: 2, enabled: true },
  ],
  organizations: [
    { id: 1, zone: "Asia/Shanghai" },
    { id: 2, zone: "America/New_York" },
  ],
};
test("single fixed-price visit cannot carry stale hidden inputs", () =>
  assert.deepEqual(
    submission(
      "booking.request",
      { intervalWeeks: 0, occurrences: 12, serviceId: 1, units: "99" },
      data,
    ),
    { intervalWeeks: 0, occurrences: 1, serviceId: 1, units: "1.00" },
  ));
test("booking choices follow selected customer organization", () => {
  const f = definition("booking.request", data, [], {}, { permissions: [] });
  for (const key of ["siteId", "serviceId", "extraIds"])
    assert.deepEqual(
      f.fields
        .find((x) => x.key === key)
        .getOptions({ customerId: 1 })
        .map((o) => o.value),
      [1],
    );
  assert.ok(
    f.fields
      .find((x) => x.key === "localStart")
      .getHint({ customerId: 2 })
      .includes("America/New_York"),
  );
});
test("worker minutes are sent with explicit worker identities", () =>
  assert.deepEqual(
    submission(
      "visit.complete",
      { id: 3, minutes_12: 60, minutes_13: 45, note: "QA" },
      data,
    ),
    { id: 3, minutes: { 12: 60, 13: 45 }, note: "QA" },
  ));
test("date inputs use organization time", () => {
  assert.equal(
    localInput("2026-10-15T02:00:00Z", "Asia/Shanghai"),
    "2026-10-15T10:00",
  );
  assert.equal(
    localInput("2026-10-15T02:00:00Z", "America/New_York"),
    "2026-10-14T22:00",
  );
});
