# CleanDesk 操作手册 / Operations
知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

## 从空库开始

登录初始管理员，在组织与设置中先核对组织时区和币种。已有人员或订单后不能随意更换时区；已有预约后锁定币种。默认角色：管理员、调度、财务、保洁人员、客户。管理员不拥有客户/员工身份，不能代替人员确认结算。

建立客户登录账号（客户角色）和员工登录账号（保洁人员角色）。客户与场所中绑定客户账号；人员与时段中绑定员工账号并维护每小时结算单价。客户可不绑定账号，由办公室记录线下沟通凭证，但不能伪称客户已在线确认。

维护每周工作时段后才能派工；请假按组织当地时间录入。修改或删除时段、增加请假如影响现有派工会拒绝，应先替班或改期。服务价目选择每次、每平方米或班组小时，设置单价、基础费、每单位班组分钟、所需人数和类别；增项为每次固定追加价格/分钟。停用保留历史。

## 预约到验收

客户从“我的保洁”维护场所并申请服务。办公室也可代录申请。单次或固定最多12次每周/隔周，申请只是希望时间，未锁人员。办公室发送每次优惠和报价说明；客户确认后待派工。办公室登记线下确认必须填写客户凭证。

在派工日历选择日期并打开上门，按所需人数选班组和负责人；系统检查同组织、启用账号、工作时段、请假、冲突与交通间隔。替班生成新轮次，旧指派取消但保留历史。

工作人员在手机浏览器登录，打开本人任务。当轮负责人上传作业前PNG/JPEG照片后才可开工；开工窗口是计划开始前30分钟至计划结束前。同一人员有其他进行中任务时不能开工。完工需作业后照片、每位人员1–1440分钟及完成说明，等待客户验收。工时是人工申报，不是自动定位计时。

客户验收通过，或说明需要返工的内容及希望时段；办公室线下验收/反馈须留客户凭证。返工重新派人，保留原轮次、照片、工时，不重复增加应收。已开始的作业没有中途终止流程；账号被停用时由管理员恢复账号处理未完任务。

## 改期、取消与异常

客户改期/取消须达到系统规定提前小时数（默认24）；不足时联系服务方处理。办公室例外操作须原因。取消/跳过只针对未开始且未发生实际完成作业的服务，应收归零；原收款保留为待退款，不自动退钱。返工待安排时不能把已实际作业按“未服务”取消。

状态变动或版本冲突提示后刷新，核对当前记录再操作。写入结果未知时，先点“核对保存结果”；仍无法确认可明确“原样重发同一请求”，不修改内容另发。此待核对状态仅保留在当前页面内存；关闭/刷新页面后应先查询列表、对象和外部凭证核对，不能假定原提交失败。

照片上传中断后刷新作业详情检查，未出现才重新选择上传；同一轮同阶段相同内容去重。不要上传家庭门锁密码、身份证或无关私密资料。

## 收付与人员结算

财务仅登记**已经在外部完成**的收款/退款，填写真实外部凭证，不能超额收款或对未取消服务登记取消退款。登记不是发起支付或银行到账认证。凭证为不可直接修改的流水，录入前复核金额；误登记纠错未提供自动冲销，正式使用前需建立人工审核安排。

验收后才可按人员与完成日期汇集结算，包含已完成返工轮次。结算单冻结后员工本人登录“我的结算”确认金额，财务再登记外部付款。撤销未付结算单需原因，释放所属明细可重新制单；已付不可撤销或重复入单。

客户从“月度对账”查询本人服务并导出CSV。办公室“经营报表”可按客户筛选。报表只包含已确认订单，按上门当地日期归月，统计当前净收款、待收和待退，不是期间现金账或税票。

## English workflow

Provision customer and worker accounts, link them to records, create sites/rates and weekly availability. Request → office quote → customer confirmation → crew allocation → lead uploads before photo and starts → after photo and declared minutes → customer acceptance or rework. Finance records completed external transactions only. Worker statements require personal confirmation before an external payment is recorded. Refresh stale records; check uncertain write outcomes before resending. Uncertain-command memory does not survive page reload. An interrupted photo upload should be checked in visit details. No automatic payment, invoice, mid-job termination, dispute resolution or ledger reversal is included.
