package com.fluxcraft.MiaoMenu.api;

public record DynamicListEntry(String value, String display) {
    public DynamicListEntry {
        value = value != null ? value : "";
        display = display != null ? display : value;
    }
}
