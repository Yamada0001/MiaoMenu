package com.fluxcraft.MiaoMenu.menu.requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.PlaceholderUtils;

/**
 * 需求条件评估引擎。
 *
 * <p>设计原则：任何无法确定的输入都按"不满足"处理（fail-closed），
 * 绝不因为配置笔误、依赖缺失或占位符未解析而放行。</p>
 */
public class RequirementService {
    private static final String INVALID_REQUIREMENT_TYPE = "__invalid_requirement__";
    private static final int MAX_NESTING_DEPTH = 16;

    private final MiaoMenu plugin;
    private final Map<String, RequirementEvaluator> evaluators = new ConcurrentHashMap<>();
    private final Set<String> reportedConfigurationErrors = ConcurrentHashMap.newKeySet();

    public RequirementService(MiaoMenu plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        register("permission", this::evaluatePermission);
        register("placeholder_equals", this::evaluatePlaceholderEquals);
        register("placeholder-not-equals", this::evaluatePlaceholderNotEquals);
        register("placeholder_not_equals", this::evaluatePlaceholderNotEquals);
        register("placeholder_contains", this::evaluatePlaceholderContains);
        register("advancement", this::evaluateAdvancement);
        register("progress", this::evaluateProgress);
        register("score_gte", this::evaluateScoreGreaterOrEqual);
        register("score_lte", this::evaluateScoreLessOrEqual);
        register("score_equals", this::evaluateScoreEquals);
        register("score_range", this::evaluateScoreRange);
        register("block", this::evaluateBlockReference);
    }

    public void register(String type, RequirementEvaluator evaluator) {
        if (type == null || type.isBlank() || evaluator == null) {
            throw new IllegalArgumentException("Requirement type and evaluator are required");
        }
        evaluators.put(type.toLowerCase(Locale.ROOT), evaluator);
    }

    /** 每次重载菜单时清空错误去重集合，使"修好后再次写错"仍能被记录。 */
    public void resetReportedErrors() {
        reportedConfigurationErrors.clear();
    }

    public Map<String, RequirementBlock> loadBlocks(ConfigurationSection section) {
        return loadBlocks("<unknown>", section);
    }

