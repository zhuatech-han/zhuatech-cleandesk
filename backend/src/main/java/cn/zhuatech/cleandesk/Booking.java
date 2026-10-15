// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 一次或有限周期订单，保留报价快照及客户确认。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "clean_booking")
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "site_id", nullable = false)
  public Long siteId;

  @Column(name = "service_id", nullable = false)
  public Long serviceId;

  @Column(name = "service_name", nullable = false, length = 180)
  public String serviceName;

  @Column(name = "unit", nullable = false, length = 20)
  public String unit;

  @Column(name = "units", nullable = false, precision = 14, scale = 2)
  public BigDecimal units;

  @Column(name = "extras_snapshot", nullable = false, length = 4000)
  public String extrasSnapshot;

  @Column(name = "site_snapshot", nullable = false, length = 2000)
  public String siteSnapshot;

  @Column(name = "status", nullable = false, length = 24)
  public String status;

  @Column(name = "occurrences", nullable = false)
  public int occurrences;

  @Column(name = "interval_weeks", nullable = false)
  public int intervalWeeks;

  @Column(name = "duration_minutes", nullable = false)
  public int durationMinutes;

  @Column(name = "workers_required", nullable = false)
  public int workersRequired;

  @Column(name = "unit_visit_price", nullable = false, precision = 14, scale = 2)
  public BigDecimal unitVisitPrice;

  @Column(name = "discount", nullable = false, precision = 14, scale = 2)
  public BigDecimal discount;

  @Column(name = "quote_note", nullable = false, length = 1000)
  public String quoteNote;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "confirmed_at")
  public Instant confirmedAt;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
