package com.jllado.weightcontrol.domain;

public enum ProgressPhotoSide {
    FRONT,
    LEFT,
    RIGHT;

    public String path(Weight weight) {
        return switch (this) {
            case FRONT -> weight.getPhotoFrontPath();
            case LEFT -> weight.getPhotoLeftPath();
            case RIGHT -> weight.getPhotoRightPath();
        };
    }

    public String fileLabel() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
