package com.fluxcraft.MiaoMenu.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxcraft.MiaoMenu.MiaoMenu;

class ConfigManagerExampleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultModeOnlyGeneratesInventoryJavaExample() {
        MiaoMenu plugin = configuredPlugin("DEFAULT");

        new ConfigManager(plugin).checkAndRefreshMenus();

        verify(plugin).saveResource("java_menus/test.yml", false);
        verify(plugin, never()).saveResource("dialog_menus/test.yml", false);
    }

    @Test
    void dialogModeOnlyGeneratesDialogJavaExample() {
        MiaoMenu plugin = configuredPlugin("DIALOG");

        new ConfigManager(plugin).checkAndRefreshMenus();

        verify(plugin).saveResource("dialog_menus/test.yml", false);
        verify(plugin, never()).saveResource("java_menus/test.yml", false);
    }

    private MiaoMenu configuredPlugin(String mode) {
        MiaoMenu plugin = mock(MiaoMenu.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getConfig()).thenReturn(config);
        when(config.getInt("menu-version", 0)).thenReturn(8);
        when(config.getBoolean("settings.auto-generate-examples", true)).thenReturn(true);
        when(config.getString("settings.java-menu-mode", "DEFAULT")).thenReturn(mode);
        return plugin;
    }
}
