// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import jakarta.persistence.*;

/** 图片内容与轻量目录分开，避免列举照片时加载所有二进制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "photo_data")
public class PhotoData {
  @Id
  @Column(name = "photo_id")
  public Long photoId;

  @Lob
  @Column(nullable = false, columnDefinition = "longblob")
  @com.fasterxml.jackson.annotation.JsonIgnore
  public byte[] content;
}
