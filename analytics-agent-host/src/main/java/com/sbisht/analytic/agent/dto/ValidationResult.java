package com.sbisht.analytic.agent.dto;

public record ValidationResult(boolean valid,
                               String errorMessage) {

    public static ValidationResult valids() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }

}
