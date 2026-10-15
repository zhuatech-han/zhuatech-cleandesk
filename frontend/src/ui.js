// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref(localStorage.getItem("cleandesk-language") || "zh");
/** 展示语言不改变价格、时段或后台授权。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const t = (zh, en) => (language.value === "zh" ? zh : en);
export const localized = (r) =>
  language.value === "en" ? r.nameEn || r.name : r.name;
export const cloneRecord = (r) => JSON.parse(JSON.stringify(r));
export const dateTime = (v, zone) =>
  v
    ? new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
        dateStyle: "medium",
        timeStyle: "short",
        ...(zone ? { timeZone: zone } : {}),
      }).format(new Date(v))
    : "—";
export const confirmation = ref(null);
/** 使用应用内确认，避免系统原生弹窗。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => {
    confirmation.value = { message, resolve };
  });
}
const errors = {
  SERVICE_ALREADY_PERFORMED: [
    "此服务已有实际作业，请先完成返工与验收，不能按未服务取消。",
    "Work has already been performed. Finish rework and acceptance instead of cancelling as unperformed.",
  ],
  UNAUTHENTICATED: [
    "登录已失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  FORBIDDEN: ["没有此操作权限。", "Permission denied."],
  OUT_OF_SCOPE: ["无权访问这条记录。", "Record outside your authorized scope."],
  LOGIN_FAILED: ["账号或密码不正确。", "Invalid username or password."],
  LOGIN_THROTTLED: [
    "登录尝试过多，请稍后再试。",
    "Too many sign-in attempts. Try later.",
  ],
  PASSWORD_WEAK: [
    "密码须12–72字节，含大小写字母和数字。",
    "Use 12–72 bytes, uppercase, lowercase and a digit.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Original password is incorrect."],
  VERSION_CONFLICT: [
    "记录已被更新，请刷新后再操作。",
    "Record changed. Refresh before editing.",
  ],
  INVALID_INPUT: [
    "请核对必填项、数值和格式。",
    "Check required fields, values and formats.",
  ],
  CONFLICT: ["记录重复或仍被引用。", "Duplicate or referenced record."],
  STATE_INVALID: [
    "当前状态不能进行此操作，请刷新核对。",
    "Action unavailable in the current state. Refresh the record.",
  ],
  LAST_ADMIN: ["须保留一位完整管理员。", "Keep one full administrator."],
  SYSTEM_MENU_REQUIRED: [
    "账号、角色和设置菜单须保持启用。",
    "Keep account, role and settings menus enabled.",
  ],
  IDENTITY_LOCKED: [
    "已有记录的归属与稳定身份不能更改。",
    "Ownership and stable identity cannot change.",
  ],
  BOUND_ACCOUNT_ROLE: [
    "此账号已绑定客户或人员，须保留相应角色与组织。",
    "A linked account must keep its required role and organization.",
  ],
  CUSTOMER_ROLE_EXCLUSIVE: [
    "客户角色不能同时包含内部管理权限。",
    "Customer roles cannot include internal permissions.",
  ],
  CUSTOMER_NOT_LINKED: [
    "账号尚未绑定客户，请联系服务公司管理员。",
    "This account is not linked to a customer. Contact your service administrator.",
  ],
  WORKER_NOT_LINKED: [
    "账号尚未绑定启用的保洁人员。",
    "This account is not linked to an active worker.",
  ],
  CUSTOMER_ACCOUNT_INVALID: [
    "请选择同组织启用的客户角色账号。",
    "Choose an active customer account in this organization.",
  ],
  WORKER_ACCOUNT_INVALID: [
    "请选择同组织启用的员工任务账号。",
    "Choose an active worker account in this organization.",
  ],
  CUSTOMER_DISABLED: ["客户已停用。", "Customer disabled."],
  WORKER_DISABLED: ["人员或其账号已停用。", "Worker or account disabled."],
  DEPARTMENT_DISABLED: ["组织已停用。", "Organization disabled."],
  SITE_INVALID: [
    "请选择该客户已启用的服务场所。",
    "Choose an active site owned by this customer.",
  ],
  SERVICE_INVALID: [
    "服务或增项已停用，或不属于该组织。",
    "Service or extra disabled or outside this organization.",
  ],
  CATEGORY_INVALID: [
    "请选择已启用的服务类别。",
    "Choose an active service category.",
  ],
  AMOUNT_INVALID: [
    "金额须为有效的非负两位小数；收费数量须大于零。",
    "Use valid amounts with at most two decimals; quantities must be positive.",
  ],
  UNITS_INVALID: [
    "按次服务数量必须为1，其他数量不能超过10000。",
    "Fixed services require quantity 1; other quantities cannot exceed 10,000.",
  ],
  DURATION_INVALID: [
    "总服务时长须在30分钟至12小时之间。",
    "Total visit duration must be 30 minutes to 12 hours.",
  ],
  RECURRENCE_INVALID: [
    "选择单次、每周或隔周，最多12次。",
    "Choose one-off, weekly or fortnightly, at most 12 visits.",
  ],
  BOOKING_TIME_INVALID: [
    "服务须在将来开始，并在180天内。",
    "Visits must start in the future and within 180 days.",
  ],
  AMBIGUOUS_LOCAL_TIME: [
    "此当地时刻处于夏令时跳变或重复区间，请另选时间。",
    "This local time is missing or repeated at a daylight-saving transition. Choose another time.",
  ],
  NOTICE_TOO_SHORT: [
    "距上门时间不足规定的提前小时，请联系调度处理。",
    "Too close to the visit for a customer change. Contact dispatch.",
  ],
  TIME_INVALID: [
    "开始须早于结束，请核对时间范围。",
    "Start must precede end. Check the date range.",
  ],
  TIME_CONFLICT: [
    "人员已有重叠任务或交通间隔不足。",
    "Worker has an overlapping job or insufficient travel buffer.",
  ],
  TIME_OFF: [
    "人员在该时段请假或不可安排。",
    "Worker is unavailable in this time-off period.",
  ],
  OUTSIDE_AVAILABILITY: [
    "任务超出人员的可工作时段。",
    "Visit is outside worker availability.",
  ],
  ASSIGNMENT_EXISTS: [
    "此修改会影响已有派工，请先改派相关任务。",
    "Existing assignments would be affected. Reassign them first.",
  ],
  CREW_SIZE_INVALID: [
    "请选择规定人数，不能重复选择同一人员。",
    "Select the required number of distinct workers.",
  ],
  LEADER_INVALID: [
    "负责人必须在本次指派人员中。",
    "The lead must be in this crew.",
  ],
  LEADER_ONLY: [
    "只有本轮负责人可以开始或提交完工。",
    "Only this round’s lead can start or complete work.",
  ],
  WORKER_BUSY: [
    "人员还有未完成的现场任务。",
    "Worker has unfinished work in progress.",
  ],
  START_WINDOW_INVALID: [
    "只能在预约开始前30分钟至结束前开始作业。",
    "Start work from 30 minutes before the scheduled start until the scheduled end.",
  ],
  BEFORE_PHOTO_REQUIRED: [
    "请先上传作业前照片。",
    "Upload a before-work photo first.",
  ],
  AFTER_PHOTO_REQUIRED: [
    "请先上传作业后照片。",
    "Upload an after-work photo first.",
  ],
  HOURS_REQUIRED: [
    "请填写每位指派人员的实际分钟数。",
    "Enter actual minutes for every assigned worker.",
  ],
  PHOTO_PHASE_INVALID: [
    "当前轮次不接受此类照片。",
    "This photo phase is not allowed now.",
  ],
  IMAGE_INVALID: [
    "只接受可解析的PNG或JPEG照片。",
    "Upload a valid PNG or JPEG image.",
  ],
  IMAGE_DIMENSIONS: [
    "图片最大4096×4096且不超过1600万像素。",
    "Images must be at most 4096×4096 and 16 megapixels.",
  ],
  FILE_TOO_LARGE: ["照片最大8MB。", "Photos must be no larger than 8MB."],
  PHOTO_LIMIT: ["每轮最多12张照片。", "At most 12 photos per work round."],
  PHOTO_QUOTA: [
    "该组织照片已达256MB上限。",
    "Organization photos exceed the 256MB quota.",
  ],
  PAYMENT_EXCEEDS_DUE: [
    "收款不能超过该次上门尚欠金额。",
    "Receipt exceeds the balance due for this visit.",
  ],
  REFUND_EXCEEDS_PAID: [
    "仅取消或跳过的服务可退，且不能超过净收款。",
    "Refunds require a cancelled/skipped visit and cannot exceed net receipts.",
  ],
  NOTHING_TO_SETTLE: [
    "没有已验收且尚未入单的工时。",
    "No accepted, unsettled work is available.",
  ],
  WORKER_CONFIRMATION_REQUIRED: [
    "请先由人员本人确认结算单。",
    "The worker must confirm this statement first.",
  ],
  DUPLICATE_REFERENCE: [
    "该付款凭证已登记。",
    "Payment reference already recorded.",
  ],
  IDEMPOTENCY_CONFLICT: [
    "这次提交的内容与原请求不同，请先核对原结果。",
    "This submission differs from the original request. Verify its result first.",
  ],
  CURRENCY_LOCKED: [
    "已有订单后不能直接更改部署币种。",
    "Currency is locked after the first booking.",
  ],
  ZONE_LOCKED: [
    "已有人员或订单，不能直接更改组织时区。",
    "Organization time zone is locked after workers or bookings exist.",
  ],
  NETWORK_ERROR: [
    "暂时无法连接服务器，请刷新重试。",
    "Server unavailable. Refresh and try again.",
  ],
  RESULT_UNKNOWN: [
    "连接中断，尚不能确定是否已保存，请先核对结果。",
    "Connection lost. We cannot yet confirm whether it was saved. Check the result first.",
  ],
  SERVICE_ALREADY_PERFORMED: [
    "已有实际作业记录，请通过返工验收流程继续处理。",
    "Work has already been performed. Continue through rework and acceptance.",
  ],
};
/** 业务错误使用可操作文案，未知错误不向操作人员展示浏览器内部异常。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const errorMessage = (e) =>
  errors[e.message]
    ? t(...errors[e.message])
    : t(
        "操作未完成，请刷新页面后重试。",
        "Action failed. Refresh the page and try again.",
      );
const states = {
  SUBMITTED: ["待报价", "Awaiting quote"],
  QUOTED: ["待确认报价", "Quote ready"],
  CONFIRMED: ["已确认", "Confirmed"],
  REQUESTED: ["待报价确认", "Requested"],
  READY: ["待派工", "Unassigned"],
  ASSIGNED: ["已派工", "Assigned"],
  IN_PROGRESS: ["作业中", "In progress"],
  REVIEW: ["待客户验收", "Awaiting acceptance"],
  ACCEPTED: ["已验收", "Accepted"],
  REWORK: ["待安排返工", "Rework needed"],
  REJECTED: ["需返工", "Rework requested"],
  CANCELLED: ["已取消", "Cancelled"],
  SKIPPED: ["已跳过", "Skipped"],
  DRAFT: ["待本人确认", "Awaiting worker"],
  PAID: ["已登记付款", "Payment recorded"],
  ABORTED: ["已中止", "Aborted"],
};
export const stateName = (s) => (states[s] ? t(...states[s]) : s);
/** 安全随机幂等键，适用于HTTPS与本机环境。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function requestId() {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 15) | 64;
  b[8] = (b[8] & 63) | 128;
  const s = [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
  return [
    s.slice(0, 8),
    s.slice(8, 12),
    s.slice(12, 16),
    s.slice(16, 20),
    s.slice(20),
  ].join("-");
}

/** 将业务历史显示为岗位可理解的动作名称。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function eventName(code) {
  const names = {
    REQUESTED: ["申请服务", "Service requested"],
    QUOTED: ["已报价", "Quote issued"],
    CUSTOMER_CONFIRMED: ["客户确认报价", "Customer confirmed quote"],
    OFFLINE_CONFIRMATION_RECORDED: [
      "登记线下确认",
      "Offline confirmation recorded",
    ],
    RESCHEDULED: ["改期并释放旧派工", "Rescheduled; previous crew released"],
    ASSIGNED: ["安排人员", "Crew assigned"],
    STARTED: ["开始作业", "Work started"],
    COMPLETED: ["提交完工", "Work completed"],
    ACCEPTED: ["客户验收通过", "Customer accepted"],
    OFFLINE_ACCEPTANCE_RECORDED: [
      "登记线下验收",
      "Offline acceptance recorded",
    ],
    REWORK_REQUESTED: ["要求返工", "Rework requested"],
    RECEIPT: ["登记收款", "Receipt recorded"],
    REFUND: ["登记退款", "Refund recorded"],
    CANCELLED: ["取消此次上门", "Visit cancelled"],
    SKIPPED: ["跳过此次上门", "Visit skipped"],
  };
  return names[code] ? t(...names[code]) : stateName(code);
}
