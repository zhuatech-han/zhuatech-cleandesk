// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;

/** 客户场所及联系资料；访问继承客户范围。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "clean_site")
public class Site {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "name", nullable = false, length = 180)
  public String name;

  @Column(name = "address", nullable = false, length = 500)
  public String address;

  @Column(name = "contact", nullable = false, length = 180)
  public String contact;

  @Column(name = "instructions", nullable = false, length = 1000)
  public String instructions;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
