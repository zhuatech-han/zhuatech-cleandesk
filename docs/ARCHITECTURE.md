# CleanDesk 架构与数据 / Architecture
知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

Nginx提供Vue页面并反代同源API。Spring Boot使用会话、CSRF、BCrypt与数据库实时身份检查；权限不依赖隐藏按钮。JPA与Flyway负责结构、事务和约束；MySQL同时保存受保护照片内容，无外部对象存储依赖。

## 数据关系

- `account` → `access_role`、`department`；角色权限单独集合。客户账号仅有`portal`，人员账号具备`work`。
- `clean_customer` → `clean_site`；客户账号可选，但一旦绑定不允许换绑。人员账号固定绑定`clean_worker`。
- `service_rate`、`service_extra` → 预约时复制到`clean_booking`快照。调价、地址修改不追改历史。
- `clean_booking` → `clean_visit`，每个周期次数独立且唯一。
- `clean_visit` → `work_round` → `work_assignment` / `work_photo` → `photo_data`。
- `money_entry`为追加式外部收退款登记；`worker_settlement`冻结人员成本，指派明细只能属于一个有效结算单。
- `visit_event`记录动作及原因，`audit_event`记录账号和操作；`command_receipt`只存账号、键、摘要和结果标识，不存完整请求或明文密码。

## 状态与一致性

订单：`SUBMITTED → QUOTED → CONFIRMED`。改报价仅在确认前；确认后服务价固定。

上门：`REQUESTED → READY → ASSIGNED → IN_PROGRESS → REVIEW → ACCEPTED`。未开工可取消/跳过；改期清除旧指派并回到READY。待验收可提出返工：旧轮次REJECTED，上门REWORK，再建立新轮次。返工保留已发生人工成本，客户应收不重复计算。

人员结算：`DRAFT → CONFIRMED → PAID`；只允许本人确认。未付款可撤销并释放明细，已付款不可重用。

所有业务命令先锁定稳定组织锁行，再读取和核对版本及业务约束。同键同内容返回第一次结果，同键不同内容拒绝；客户端不自动重试写入。该设计优先保证小规模部署一致性，串行写锁不是高吞吐架构。

图片按实际解码格式、维度与容量校验，再重新编码PNG去除原始元数据；BLOB与元数据原子写入。下载时重新核对订单或实际派工权限，并使用`no-store`。员工只能看自己参与的轮次及本人结算，不获得其他人员薪酬或客户资金流水。

## 时间与金额

UTC持久化；周历在组织IANA时区生成，拒绝夏令时缺失/重复时刻。交通间隔、请假、每周时段与正在执行任务共同检查。日历跨当地日期的班次不支持。

金额用BigDecimal/DECIMAL，输入不静默截断；计价结果统一到分。班组时长不同于全员人时，人员成本按每人申报分钟和派工时冻结的时薪计算。月账单按上门日期归月，过滤未确认订单，收付为当前累计值。

## English notes

A single-company, same-origin Vue/Nginx/Spring Boot/MySQL application. Quote snapshots and occurrence identities are stable. Business commands serialize under a database lock and use actor-scoped idempotency receipts. Photos are authorized database BLOBs. Workers see only their own assigned rounds and settlement; customers see only their linked customer data. Recurrence uses organization-local calendar time, while persistence uses UTC. Money uses decimal arithmetic. Large-scale, multi-instance and high-throughput operation has not been validated.
