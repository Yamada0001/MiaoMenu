package com.fluxcraft.MiaoMenu.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxcraft.MiaoMenu.MiaoMenu;

class VariableStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndResolvesGlobalAndPlayerVariables() {
        UUID playerId = UUID.randomUUID();
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        VariableStore store = new VariableStore(plugin);
        store.setGlobal("season", "summer");
        store.setPlayer(playerId, "visits", "2");
        assertEquals("3", store.addPlayer(playerId, "visits", 1));

        VariableStore reloaded = new VariableStore(plugin);
        reloaded.load();

        assertEquals("summer", reloaded.getGlobal("season").orElseThrow());
        assertEquals("3", reloaded.getPlayer(playerId, "visits").orElseThrow());
        assertEquals(
                "summer/3",
                reloaded.resolve(player, "%miao_global_season%/{player:visits}")
        );
    }

    @Test
    void rejectsInvalidKeysAndNonNumericAddition() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        VariableStore store = new VariableStore(plugin);

        assertThrows(IllegalArgumentException.class, () -> store.setGlobal("bad key", "value"));
        store.setGlobal("text", "hello");
        assertThrows(IllegalArgumentException.class, () -> store.addGlobal("text", 1));
    }
}
