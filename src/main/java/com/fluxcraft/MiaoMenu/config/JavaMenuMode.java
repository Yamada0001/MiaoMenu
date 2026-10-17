package com.fluxcraft.MiaoMenu.config;

import java.util.Locale;

public enum JavaMenuMode {
    DEFAULT,
    DIALOG;

    public static JavaMenuMode parse(String configured) {
        if (configured == null) {
            return DEFAULT;
        }
        return switch (configured.trim().toUpperCase(Locale.ROOT)) {
            case "DIALOG", "NATIVE_DIALOG", "NATIVE" -> DIALOG;
            default -> DEFAULT;
        };
    }
}