    public Map<String, RequirementBlock> loadBlocks(String menuName, ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, RequirementBlock> blocks = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (key.indexOf('.') >= 0) {
                reportConfigurationError(menuName, "requirement_blocks." + key,
                        "requirement block names cannot contain '.'");
                continue;
            }
            Object configuredRequirements = section.get(key + ".requirements");
            if (configuredRequirements == null && section.isList(key)) {
                configuredRequirements = section.get(key);
            }
            List<Map<?, ?>> requirements = readRequirementList(
                    configuredRequirements,
                    menuName,
                    "requirement_blocks." + key + ".requirements"
            );
            if (requirements.isEmpty()) {
                // 块被定义却没有任何条件：这几乎总是键名拼写错误，必须 fail-closed。
                reportConfigurationError(menuName, "requirement_blocks." + key,
                        "requirement block has no requirements (check the 'requirements' key)");
                requirements = invalidRequirementList();
            }
            String denyMessage = section.getString(key + ".deny_message");
            String fallbackMenu = section.getString(key + ".fallback_menu");
            blocks.put(key, new RequirementBlock(key, requirements, denyMessage, fallbackMenu));
        }
        return Collections.unmodifiableMap(blocks);
    }

    public List<Map<?, ?>> readRequirementList(Object configuredValue, String menuName, String location) {
        if (configuredValue == null) {
            return Collections.emptyList();
        }
        if (!(configuredValue instanceof List<?> rawList)) {
            reportConfigurationError(menuName, location, "requirements must be a list of maps");
            return invalidRequirementList();
        }
        List<Map<?, ?>> requirements = new ArrayList<>();
        for (Object element : rawList) {
            if (!(element instanceof Map<?, ?> map)) {
                reportConfigurationError(menuName, location, "each requirement must be a map");
                return invalidRequirementList();
            }
            requirements.add(map);
        }
        return Collections.unmodifiableList(requirements);
    }

    public boolean validateRequirementBlocks(String menuName, Map<String, RequirementBlock> blocks) {
        boolean valid = true;
        for (RequirementBlock block : blocks.values()) {
            valid &= validateRequirements(
                    menuName,
                    "requirement_blocks." + block.key() + ".requirements",
                    blocks,
                    block.requirements()
            );
        }
        return valid;
    }

    public boolean validateRequirements(
            String menuName,
            String location,
            Map<String, RequirementBlock> blocks,
            List<Map<?, ?>> requirements
    ) {
        boolean valid = true;
        for (int index = 0; index < requirements.size(); index++) {
            String error = getValidationError(requirements.get(index), blocks);
            if (error != null) {
                reportConfigurationError(menuName, location + "[" + index + "]", error);
                valid = false;
            }
        }
        return valid;
    }

    public boolean validateConditionGroup(
            String menuName,
            String location,
            Map<String, RequirementBlock> blocks,
            ConditionGroup group
    ) {
        return validateConditionGroup(menuName, location, blocks, group, 0);
    }

    private boolean validateConditionGroup(
            String menuName,
            String location,
            Map<String, RequirementBlock> blocks,
            ConditionGroup group,
            int depth
    ) {
        if (depth > MAX_NESTING_DEPTH) {
            reportConfigurationError(menuName, location, "condition nesting is too deep");
            return false;
        }
        boolean valid = validateRequirements(menuName, location + ".requirements", blocks, group.requirements());
        for (int index = 0; index < group.children().size(); index++) {
            valid &= validateConditionGroup(
                    menuName,
                    location + ".children[" + index + "]",
                    blocks,
                    group.children().get(index),
                    depth + 1
            );
        }
        return valid;
    }

    public RequirementResult evaluate(Player player, String menuName, Map<String, RequirementBlock> blocks, List<Map<?, ?>> requirements) {
        RequirementResult.RequirementContext context = new RequirementResult.RequirementContext(
                menuName, blocks, new HashSet<>(), requirements);
        return evaluateInternal(player, context);
    }

    public RequirementResult evaluateGroup(Player player, String menuName, Map<String, RequirementBlock> blocks, ConditionGroup group) {
        return evaluateGroup(player, menuName, blocks, group, 0);
    }

    private RequirementResult evaluateGroup(
            Player player,
            String menuName,
            Map<String, RequirementBlock> blocks,
            ConditionGroup group,
            int depth
    ) {
        if (depth > MAX_NESTING_DEPTH) {
            reportConfigurationError(menuName, "runtime condition", "condition nesting is too deep");
            return RequirementResult.locked(null);
        }
        boolean empty = group.requirements().isEmpty() && group.children().isEmpty();
        if (empty) {
            // 空 AND 组恒真；空 OR 组没有任何可满足的分支，必须拒绝。
            return group.operator() == ConditionGroup.Operator.AND
                    ? RequirementResult.allow()
                    : RequirementResult.locked(null);
        }

        ConditionGroup.Operator operator = group.operator();
        RequirementResult firstFailure = null;

        // 任何一条 requirement 配置非法时整组 fail-closed：
        // 无法确定作者意图时宁可拒绝，也不能让 OR 的其它分支放行。
        for (Map<?, ?> rawRequirement : group.requirements()) {
            String error = getValidationError(rawRequirement, blocks);
            if (error != null) {
                reportConfigurationError(menuName, "runtime condition", error);
                return RequirementResult.locked(requirementMessage(rawRequirement));
            }
        }

        for (Map<?, ?> rawRequirement : group.requirements()) {
            RequirementResult result = evaluateSingle(player, menuName, blocks, rawRequirement);
            if (operator == ConditionGroup.Operator.OR) {
                if (result.allowed()) {
                    return RequirementResult.allow();
                }
                if (firstFailure == null) {
                    firstFailure = result;
                }
                continue;
            }
            if (!result.allowed()) {
                return mergeFailure(result, rawRequirement);
            }
        }

        for (ConditionGroup child : group.children()) {
            RequirementResult result = evaluateGroup(player, menuName, blocks, child, depth + 1);
            if (operator == ConditionGroup.Operator.OR) {
                if (result.allowed()) {
                    return RequirementResult.allow();
                }
                if (firstFailure == null) {
                    firstFailure = result;
                }
                continue;
            }
            if (!result.allowed()) {
                return result;
            }
        }

        if (operator == ConditionGroup.Operator.AND) {
            return RequirementResult.allow();
        }
        // OR 组全部失败：保留第一个失败项的自定义文案与回退菜单。
        return firstFailure != null ? firstFailure : RequirementResult.locked(null);
    }

    /**
     * 求值单条条件，并把该条件自身的 deny_message / fallback_menu 合并进结果。
     */
    private RequirementResult evaluateSingle(
            Player player,
            String menuName,
            Map<String, RequirementBlock> blocks,
            Map<?, ?> rawRequirement
    ) {
        String error = getValidationError(rawRequirement, blocks);
        if (error != null) {
            reportConfigurationError(menuName, "runtime condition", error);
            return RequirementResult.locked(requirementMessage(rawRequirement));
        }
        String type = getString(rawRequirement, "type", null);
        if (type == null) {
            return RequirementResult.locked(requirementMessage(rawRequirement));
        }
        RequirementEvaluator evaluator = evaluators.get(type.toLowerCase(Locale.ROOT));
        if (evaluator == null) {
            reportConfigurationError(menuName, "runtime condition", "unknown requirement type '" + type + "'");
            return RequirementResult.locked(requirementMessage(rawRequirement));
        }
        RequirementResult.RequirementContext ctx = new RequirementResult.RequirementContext(
                menuName, blocks, new HashSet<>(), Collections.singletonList(rawRequirement)
        );
        try {
            RequirementResult result = evaluator.evaluate(player, ctx);
            return result == null ? RequirementResult.deny(null, null) : result;
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(
                    java.util.logging.Level.WARNING,
                    "Requirement evaluation failed in menu '" + menuName + "' for type '" + type + "'",
                    e
            );
            return RequirementResult.deny(null, null);
        }
    }

    /**
     * AND 组失败时，优先使用条件自身产出的消息，其次才是配置里的静态文案。
     */
    private RequirementResult mergeFailure(RequirementResult result, Map<?, ?> rawRequirement) {
        String denyMessage = firstNonBlank(result.denyMessage(), requirementMessage(rawRequirement));
        String fallbackMenu = firstNonBlank(result.fallbackMenu(), getString(rawRequirement, "fallback_menu", null));
        return result.locked()
                ? RequirementResult.locked(denyMessage, fallbackMenu)
                : RequirementResult.deny(denyMessage, fallbackMenu);
    }

    private String requirementMessage(Map<?, ?> rawRequirement) {
        return firstNonBlank(
                getString(rawRequirement, "deny_message", null),
                getString(rawRequirement, "lock_message", null)
        );
    }

    public RequirementResult evaluateBlock(Player player, RequirementResult.RequirementContext parentContext, String blockKey) {
        RequirementBlock block = parentContext.blocks().get(blockKey);
        if (block == null) {
            reportConfigurationError(parentContext.menuName(), "runtime condition",
                    "unknown requirement block '" + blockKey + "'");
            return RequirementResult.deny(null, null);
        }
        if (!parentContext.visitedBlocks().add(blockKey)) {
            reportConfigurationError(parentContext.menuName(), "requirement_blocks." + blockKey,
                    "circular requirement block reference");
            return RequirementResult.deny(null, null);
        }
        try {
            RequirementResult.RequirementContext blockContext = new RequirementResult.RequirementContext(
                    parentContext.menuName(),
                    parentContext.blocks(),
                    parentContext.visitedBlocks(),
                    block.requirements()
            );
            RequirementResult result = evaluateInternal(player, blockContext);
            if (result.allowed()) {
                return result;
            }
            String denyMessage = firstNonBlank(result.denyMessage(), block.denyMessage());
            String fallbackMenu = firstNonBlank(result.fallbackMenu(), block.fallbackMenu());
            return RequirementResult.deny(denyMessage, fallbackMenu);
        } finally {
            parentContext.visitedBlocks().remove(blockKey);
        }
    }

    private RequirementResult evaluateInternal(Player player, RequirementResult.RequirementContext context) {
        for (Map<?, ?> rawRequirement : context.requirements()) {
            RequirementResult result = evaluateSingle(
                    player, context.menuName(), context.blocks(), rawRequirement);
            if (!result.allowed()) {
                String denyMessage = firstNonBlank(result.denyMessage(), requirementMessage(rawRequirement));
                String fallbackMenu = firstNonBlank(
                        result.fallbackMenu(), getString(rawRequirement, "fallback_menu", null));
                return RequirementResult.deny(denyMessage, fallbackMenu);
            }
        }
        return RequirementResult.allow();
    }

    private RequirementResult evaluatePermission(Player player, RequirementResult.RequirementContext context) {
        String permission = getString(context.requirements().getFirst(), "permission", null);
        if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
            return RequirementResult.allow();
        }
        return RequirementResult.deny(null, null);
    }

    private RequirementResult evaluatePlaceholderEquals(Player player, RequirementResult.RequirementContext context) {
        return comparePlaceholder(player, context, Comparison.EQUALS);
    }

    private RequirementResult evaluatePlaceholderNotEquals(Player player, RequirementResult.RequirementContext context) {
        return comparePlaceholder(player, context, Comparison.NOT_EQUALS);
    }

    private RequirementResult evaluatePlaceholderContains(Player player, RequirementResult.RequirementContext context) {
        Map<?, ?> requirement = context.requirements().getFirst();
        String placeholder = getString(requirement, "placeholder", null);
        String expected = getString(requirement, "value", null);
        if (placeholder == null || placeholder.isBlank() || expected == null || expected.isBlank()) {
            return RequirementResult.deny(null, null);
        }
        String resolved = resolvePlaceholder(player, placeholder);
        if (resolved == null) {
            return RequirementResult.deny(null, null);
        }
        return resolved.contains(expected) ? RequirementResult.allow() : RequirementResult.deny(null, null);
    }

    private RequirementResult comparePlaceholder(
            Player player,
            RequirementResult.RequirementContext context,
            Comparison comparison
    ) {
        Map<?, ?> requirement = context.requirements().getFirst();
        String placeholder = getString(requirement, "placeholder", null);
        String expected = getString(requirement, "value", null);
        if (placeholder == null || placeholder.isBlank() || expected == null) {
            return RequirementResult.deny(null, null);
        }
        String resolved = resolvePlaceholder(player, placeholder);
        if (resolved == null) {
            return RequirementResult.deny(null, null);
        }
        boolean matched = Objects.equals(resolved.trim(), expected.trim());
        if (comparison == Comparison.NOT_EQUALS) {
            matched = !matched;
        }
        return matched ? RequirementResult.allow() : RequirementResult.deny(null, null);
    }

    /**
     * 解析占位符；若解析结果仍是未替换的原串（PAPI 缺失或 expansion 不存在），
     * 返回 {@code null} 让调用方 fail-closed。
     */
    private String resolvePlaceholder(Player player, String placeholder) {
        String resolved = PlaceholderUtils.parse(player, placeholder, plugin);
        if (resolved == null) {
            return null;
        }
        if (resolved.equals(placeholder) && placeholder.indexOf('%') >= 0) {
            return null;
        }
        return resolved;
    }

    private RequirementResult evaluateAdvancement(Player player, RequirementResult.RequirementContext context) {
        String advancementKey = getString(context.requirements().getFirst(), "advancement", null);
        if (advancementKey == null || advancementKey.isBlank()) {
            return RequirementResult.deny(null, null);
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(advancementKey);
        if (namespacedKey == null) {
            return RequirementResult.deny(null, null);
        }
        org.bukkit.Server server = server();
        if (server == null) {
            return RequirementResult.deny(null, null);
        }
        Advancement advancement = server.getAdvancement(namespacedKey);
        if (advancement == null) {
            return RequirementResult.deny(null, null);
        }
        boolean completed = player.getAdvancementProgress(advancement).isDone();
        return completed ? RequirementResult.allow() : RequirementResult.deny(null, null);
    }

    private RequirementResult evaluateProgress(Player player, RequirementResult.RequirementContext context) {
        Map<?, ?> requirement = context.requirements().getFirst();
        Integer threshold = getInteger(requirement, "value");
        if (requirement.containsKey("value") && threshold == null) {
            return RequirementResult.deny(null, null);
        }
        Integer currentScore = getScore(player, requirement);
        if (currentScore == null) {
            return RequirementResult.deny(null, null);
        }
        if (threshold == null) {
            return currentScore > 0 ? RequirementResult.allow() : RequirementResult.deny(null, null);
        }
        return currentScore >= threshold ? RequirementResult.allow() : RequirementResult.deny(null, null);
    }

    private RequirementResult evaluateScoreComparison(
            Player player,
            RequirementResult.RequirementContext context,
            ScoreComparator comparator
    ) {
        Map<?, ?> req = context.requirements().getFirst();
        Integer currentScore = getScore(player, req);
        Integer expected = getInteger(req, "value");
        if (currentScore == null || expected == null) {
            return RequirementResult.deny(null, null);
        }
        return comparator.test(currentScore, expected) ? RequirementResult.allow() : RequirementResult.deny(null, null);
    }

    private RequirementResult evaluateScoreGreaterOrEqual(Player player, RequirementResult.RequirementContext context) {
        return evaluateScoreComparison(player, context, (current, expected) -> current >= expected);
    }

    private RequirementResult evaluateScoreLessOrEqual(Player player, RequirementResult.RequirementContext context) {
        return evaluateScoreComparison(player, context, (current, expected) -> current <= expected);
    }

    private RequirementResult evaluateScoreEquals(Player player, RequirementResult.RequirementContext context) {
        return evaluateScoreComparison(player, context, (current, expected) -> current == expected);
    }

    private RequirementResult evaluateScoreRange(Player player, RequirementResult.RequirementContext context) {
        Map<?, ?> req = context.requirements().getFirst();
        Integer currentScore = getScore(player, req);
        Integer min = getInteger(req, "min");
        Integer max = getInteger(req, "max");
        if (currentScore == null || min == null || max == null) {
            return RequirementResult.deny(null, null);
        }
        return currentScore >= min && currentScore <= max ? RequirementResult.allow() : RequirementResult.deny(null, null);
    }

    /**
     * 读取玩家在指定 objective 上的分数。
     *
     * <p>从未设置过分数的玩家视为"不满足"，而不是当作 0 分——否则
     * {@code score_lte} 之类的上限条件会对所有新玩家恒真。</p>
     */
    private Integer getScore(Player player, Map<?, ?> requirement) {
        String objectiveName = getString(requirement, "objective", null);
        if (objectiveName == null || objectiveName.isBlank()) {
            return null;
        }
        Objective objective = player.getScoreboard().getObjective(objectiveName);
        if (objective == null) {
            org.bukkit.Server server = server();
            if (server == null) {
                return null;
            }
            Scoreboard main = server.getScoreboardManager().getMainScoreboard();
            objective = main.getObjective(objectiveName);
        }
        if (objective == null) {
            return null;
        }
        Score score = objective.getScore(player.getName());
        return score.isScoreSet() ? score.getScore() : null;
    }

    private Integer getInteger(Map<?, ?> requirement, String key) {
        Object raw = requirement.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (!Double.isFinite(value) || value != Math.rint(value)) {
                return null;
            }
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private RequirementResult evaluateBlockReference(Player player, RequirementResult.RequirementContext context) {
        String blockKey = getString(context.requirements().getFirst(), "block", null);
        if (blockKey == null || blockKey.isBlank()) {
            return RequirementResult.deny(null, null);
        }
        return evaluateBlock(player, context, blockKey);
    }

    private String getValidationError(Map<?, ?> requirement, Map<String, RequirementBlock> blocks) {
        if (requirement == null || requirement.isEmpty()) {
            return "requirement cannot be empty";
        }
        String type = getString(requirement, "type", null);
        if (type == null || type.isBlank()) {
            return "missing required type";
        }
        String normalizedType = type.toLowerCase(Locale.ROOT);
        if (INVALID_REQUIREMENT_TYPE.equals(normalizedType)) {
            return "invalid requirement list";
        }
        if (!evaluators.containsKey(normalizedType)) {
            return "unknown requirement type '" + type + "'";
        }

        return switch (normalizedType) {
            case "permission" -> hasNonBlankString(requirement, "permission") ? null : "missing required permission";
            case "placeholder_equals", "placeholder-not-equals", "placeholder_not_equals" ->
                    hasNonBlankString(requirement, "placeholder") && hasValue(requirement, "value")
                            ? null
                            : "placeholder requirements need placeholder and value";
            case "placeholder_contains" ->
                    hasNonBlankString(requirement, "placeholder") && hasNonBlankString(requirement, "value")
                            ? null
                            : "placeholder_contains needs a non-blank placeholder and value";
            case "advancement" -> validateAdvancement(requirement);
            case "progress" -> validateProgress(requirement);
            case "score_gte", "score_lte", "score_equals" ->
                    hasNonBlankString(requirement, "objective") && getInteger(requirement, "value") != null
                            ? null
                            : "score requirements need objective and integer value";
            case "score_range" -> validateScoreRange(requirement);
            case "block" -> validateBlockReference(requirement, blocks);
            default -> null;
        };
    }

    private String validateAdvancement(Map<?, ?> requirement) {
        String advancement = getString(requirement, "advancement", null);
        if (advancement == null || advancement.isBlank()) {
            return "missing required advancement";
        }
        NamespacedKey key = NamespacedKey.fromString(advancement);
        if (key == null) {
            return "invalid advancement key '" + advancement + "'";
        }
        org.bukkit.Server server = server();
        if (server != null && server.getAdvancement(key) == null) {
            return "unknown advancement '" + advancement + "'";
        }
        return null;
    }

    /** 单元测试环境下 server 可能不可用。 */
    private org.bukkit.Server server() {
        return plugin == null ? null : plugin.getServer();
    }

    private String validateProgress(Map<?, ?> requirement) {
        if (!hasNonBlankString(requirement, "objective")) {
            return "missing required objective";
        }
        if (requirement.containsKey("value") && getInteger(requirement, "value") == null) {
            return "progress value must be an integer";
        }
        return null;
    }

    private String validateScoreRange(Map<?, ?> requirement) {
        Integer min = getInteger(requirement, "min");
        Integer max = getInteger(requirement, "max");
        if (!hasNonBlankString(requirement, "objective") || min == null || max == null) {
            return "score_range needs objective, integer min, and integer max";
        }
        return min <= max ? null : "score_range min cannot exceed max";
    }

    private String validateBlockReference(Map<?, ?> requirement, Map<String, RequirementBlock> blocks) {
        String blockKey = getString(requirement, "block", null);
        if (blockKey == null || blockKey.isBlank()) {
            return "missing required block";
        }
        return blocks != null && blocks.containsKey(blockKey) ? null : "unknown requirement block '" + blockKey + "'";
    }

    private boolean hasNonBlankString(Map<?, ?> requirement, String key) {
        Object value = requirement.get(key);
        return value instanceof String string && !string.isBlank();
    }

    private boolean hasValue(Map<?, ?> requirement, String key) {
        return requirement.containsKey(key) && requirement.get(key) != null;
    }

    private List<Map<?, ?>> invalidRequirementList() {
        return Collections.singletonList(Map.of("type", INVALID_REQUIREMENT_TYPE));
    }

    private void reportConfigurationError(String menuName, String location, String error) {
        String identifier = menuName + "|" + location + "|" + error;
        if (!reportedConfigurationErrors.add(identifier)) {
            return;
        }
        logger().warning("Invalid requirement in menu '" + menuName + "' at " + location + ": " + error);
    }

    /** 插件实例可能为空（单元测试），此时退回全局 logger。 */
    private Logger logger() {
        if (plugin == null) {
            return Logger.getLogger("MiaoMenu");
        }
        return Objects.requireNonNullElse(plugin.getLogger(), Logger.getLogger("MiaoMenu"));
    }

    private String getString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    public RequirementResult checkViewRequirement(Player player, String menuName,
            Map<String, RequirementBlock> blocks, List<Map<?, ?>> viewRequirements,
            String denyMessage, String fallbackMenu) {
        RequirementResult result = evaluate(player, menuName, blocks, viewRequirements);
        if (!result.allowed()) {
            return RequirementResult.deny(
                    firstNonBlank(result.denyMessage(), denyMessage),
                    firstNonBlank(result.fallbackMenu(), fallbackMenu)
            );
        }
        return result;
    }

    private enum Comparison {
        EQUALS,
        NOT_EQUALS
    }

    @FunctionalInterface
    private interface ScoreComparator {
        boolean test(int current, int expected);
    }
}
