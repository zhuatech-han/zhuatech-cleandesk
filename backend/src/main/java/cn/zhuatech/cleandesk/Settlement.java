// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 冻结人员结算单，员工确认后登记付款。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "worker_settlement")
public class Settlement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "worker_id", nullable = false)
  public Long workerId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "from_date", nullable = false, length = 10)
  public String fromDate;

  @Column(name = "to_date", nullable = false, length = 10)
  public String toDate;

  @Column(name = "amount", nullable = false, precision = 14, scale = 2)
  public BigDecimal amount;

  @Column(name = "status", nullable = false, length = 24)
  public String status;

  @Column(name = "payment_reference", length = 120)
  public String paymentReference;

  @Column(name = "note", nullable = false, length = 500)
  public String note;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "confirmed_at")
  public Instant confirmedAt;

  @Column(name = "paid_at")
  public Instant paidAt;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
