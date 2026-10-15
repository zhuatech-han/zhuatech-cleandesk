// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.cleandesk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 金额、时长、周历和冲突边界，不依赖系统当天时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class PricingTest {
  @Test
  void decimalNeverSilentlyRoundsInput() {
    assertThrows(Problem.class, () -> Pricing.decimal("12.001", false));
  }

  @Test
  void negativeAmountRejected() {
    assertThrows(Problem.class, () -> Pricing.decimal("-0.01", true));
  }

  @Test
  void excessiveAmountRejected() {
    assertThrows(Problem.class, () -> Pricing.decimal("100000000", false));
  }

  @Test
  void zeroRequiresExplicitAllowance() {
    assertEquals(new BigDecimal("0.00"), Pricing.decimal("0", true));
    assertThrows(Problem.class, () -> Pricing.decimal("0", false));
  }

  ServiceRate rate() {
    var r = new ServiceRate();
    r.unit = "AREA";
    r.basePrice = new BigDecimal("20.00");
    r.unitPrice = new BigDecimal("1.25");
    r.minutesPerUnit = 2;
    return r;
  }

  @Test
  void exactExtrasAndPriceAreRoundedOnce() {
    var e = new ServiceExtra();
    e.price = new BigDecimal("2.50");
    assertEquals(
        new BigDecimal("41.94"), Pricing.price(rate(), new BigDecimal("15.55"), List.of(e)));
  }

  @Test
  void fixedVisitCannotMultiplyQuantity() {
    var r = rate();
    r.unit = "FIXED";
    assertThrows(Problem.class, () -> Pricing.price(r, new BigDecimal("2"), List.of()));
  }

  @Test
  void fractionalDurationRoundsUp() {
    assertEquals(32, Pricing.minutes(rate(), new BigDecimal("15.55"), List.of()));
  }

  @Test
  void durationLimitAndMinimumAreEnforced() {
    assertThrows(Problem.class, () -> Pricing.minutes(rate(), BigDecimal.ONE, List.of()));
    assertThrows(Problem.class, () -> Pricing.minutes(rate(), new BigDecimal("361"), List.of()));
  }

  @Test
  void recurringServiceKeepsLocalTimeAcrossDst() {
    var times =
        Pricing.occurrences(
            LocalDateTime.parse("2026-10-25T09:00"), 2, 1, ZoneId.of("America/New_York"));
    assertEquals(169, Duration.between(times.get(0), times.get(1)).toHours());
    assertEquals(9, times.get(1).atZone(ZoneId.of("America/New_York")).getHour());
  }

  @Test
  void missingAndAmbiguousTimesAreRejected() {
    for (String d : List.of("2026-03-08T02:30", "2026-11-01T01:30"))
      assertEquals(
          "AMBIGUOUS_LOCAL_TIME",
          assertThrows(
                  Problem.class,
                  () ->
                      Pricing.occurrences(
                          LocalDateTime.parse(d), 1, 0, ZoneId.of("America/New_York")))
              .getMessage());
  }

  @Test
  void recurrenceCannotExceedTwelveOrRepeatOneOff() {
    assertThrows(
        Problem.class, () -> Pricing.occurrences(LocalDateTime.now(), 13, 1, ZoneOffset.UTC));
    assertThrows(
        Problem.class, () -> Pricing.occurrences(LocalDateTime.now(), 2, 0, ZoneOffset.UTC));
  }

  @Test
  void halfOpenIntervalsAllowExactAdjacency() {
    var t = Instant.EPOCH;
    assertFalse(Pricing.overlaps(t, t.plusSeconds(60), t.plusSeconds(60), t.plusSeconds(120)));
    assertTrue(Pricing.overlaps(t, t.plusSeconds(61), t.plusSeconds(60), t.plusSeconds(120)));
  }
}
