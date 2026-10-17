package com.fluxcraft.MiaoMenu.managers;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.foliacall.FoliaFactory;
import com.fluxcraft.MiaoMenu.menu.action.ActionRegistry;
import com.fluxcraft.MiaoMenu.menu.requirement.ConditionGroup;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementService;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

public final class PeriodicTaskManager {
    private final MiaoMenu plugin;
    private final ActionRegistry actionRegistry;
    private final RequirementService requirementService;
    private final List<Runnable> cancellations = new CopyOnWriteArrayList<>();

    public PeriodicTaskManager(MiaoMenu plugin, ActionRegistry actionRegistry, RequirementService requirementService) {
        this.plugin = plugin;
        this.actionRegistry = actionRegistry;
        this.requirementService = requirementService;
    }

    public synchronized void reload() {
        shutdown();
        File file = new File(plugin.getDataFolder(), "tasks.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection tasks = configuration.getConfigurationSection("tasks");
        if (tasks == null) {
            return;
        }
        for (String name : tasks.getKeys(false)) {
            ConfigurationSection section = tasks.getConfigurationSection(name);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            try {
                TaskDefinition definition = parse(name, section);
                schedule(definition);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not load periodic task " + name, e);
            }
        }
    }

    public synchronized void shutdown() {
        cancellations.forEach(Runnable::run);
        cancellations.clear();
    }

    private TaskDefinition parse(String name, ConfigurationSection section) {
        long interval = section.getLong("interval", 1200L);
        long delay = section.getLong("delay", interval);
        if (interval < 1L || delay < 0L) {
            throw new IllegalArgumentException("Task interval must be positive and delay cannot be negative");
        }
        List<String> actions = section.getStringList("actions");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("Periodic task requires at least one action");
        }
        ConditionGroup conditions = ConditionGroup.fromLegacyConditions(null);
        ConfigurationSection conditionSection = section.getConfigurationSection("conditions");
        if (conditionSection != null) {
            conditions = ConditionGroup.fromYaml(conditionSection.getValues(false));
        }
        requirementService.validateConditionGroup("task:" + name, "conditions", Map.of(), conditions);
        return new TaskDefinition(
                name,
                interval,
                delay,
                section.getString("permission"),
                List.copyOf(actions),
                conditions
        );
    }

    private void schedule(TaskDefinition definition) {
        long initialDelay = Math.max(1L, definition.delay());
        if (FoliaFactory.isFolia()) {
            var task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    _ -> dispatch(definition),
                    initialDelay,
                    definition.interval()
            );
            cancellations.add(task::cancel);
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> dispatch(definition),
                definition.delay(),
                definition.interval()
        );
        cancellations.add(task::cancel);
    }

    private void dispatch(TaskDefinition definition) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Runnable task = () -> executeForPlayer(definition, player);
            if (FoliaFactory.isFolia()) {
                FoliaFactory.getAdapter().runTaskLaterForEntity(plugin, player, task, 1L);
            } else {
                task.run();
            }
        }
    }

    private void executeForPlayer(TaskDefinition definition, Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (definition.permission() != null
                && !definition.permission().isBlank()
                && !player.hasPermission(definition.permission())) {
            return;
        }
        if (!definition.conditions().requirements().isEmpty() || !definition.conditions().children().isEmpty()) {
            RequirementResult result = requirementService.evaluateGroup(
                    player,
                    "task:" + definition.name(),
                    Collections.emptyMap(),
                    definition.conditions()
            );
            if (!result.allowed()) {
                return;
            }
        }
        for (String action : definition.actions()) {
            if (action == null || action.isBlank()) {
                continue;
            }
            actionRegistry.dispatch(player, PlaceholderUtils.parse(player, action, plugin));
        }
    }

    private record TaskDefinition(
            String name,
            long interval,
            long delay,
            String permission,
            List<String> actions,
            ConditionGroup conditions
    ) {
    }
}
