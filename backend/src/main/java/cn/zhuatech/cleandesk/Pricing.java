// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import java.math.*;
import java.time.*;
import java.util.*;

/** 保洁计价与周期时间规则；不可静默舍入输入或跳过夏令时歧义。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class Pricing {
  private Pricing() {}

  /** 金额或数量只接受两位精度，拒绝负数、指数溢出及超限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal decimal(Object value, boolean zero) {
    try {
      var x = new BigDecimal(Objects.toString(value, "")).setScale(2, RoundingMode.UNNECESSARY);
      Rules.check(
          x.signum() >= (zero ? 0 : 1) && x.compareTo(new BigDecimal("99999999.99")) <= 0,
          "AMOUNT_INVALID");
      return x;
    } catch (ArithmeticException | NumberFormatException e) {
      throw new Problem(400, "AMOUNT_INVALID");
    }
  }

  /** 主服务加固定增项，每次报价统一到分。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal price(ServiceRate rate, BigDecimal units, List<ServiceExtra> extras) {
    Rules.check(
        !rate.unit.equals("FIXED") || units.compareTo(BigDecimal.ONE) == 0, "UNITS_INVALID");
    Rules.check(units.compareTo(new BigDecimal("10000")) <= 0, "UNITS_INVALID");
    return decimal(
        rate.basePrice
            .add(rate.unitPrice.multiply(units))
            .add(extras.stream().map(x -> x.price).reduce(BigDecimal.ZERO, BigDecimal::add))
            .setScale(2, RoundingMode.HALF_UP),
        false);
  }

  /** 工时指班组墙钟时长，非人员工时总和；上限12小时。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int minutes(ServiceRate rate, BigDecimal units, List<ServiceExtra> extras) {
    int n =
        units
                .multiply(BigDecimal.valueOf(rate.minutesPerUnit))
                .setScale(0, RoundingMode.CEILING)
                .intValueExact()
            + extras.stream().mapToInt(x -> x.minutes).sum();
    Rules.check(n >= 30 && n <= 720, "DURATION_INVALID");
    return n;
  }

  /** 按所在地每周本地时刻生成，不用固定UTC秒数代替周历。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<Instant> occurrences(LocalDateTime first, int count, int weeks, ZoneId zone) {
    Rules.check(
        count >= 1
            && count <= 12
            && (weeks == 0 || weeks == 1 || weeks == 2)
            && (count == 1 || weeks > 0),
        "RECURRENCE_INVALID");
    List<Instant> rows = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      var local = first.plusWeeks((long) i * weeks);
      var offsets = zone.getRules().getValidOffsets(local);
      Rules.check(offsets.size() == 1, "AMBIGUOUS_LOCAL_TIME");
      rows.add(local.toInstant(offsets.getFirst()));
    }
    return rows;
  }

  /** 半开时段，邻接允许；交通间隔由调度层按人员规则额外应用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean overlaps(Instant a, Instant b, Instant c, Instant d) {
    return a.isBefore(d) && c.isBefore(b);
  }
}
