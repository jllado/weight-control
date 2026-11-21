package com.jllado.weightcontrol.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/** Keep the means of exercise and day ratios exact, including repeating fractions at a scoring boundary. */
record WorkoutLoadRatio(BigInteger numerator, BigInteger denominator) {
    WorkoutLoadRatio {
        var divisor = numerator.gcd(denominator);
        numerator = numerator.divide(divisor);
        denominator = denominator.divide(divisor);
    }

    static WorkoutLoadRatio mean(List<WorkoutLoadRatio> ratios) {
        var sum = ratios.getFirst();
        for (int i = 1; i < ratios.size(); i++) {
            var next = ratios.get(i);
            sum = new WorkoutLoadRatio(sum.numerator.multiply(next.denominator).add(next.numerator.multiply(sum.denominator)),
                sum.denominator.multiply(next.denominator));
        }
        return new WorkoutLoadRatio(sum.numerator, sum.denominator.multiply(BigInteger.valueOf(ratios.size())));
    }

    int adjustment() {
        if (comparePercentage(75) <= 0) return -1;
        return comparePercentage(125) >= 0 ? 1 : 0;
    }

    String percentage() {
        var rounded = new BigDecimal(numerator).movePointRight(2).divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP);
        for (int threshold : List.of(75, 125)) {
            if (rounded.compareTo(BigDecimal.valueOf(threshold)) == 0) {
                int direction = comparePercentage(threshold);
                if (direction < 0) return "Just under " + threshold + "%";
                if (direction > 0) return "Just over " + threshold + "%";
            }
        }
        return rounded.stripTrailingZeros().toPlainString() + "%";
    }

    private int comparePercentage(int percent) {
        return numerator.multiply(BigInteger.valueOf(100)).compareTo(denominator.multiply(BigInteger.valueOf(percent)));
    }
}
