// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 同源业务、命令恢复、管理目录及受保护图片入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final CleaningService core;
  final QueryService queries;
  final AdminService admin;
  final PhotoService photos;
  final Store db;
  final AccessService access;

  /** 接线业务事务和安全读模型。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ApiController(
      CleaningService core,
      QueryService queries,
      AdminService admin,
      PhotoService photos,
      Store db,
      AccessService access) {
    this.core = core;
    this.queries = queries;
    this.admin = admin;
    this.photos = photos;
    this.db = db;
    this.access = access;
  }

  /** 按当前账号归属生成三端工作集合。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workspace")
  public Object workspace() {
    return queries.workspace();
  }

  /** 所有业务写操作带幂等键，不自动重复扣款或派工。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/commands/{action}")
  public Object command(@PathVariable String action, @RequestBody Map<String, Object> b) {
    return core.command(action, b);
  }

  /** 连接中断后先查提交结果，不把未知结果当失败。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/command-receipts/{key}")
  public Object receipt(@PathVariable String key) {
    return core.receipt(key);
  }

  /** 订单报价和周期上门。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/bookings/{id}")
  public Object booking(@PathVariable Long id) {
    return queries.booking(id);
  }

  /** 授权作业记录、历史和证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/visits/{id}")
  public Object visit(@PathVariable Long id) {
    return queries.visit(id);
  }

  /** 原图验证后重编码，在事务中存入数据库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rounds/{id}/photos")
  public Object upload(
      @PathVariable Long id,
      @RequestParam String phase,
      @RequestParam(defaultValue = "") String caption,
      @RequestPart MultipartFile file)
      throws java.io.IOException {
    return photos.upload(id, phase, caption, file);
  }

  /** 不含公开地址或缓存授权旁路的私有照片。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/photos/{id}")
  public ResponseEntity<byte[]> photo(@PathVariable Long id) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "inline; filename=work-photo.png")
        .contentType(MediaType.IMAGE_PNG)
        .body(photos.read(id));
  }

  /** 员工本人或财务可看冻结结算明细。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/settlements/{id}")
  public Object settlement(@PathVariable Long id) {
    return queries.settlement(id);
  }

  /** 客户本人的月账单或授权经营范围月报。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object report(
      @RequestParam String month, @RequestParam(required = false) Long customerId) {
    return queries.report(month, customerId);
  }

  /** CSV不加入推广信息，转义公式与客户文本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports.csv")
  public ResponseEntity<String> csv(
      @RequestParam String month, @RequestParam(required = false) Long customerId) {
    var report = (Map<?, ?>) queries.report(month, customerId);
    var lines = (List<Map<String, Object>>) report.get("rows");
    StringBuilder out =
        new StringBuilder(
            "\ufeffVisit,Customer,Service,Start,Status,Charge,Registered paid,Due,Refund due\r\n");
    for (var x : lines) {
      out.append(
              List.of(
                      "id",
                      "customerName",
                      "serviceName",
                      "startsAt",
                      "status",
                      "charge",
                      "paid",
                      "due",
                      "refundDue")
                  .stream()
                  .map(k -> Rules.csv(x.get(k)))
                  .collect(java.util.stream.Collectors.joining(",")))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=cleaning-statement.csv")
        .body(out.toString());
  }

  /** 范围内操作日志。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return queries.audit();
  }

  /** 管理目录的实时权限检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object directory(@PathVariable String kind) {
    return admin.read(kind);
  }

  /** 账号绑定选项仅供账号管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin-options")
  public Object options() {
    return admin.options();
  }

  /** 业务管理员只读取可绑定的姓名、角色代码及组织，不读密码或权限管理接口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/binding-options")
  @Transactional(readOnly = true)
  public Object bindings() {
    Rules.check(access.has("customers") || access.has("workers"), "FORBIDDEN");
    return db.all(Account.class).stream()
        .filter(a -> access.department(a.departmentId))
        .map(
            a ->
                CleaningService.row(
                    "id",
                    a.id,
                    "username",
                    a.username,
                    "displayName",
                    a.displayName,
                    "departmentId",
                    a.departmentId,
                    "enabled",
                    a.enabled,
                    "portal",
                    db.get(AccessRole.class, a.roleId).permissions.equals(Set.of("portal")),
                    "work",
                    db.get(AccessRole.class, a.roleId).permissions.contains("work")))
        .toList();
  }
}
