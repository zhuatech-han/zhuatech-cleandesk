// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;

/** 人员本地每周工作时段，结束时刻不含在内。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "worker_availability")
public class Availability {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "worker_id", nullable = false)
  public Long workerId;

  @Column(name = "weekday", nullable = false)
  public int weekday;

  @Column(name = "start_time", nullable = false, length = 5)
  public String startTime;

  @Column(name = "end_time", nullable = false, length = 5)
  public String endTime;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
