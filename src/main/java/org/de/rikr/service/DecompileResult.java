package org.de.rikr.service;

public record DecompileResult(boolean success, String source, String message) {
    public static DecompileResult success(String source) {
        return new DecompileResult(true, source, null);
    }

    public static DecompileResult failure(String message) {
        return new DecompileResult(false, "", message);
    }
}
