package com.jllado.weightcontrol.api.dto;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record ApiMessage(String message) {
    }

    public record DeletionResponse(boolean deleted) {
    }
}
