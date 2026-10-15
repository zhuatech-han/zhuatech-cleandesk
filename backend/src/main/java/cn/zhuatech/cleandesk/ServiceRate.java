// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 服务价目：按次、面积或班组小时，历史订单用快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "service_rate")
public class ServiceRate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "name", nullable = false, length = 180)
  public String name;

  @Column(name = "name_en", nullable = false, length = 180)
  public String nameEn;

  @Column(name = "category_code", nullable = false, length = 20)
  public String categoryCode;

  @Column(name = "unit", nullable = false, length = 20)
  public String unit;

  @Column(name = "base_price", nullable = false, precision = 14, scale = 2)
  public BigDecimal basePrice;

  @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
  public BigDecimal unitPrice;

  @Column(name = "minutes_per_unit", nullable = false)
  public int minutesPerUnit;

  @Column(name = "workers_required", nullable = false)
  public int workersRequired;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
