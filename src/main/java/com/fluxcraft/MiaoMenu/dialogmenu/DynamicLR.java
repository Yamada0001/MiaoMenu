package com.fluxcraft.MiaoMenu.dialogmenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.api.DynamicListEntry;
import com.fluxcraft.MiaoMenu.api.DynamicListProvider;

public final class DynamicLR {
    private final MiaoMenu plugin;
    private final Map<String, DynamicListProvider> providers = new ConcurrentHashMap<>();

    public DynamicLR(MiaoMenu plugin) {
        this.plugin = plugin;
        registerBuiltIns();
    }

    public void register(String source, DynamicListProvider provider) {
        if (source == null || source.isBlank() || provider == null) {
            throw new IllegalArgumentException("Dynamic list source and provider are required");
        }
        providers.put(source.trim().toLowerCase(Locale.ROOT), provider);
    }

    public List<DynamicListEntry> resolve(String source, Player player, Map<String, Object> options) {
        DynamicListProvider provider = providers.get(source.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException("Unknown dynamic list source '" + source + "'");
        }
        List<DynamicListEntry> entries = provider.provide(
                player,
                Map.copyOf(options)
        );
        return entries != null ? List.copyOf(entries) : List.of();
    }

    private void registerBuiltIns() {
        register("online_players", (_, _) -> plugin.getServer().getOnlinePlayers().stream()
                .map(player -> new DynamicListEntry(player.getName(), player.getName()))
                .sorted(Comparator.comparing(DynamicListEntry::display, String.CASE_INSENSITIVE_ORDER))
                .toList());
        register("global_variable", (_, options) -> variableEntries(
                plugin.getVariableStore().getGlobal(stringOption(options, "variable")).orElse(""),
                stringOption(options, "separator", ",")
        ));
        register("player_variable", (player, options) -> variableEntries(
                plugin.getVariableStore().getPlayer(player.getUniqueId(), stringOption(options, "variable")).orElse(""),
                stringOption(options, "separator", ",")
        ));
    }

    private static List<DynamicListEntry> variableEntries(String configured, String separator) {
        if (configured.isBlank()) {
            return List.of();
        }
        String effectiveSeparator = separator.isEmpty() ? "," : separator;
        List<DynamicListEntry> entries = new ArrayList<>();
        for (String part : configured.split(Pattern.quote(effectiveSeparator))) {
            String value = part.trim();
            if (!value.isEmpty()) {
                entries.add(new DynamicListEntry(value, value));
            }
        }
        return entries;
    }

    private static String stringOption(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Dynamic list option '" + key + "' is required");
        }
        return String.valueOf(value);
    }

    private static String stringOption(Map<String, Object> options, String key, String fallback) {
        Object value = options.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
