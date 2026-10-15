// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;
import java.time.Instant;

/** 授权作业图片；重编码PNG去掉原始元数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "work_photo")
public class Photo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "round_id", nullable = false)
  public Long roundId;

  @Column(name = "uploader_id", nullable = false)
  public Long uploaderId;

  @Column(name = "phase", nullable = false, length = 12)
  public String phase;

  @Column(name = "file_name", nullable = false, length = 80)
  public String fileName;

  @Column(name = "sha256", nullable = false, length = 64)
  public String sha256;

  @Column(name = "caption", nullable = false, length = 300)
  public String caption;

  @Column(name = "size", nullable = false)
  public long size;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
