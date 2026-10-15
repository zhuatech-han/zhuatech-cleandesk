// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 保洁业务服务；日期计算与财务使用明确时区和精确小数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootApplication
public class CleanDeskApplication {
  /** 启动数据库服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(CleanDeskApplication.class, args);
  }

  /** 注入可验收的UTC时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
