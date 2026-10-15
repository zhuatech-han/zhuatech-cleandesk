# CleanDesk API
知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

同源 `/api`，会话cookie。先GET `/auth/csrf`取得`header/token`，所有POST都带该请求头。POST `/auth/login`提交`username/password`；成功后重新获取CSRF。GET `/auth/me`当前身份，POST `/auth/logout`退出，POST `/auth/password`提交旧/新密码并撤销会话。不要在URL、日志或文档写真实密码。

## 查询

| GET | 内容 |
| --- | --- |
| `/workspace` | 当前账号三端可见目录、上门摘要、菜单与时区/币种 |
| `/bookings/{id}` | 客户归属订单、报价快照及周期上门 |
| `/visits/{id}` | 授权上门、可见作业轮次、照片元数据和事件 |
| `/settlements/{id}` | 财务或人员本人结算单及冻结明细 |
| `/photos/{id}` | 授权PNG二进制，no-store，不公开URL |
| `/reports?month=2026-10&customerId=1` | 已确认服务的月度当前对账；客户只能看本人 |
| `/reports.csv` | 同范围CSV，转义公式及文本 |
| `/audit` | 组织授权审计，不含请求正文 |
| `/admin/{kind}` | users/roles/permissions/departments/menus/dictionaries/settings |
| `/admin-options` | 系统管理选项，按管理权限返回 |
| `/binding-options` | 客户/员工绑定所需最小账号选项，不返回口令哈希 |
| `/command-receipts/{requestKey}` | 仅当前账号自己的已提交结果标识 |

## 业务命令

POST `/commands/{action}`，JSON包括每次操作新生成的UUID `requestKey`。同键同内容返回原结果`{id,version?}`，不同内容409。修改已有记录需`id/version`；无版本字段的追加记录不伪造版本。写入结果未知时先查receipt，明确操作后可携原内容原键重发，不能自动改键重试。

| action | 主要字段 |
| --- | --- |
| `customer.save` | name/contact/billingType(PER_VISIT/MONTHLY)/accountId?/departmentId?/enabled |
| `site.save` | customerId/name/address/contact/instructions/enabled |
| `service.save` | departmentId?/name/nameEn/categoryCode/unit(FIXED/AREA/HOURLY)/basePrice/unitPrice/minutesPerUnit/workersRequired/enabled |
| `extra.save` | departmentId?/name/nameEn/price/minutes/enabled |
| `worker.save` | departmentId?/name/accountId/hourlyRate/enabled |
| `availability.save` | workerId/weekday(1–7)/startTime/endTime(HH:mm) |
| `availability.remove` | id/version；影响现有派工则拒绝 |
| `timeoff.save` | workerId/localStart/localEnd/reason/enabled；也接受UTC startsAt/endsAt |
| `booking.request` | customerId(办公室)/siteId/serviceId/units/extraIds/localStart/occurrences/intervalWeeks(0/1/2)/note |
| `booking.quote` | id/version/discount(每次)/note |
| `booking.confirm` | id/version；办公室另需confirmationRef |
| `visit.assign` | id/version/workerIds/leaderId/note |
| `visit.reschedule` | id/version/localStart/reason |
| `visit.cancel` | id/version/reason/skip(boolean) |
| `visit.start` | id/version |
| `visit.complete` | id/version/minutes({workerId:整数分钟})/note |
| `visit.accept` | id/version/note；办公室另需confirmationRef |
| `visit.rework` | id/version/localStart/reason；办公室另需confirmationRef |
| `money.record` | visitId/kind(RECEIPT/REFUND)/amount/reference/note |
| `settlement.create` | workerId/fromDate/toDate(最多92天)/note |
| `settlement.confirm` | id/version，人员本人 |
| `settlement.pay` | id/version/reference |
| `settlement.cancel` | id/version/reason |
| `admin.save` | kind/id?/version?及对应目录字段；账号password仅新建/重置时传 |

时间输入为所属组织当地`yyyy-MM-ddTHH:mm`，不是浏览器时区。金额建议十进制字符串，最多两位小数。服务次数1–12；单次intervalWeeks=0、occurrences=1；FIXED数量1。客户归属与人员权限由后端再次验证。

POST `/rounds/{id}/photos?phase=BEFORE|AFTER&caption=...`，multipart `file`，同样要求CSRF。PNG/JPEG实际解码后重编码，拒绝SVG/任意URL。只允许当轮实际指派人员在可上传状态操作；照片摘要去重。

错误响应`{code}`：400参数、401会话、403权限、404记录/本人receipt不存在、409状态/版本/冲突、413资源上限、429登录限流。具体可操作提示在界面和操作手册；错误不返回SQL、文件路径或敏感载荷。

English: all writes require CSRF and actor-scoped idempotency keys. Access is enforced on the server for every query, command and image download. Monetary transactions are records of external activity, not payment APIs. No notification, map, public-registration or payment-provider integration is implemented.
