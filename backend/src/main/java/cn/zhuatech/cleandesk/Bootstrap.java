// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化管理目录，不造客户、场所或业务订单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  public static final Set<String> CODES =
      Set.of(
          "bookings",
          "dispatch",
          "customers",
          "catalog",
          "workers",
          "finance",
          "reports",
          "audit",
          "users",
          "roles",
          "settings",
          "portal",
          "work");
  public static final Set<String> ADMIN_CODES =
      Set.of(
          "bookings",
          "dispatch",
          "customers",
          "catalog",
          "workers",
          "finance",
          "reports",
          "audit",
          "users",
          "roles",
          "settings");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  /** 注入首次管理员的私有配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${cleandesk.admin-username}") String username,
      @Value("${cleandesk.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 首次初始化；重启不重置身份和主数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    Rules.check(username.matches("[a-z0-9_.-]{3,60}"), "INVALID_INPUT");
    var d = new Department();
    d.name = "总部";
    d.zone = "Asia/Shanghai";
    db.save(d);
    String[][] menus = {
      {"bookings", "预约与报价", "Bookings"},
      {"dispatch", "派工日历", "Dispatch"},
      {"customers", "客户与场所", "Customers"},
      {"catalog", "服务与计价", "Services"},
      {"workers", "人员与时段", "Workers"},
      {"finance", "收付与结算", "Settlement"},
      {"reports", "经营报表", "Reports"},
      {"portal", "我的保洁", "My cleaning"},
      {"work", "我的任务", "My work"},
      {"audit", "操作审计", "Audit"},
      {"users", "登录账号", "Accounts"},
      {"roles", "角色权限", "Roles"},
      {"settings", "组织与设置", "Settings"}
    };
    for (int i = 0; i < menus.length; i++) {
      var p = new Permission();
      p.code = menus[i][0];
      p.name = menus[i][1];
      db.save(p);
      var m = new NavMenu();
      m.code = p.code;
      m.permissionCode = p.code;
      m.name = p.name;
      m.nameEn = menus[i][2];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    role("管理员", "ALL", ADMIN_CODES);
    role(
        "服务调度",
        "DEPARTMENT",
        Set.of("bookings", "dispatch", "customers", "catalog", "workers", "reports", "audit"));
    role("财务结算", "DEPARTMENT", Set.of("finance", "reports", "bookings"));
    role("保洁人员", "DEPARTMENT", Set.of("work"));
    role("客户", "DEPARTMENT", Set.of("portal"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = d.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    db.save(a);
    for (var e : Map.of("cancelHours", "24", "travelBuffer", "30", "currency", "CNY").entrySet()) {
      var x = new SystemSetting();
      x.code = e.getKey();
      x.value = e.getValue();
      db.save(x);
    }
    for (String[] c :
        new String[][] {
          {"HOME", "家庭清洁", "Home cleaning"},
          {"OFFICE", "办公室清洁", "Office cleaning"},
          {"DEEP", "深度清洁", "Deep cleaning"}
        }) {
      var x = new DictionaryEntry();
      x.type = "CATEGORY";
      x.code = c[0];
      x.name = c[1];
      x.nameEn = c[2];
      x.enabled = true;
      db.save(x);
    }
  }

  private void role(String n, String scope, Set<String> p) {
    var r = new AccessRole();
    r.name = n;
    r.scope = scope;
    r.permissions.addAll(p);
    db.save(r);
  }
}
