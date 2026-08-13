package com.jllado.weightcontrol.domain;

public enum MealType {
    BREAKFAST(0),
    LUNCH(1),
    DINNER(2),
    SNACK(3);

    private final int order;

    MealType(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
