// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 周期中的一次上门，取消、改期和返工保留原身份。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "clean_visit")
public class Visit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "booking_id", nullable = false)
  public Long bookingId;

  @Column(name = "occurrence_no", nullable = false)
  public int occurrenceNo;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "status", nullable = false, length = 24)
  public String status;

  @Column(name = "round_no", nullable = false)
  public int roundNo = 0;

  @Column(name = "charge", nullable = false, precision = 14, scale = 2)
  public BigDecimal charge;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
