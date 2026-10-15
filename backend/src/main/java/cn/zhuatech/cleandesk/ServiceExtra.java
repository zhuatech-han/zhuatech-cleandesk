// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 可选增项的固定收费与追加时长。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "service_extra")
public class ServiceExtra {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "name", nullable = false, length = 180)
  public String name;

  @Column(name = "name_en", nullable = false, length = 180)
  public String nameEn;

  @Column(name = "price", nullable = false, precision = 14, scale = 2)
  public BigDecimal price;

  @Column(name = "minutes", nullable = false)
  public int minutes;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
