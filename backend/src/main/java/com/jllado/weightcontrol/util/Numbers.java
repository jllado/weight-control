package com.jllado.weightcontrol.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Numbers {

    private Numbers() {
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentage(long number, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(number)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
