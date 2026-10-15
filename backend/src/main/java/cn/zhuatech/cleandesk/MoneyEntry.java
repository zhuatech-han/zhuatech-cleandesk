// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 已完成外部收退款凭证，只追加，不发起资金交易。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "money_entry")
public class MoneyEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "visit_id", nullable = false)
  public Long visitId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "kind", nullable = false, length = 12)
  public String kind;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  public BigDecimal amount;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "note", nullable = false, length = 500)
  public String note;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
