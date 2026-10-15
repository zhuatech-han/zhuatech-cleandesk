// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 客户可见的服务状态事件，不包含内部薪酬或凭据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "visit_event")
public class VisitEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "visit_id", nullable = false)
  public Long visitId;

  @Column(name = "action", nullable = false, length = 40)
  public String action;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "actor", nullable = false, length = 120)
  public String actor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
