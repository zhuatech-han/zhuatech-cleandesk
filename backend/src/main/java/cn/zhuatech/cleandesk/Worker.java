// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 保洁人员、登录身份与计时成本基准。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "clean_worker")
public class Worker {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "account_id", nullable = false)
  public Long accountId;

  @Column(name = "name", nullable = false, length = 180)
  public String name;

  @Column(name = "hourly_rate", nullable = false, precision = 14, scale = 2)
  public BigDecimal hourlyRate;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
