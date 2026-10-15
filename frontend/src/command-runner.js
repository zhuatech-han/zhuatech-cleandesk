// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 幂等写入队列：中断后保留原内容与请求键，只由用户明确核对或原样重发。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function createCommandRunner(request, newId) {
  let uncertain = null;
  return {
    get pending() {
      return uncertain;
    },
    async send(action, body) {
      if (uncertain)
        throw Object.assign(new Error("RESULT_UNKNOWN"), { status: 0 });
      const payload = {
        ...JSON.parse(JSON.stringify(body)),
        requestKey: newId(),
      };
      try {
        return await request("/commands/" + action, {
          method: "POST",
          body: payload,
        });
      } catch (e) {
        if (e.status === 0) uncertain = { action, payload };
        throw e;
      }
    },
    async check() {
      if (!uncertain) return null;
      try {
        const result = await request(
          "/command-receipts/" + uncertain.payload.requestKey,
        );
        uncertain = null;
        return result;
      } catch (e) {
        if (e.status === 404) return null;
        throw e;
      }
    },
    async retry() {
      if (!uncertain) return null;
      const original = uncertain;
      try {
        const result = await request("/commands/" + original.action, {
          method: "POST",
          body: original.payload,
        });
        uncertain = null;
        return result;
      } catch (e) {
        if (e.status && e.status !== 0) uncertain = null;
        throw e;
      }
    },
    clear() {
      uncertain = null;
    },
  };
}
