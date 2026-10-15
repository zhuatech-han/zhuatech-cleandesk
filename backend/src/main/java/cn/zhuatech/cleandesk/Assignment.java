// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 某轮人员指派、费率快照、实际工时与唯一结算归属。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "work_assignment")
public class Assignment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "round_id", nullable = false)
  public Long roundId;

  @Column(name = "worker_id", nullable = false)
  public Long workerId;

  @Column(name = "hourly_rate", nullable = false, precision = 14, scale = 2)
  public BigDecimal hourlyRate;

  @Column(name = "actual_minutes", nullable = false)
  public int actualMinutes = 0;

  @Column(name = "cost", nullable = false, precision = 14, scale = 2)
  public BigDecimal cost;

  @Column(name = "settlement_id")
  public Long settlementId;
}
