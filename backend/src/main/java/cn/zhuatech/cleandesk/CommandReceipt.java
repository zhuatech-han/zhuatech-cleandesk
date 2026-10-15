// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 账号范围内的幂等命令与脱敏结果恢复。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "command_receipt")
public class CommandReceipt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "account_id", nullable = false)
  public Long accountId;

  @Column(name = "request_key", nullable = false, length = 36)
  public String requestKey;

  @Column(name = "action", nullable = false, length = 120)
  public String action;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;

  @Column(name = "result_json", nullable = false, columnDefinition = "longtext")
  public String resultJson;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
