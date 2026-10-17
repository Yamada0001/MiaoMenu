package com.fluxcraft.MiaoMenu.menu.requirement;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.javamenu.JavaMenu;

class RequirementServiceTest {
    @Test
    void permissionRequirementAllowsPlayerWithPermission() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("miao.test")).thenReturn(true);
        RequirementService service = new RequirementService(plugin);

        RequirementResult result = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "permission",
                "permission", "miao.test"
        )));

        assertTrue(result.allowed());
    }

    @Test
    void permissionRequirementDeniesPlayerWithoutPermission() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("miao.test")).thenReturn(false);
        RequirementService service = new RequirementService(plugin);

        RequirementResult result = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "permission",
                "permission", "miao.test"
        )));

        assertFalse(result.allowed());
    }

    @Test
    void missingPermissionFailsClosed() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        RequirementService service = new RequirementService(plugin);

        RequirementResult result = service.evaluate(mock(Player.class), "test", Map.of(), List.of(Map.of(
                "type", "permission"
        )));

        assertFalse(result.allowed());
    }

    @Test
    void unknownRequirementTypeFailsClosed() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        RequirementService service = new RequirementService(plugin);

        RequirementResult result = service.evaluate(mock(Player.class), "test", Map.of(), List.of(Map.of(
                "type", "permission_typo",
                "permission", "miao.test"
        )));

        assertFalse(result.allowed());
    }

    @Test
    void invalidConditionInOrGroupFailsClosed() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        Player player = mock(Player.class);
        when(player.hasPermission("miao.test")).thenReturn(true);
        RequirementService service = new RequirementService(plugin);
        ConditionGroup group = new ConditionGroup(
                ConditionGroup.Operator.OR,
                List.of(
                        Map.of("type", "permission_typo"),
                        Map.of("type", "permission", "permission", "miao.test")
                ),
                List.of()
        );

        RequirementResult result = service.evaluateGroup(player, "test", Map.of(), group);

        assertFalse(result.allowed());
    }

    @Test
    void scoreGreaterOrEqualRequirementAllowsEnoughScore() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        ScoreboardManager scoreboardManager = mock(ScoreboardManager.class);
        Scoreboard mainScoreboard = mock(Scoreboard.class);
        Scoreboard playerScoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Score score = mock(Score.class);
        Player player = mock(Player.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScoreboardManager()).thenReturn(scoreboardManager);
        when(scoreboardManager.getMainScoreboard()).thenReturn(mainScoreboard);
        when(player.getScoreboard()).thenReturn(playerScoreboard);
        when(player.getName()).thenReturn("Nina");
        when(playerScoreboard.getObjective("story")).thenReturn(objective);
        when(objective.getScore("Nina")).thenReturn(score);
        when(score.getScore()).thenReturn(15);

        RequirementService service = new RequirementService(plugin);
        RequirementResult result = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "score_gte",
                "objective", "story",
                "value", 10
        )));

        assertTrue(result.allowed());
    }

    @Test
    void scoreRangeRequirementDeniesOutsideRange() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        ScoreboardManager scoreboardManager = mock(ScoreboardManager.class);
        Scoreboard mainScoreboard = mock(Scoreboard.class);
        Scoreboard playerScoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Score score = mock(Score.class);
        Player player = mock(Player.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScoreboardManager()).thenReturn(scoreboardManager);
        when(scoreboardManager.getMainScoreboard()).thenReturn(mainScoreboard);
        when(player.getScoreboard()).thenReturn(playerScoreboard);
        when(player.getName()).thenReturn("Nina");
        when(playerScoreboard.getObjective("story")).thenReturn(objective);
        when(objective.getScore("Nina")).thenReturn(score);
        when(score.getScore()).thenReturn(30);

        RequirementService service = new RequirementService(plugin);
        RequirementResult result = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "score_range",
                "objective", "story",
                "min", 1,
                "max", 20
        )));

        assertFalse(result.allowed());
    }

    @Test
    void advancementConditionAllowsWhenCompleted() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        Player player = mock(Player.class);
        Advancement advancement = mock(Advancement.class);
        AdvancementProgress progress = mock(AdvancementProgress.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString("minecraft:story/root")))).thenReturn(advancement);
        when(player.getAdvancementProgress(advancement)).thenReturn(progress);
        when(progress.isDone()).thenReturn(true);

        RequirementService service = new RequirementService(plugin);
        RequirementResult result = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "advancement",
                "advancement", "minecraft:story/root"
        )));

        assertTrue(result.allowed());
    }

    @Test
    void advancementConditionDeniesWhenNotCompleted() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        Player player = mock(Player.class);
        Advancement advancement = mock(Advancement.class);
        AdvancementProgress progress = mock(AdvancementProgress.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString("minecraft:story/root")))).thenReturn(advancement);
        when(player.getAdvancementProgress(advancement)).thenReturn(progress);
        when(progress.isDone()).thenReturn(false);

        RequirementService service = new RequirementService(plugin);
        RequirementResult result = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "advancement",
                "advancement", "minecraft:story/root"
        )));

        assertFalse(result.allowed());
    }

    @Test
    void advancementConditionRefreshesAfterChange() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        Player player = mock(Player.class);
        Advancement advancement = mock(Advancement.class);
        AdvancementProgress progress = mock(AdvancementProgress.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getAdvancement(Objects.requireNonNull(NamespacedKey.fromString("minecraft:story/root")))).thenReturn(advancement);
        when(player.getAdvancementProgress(advancement)).thenReturn(progress);

        when(progress.isDone()).thenReturn(false);
        RequirementService service = new RequirementService(plugin);
        RequirementResult result1 = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "advancement",
                "advancement", "minecraft:story/root"
        )));
        assertFalse(result1.allowed());

        when(progress.isDone()).thenReturn(true);
        RequirementResult result2 = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "advancement",
                "advancement", "minecraft:story/root"
        )));
        assertTrue(result2.allowed());
    }

    @Test
    void conditionGroupAndLogicRequiresAllConditions() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("perm.a")).thenReturn(true);
        when(player.hasPermission("perm.b")).thenReturn(false);

        RequirementService service = new RequirementService(plugin);
        ConditionGroup group = new ConditionGroup(
                ConditionGroup.Operator.AND,
                List.of(
                        Map.of("type", "permission", "permission", "perm.a"),
                        Map.of("type", "permission", "permission", "perm.b")
                ),
                List.of()
        );

        RequirementResult result = service.evaluateGroup(player, "test", Map.of(), group);
        assertFalse(result.allowed());
    }

    @Test
    void conditionGroupAndLogicPassesWhenAllMet() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("perm.a")).thenReturn(true);
        when(player.hasPermission("perm.b")).thenReturn(true);

        RequirementService service = new RequirementService(plugin);
        ConditionGroup group = new ConditionGroup(
                ConditionGroup.Operator.AND,
                List.of(
                        Map.of("type", "permission", "permission", "perm.a"),
                        Map.of("type", "permission", "permission", "perm.b")
                ),
                List.of()
        );

        RequirementResult result = service.evaluateGroup(player, "test", Map.of(), group);
        assertTrue(result.allowed());
    }

    @Test
    void conditionGroupOrLogicPassesWithAnyCondition() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("perm.a")).thenReturn(false);
        when(player.hasPermission("perm.b")).thenReturn(true);

        RequirementService service = new RequirementService(plugin);
        ConditionGroup group = new ConditionGroup(
                ConditionGroup.Operator.OR,
                List.of(
                        Map.of("type", "permission", "permission", "perm.a"),
                        Map.of("type", "permission", "permission", "perm.b")
                ),
                List.of()
        );

        RequirementResult result = service.evaluateGroup(player, "test", Map.of(), group);
        assertTrue(result.allowed());
    }

    @Test
    void conditionGroupOrLogicFailsWhenNoneMet() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("perm.a")).thenReturn(false);
        when(player.hasPermission("perm.b")).thenReturn(false);

        RequirementService service = new RequirementService(plugin);
        ConditionGroup group = new ConditionGroup(
                ConditionGroup.Operator.OR,
                List.of(
                        Map.of("type", "permission", "permission", "perm.a"),
                        Map.of("type", "permission", "permission", "perm.b")
                ),
                List.of()
        );

        RequirementResult result = service.evaluateGroup(player, "test", Map.of(), group);
        assertFalse(result.allowed());
    }

    @Test
    void nestedConditionGroupOrInsideAnd() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("perm.required")).thenReturn(true);
        when(player.hasPermission("perm.opt_a")).thenReturn(false);
        when(player.hasPermission("perm.opt_b")).thenReturn(true);

        RequirementService service = new RequirementService(plugin);

        ConditionGroup orChild = new ConditionGroup(
                ConditionGroup.Operator.OR,
                List.of(
                        Map.of("type", "permission", "permission", "perm.opt_a"),
                        Map.of("type", "permission", "permission", "perm.opt_b")
                ),
                List.of()
        );
        ConditionGroup root = new ConditionGroup(
                ConditionGroup.Operator.AND,
                List.of(Map.of("type", "permission", "permission", "perm.required")),
                List.of(orChild)
        );

        RequirementResult result = service.evaluateGroup(player, "test", Map.of(), root);
        assertTrue(result.allowed());
    }

    @Test
    void rootLevelItemRequirementsAreLoadedAsConditionGroup() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.server_info.slot", 10);
        config.set("items.server_info.material", "KNOWLEDGE_BOOK");
        config.set("items.server_info.display_name", "&e&l服务器信息");
        config.set("items.server_info.operator", "AND");
        config.set("items.server_info.requirements", List.of(Map.of(
                "type", "permission",
                "permission", "vip.shop"
        )));

        RequirementService service = new RequirementService(plugin);
        JavaMenu menu = new JavaMenu("test", config, plugin, null, service);
        JavaMenu.MenuItem item = menu.getItem(10);

        assertTrue(item != null && item.isLocked(mock(Player.class), service, "test", Map.of()));
    }

    @Test
    void progressConditionDeniesWhenScoreBelowThreshold() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        ScoreboardManager scoreboardManager = mock(ScoreboardManager.class);
        Scoreboard mainScoreboard = mock(Scoreboard.class);
        Scoreboard playerScoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Score score = mock(Score.class);
        Player player = mock(Player.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScoreboardManager()).thenReturn(scoreboardManager);
        when(scoreboardManager.getMainScoreboard()).thenReturn(mainScoreboard);
        when(player.getScoreboard()).thenReturn(playerScoreboard);
        when(player.getName()).thenReturn("Nina");
        when(playerScoreboard.getObjective("story_progress")).thenReturn(objective);
        when(objective.getScore("Nina")).thenReturn(score);
        when(score.getScore()).thenReturn(1);

        RequirementService service = new RequirementService(plugin);
        RequirementResult result = service.evaluate(player, "test", Map.of(), List.of(Map.of(
                "type", "progress",
                "objective", "story_progress",
                "value", 3
        )));

        assertFalse(result.allowed());
    }

    @Test
    void hotReloadDoesNotAffectExistingMenuReferences() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        Player player = mock(Player.class);
        when(player.hasPermission("perm.stable")).thenReturn(true);

        RequirementService service = new RequirementService(plugin);
        ConditionGroup group = new ConditionGroup(
                ConditionGroup.Operator.AND,
                List.of(Map.of("type", "permission", "permission", "perm.stable")),
                List.of()
        );

        RequirementResult result1 = service.evaluateGroup(player, "test", Map.of(), group);
        assertTrue(result1.allowed());

        RequirementResult result2 = service.evaluateGroup(player, "test", Map.of(), group);
        assertTrue(result2.allowed());
    }
}
