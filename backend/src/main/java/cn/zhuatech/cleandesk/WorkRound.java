// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 每次实际作业轮次，拒收后原记录不覆盖。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "work_round")
public class WorkRound {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "visit_id", nullable = false)
  public Long visitId;

  @Column(name = "round_no", nullable = false)
  public int roundNo;

  @Column(name = "leader_id", nullable = false)
  public Long leaderId;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "status", nullable = false, length = 24)
  public String status;

  @Column(name = "started_at")
  public Instant startedAt;

  @Column(name = "completed_at")
  public Instant completedAt;

  @Column(name = "completion_note", nullable = false, length = 1000)
  public String completionNote;

  @Column(name = "acceptance_note", nullable = false, length = 1000)
  public String acceptanceNote;
}
