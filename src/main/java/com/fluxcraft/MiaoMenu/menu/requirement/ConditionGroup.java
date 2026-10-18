package com.fluxcraft.MiaoMenu.menu.requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ConditionGroup(Operator operator, List<Map<?, ?>> requirements, List<ConditionGroup> children) {
    /**
     * 条件嵌套的最大深度。
     *
     * <p>解析期就按此上限截断，避免先递归到很深再校验；运行期求值复用同一
     * 上限，两处保持一致。</p>
     */
    public static final int MAX_NESTING_DEPTH = 16;

    public ConditionGroup {
        requirements = requirements != null ? Collections.unmodifiableList(requirements) : Collections.emptyList();
        children = children != null ? Collections.unmodifiableList(children) : Collections.emptyList();
    }

    public enum Operator {
        AND,
        OR
    }

    public static ConditionGroup fromYaml(Map<?, ?> yaml) {
        return fromYaml(yaml, 0);
    }

    /**
     * @param depth 当前嵌套层级，超过 {@link #MAX_NESTING_DEPTH} 立即失败，
     *              不再继续递归
     * @throws IllegalArgumentException 结构不合法或嵌套过深
     */
    private static ConditionGroup fromYaml(Map<?, ?> yaml, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException(
                    "Condition nesting is too deep (max " + MAX_NESTING_DEPTH + ")");
        }
        if (yaml == null) {
            return new ConditionGroup(Operator.AND, Collections.emptyList(), Collections.emptyList());
        }

        Object configuredOperator = yaml.get("operator");
        String opStr = configuredOperator instanceof String s ? s : "AND";
        if (!"AND".equalsIgnoreCase(opStr) && !"OR".equalsIgnoreCase(opStr)) {
            throw new IllegalArgumentException("Condition operator must be AND or OR");
        }
        Operator operator = "OR".equalsIgnoreCase(opStr) ? Operator.OR : Operator.AND;

        List<Map<?, ?>> requirements = new ArrayList<>();
        Object reqObj = yaml.get("requirements");
        if (reqObj != null && !(reqObj instanceof List<?>)) {
            throw new IllegalArgumentException("Condition requirements must be a list");
        }
        if (reqObj instanceof List<?> rawList) {
            for (Object element : rawList) {
                if (!(element instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("Each condition requirement must be a map");
                }
                requirements.add(map);
            }
        }

        List<ConditionGroup> children = new ArrayList<>();
        Object childrenObj = yaml.get("children");
        if (childrenObj != null && !(childrenObj instanceof List<?>)) {
            throw new IllegalArgumentException("Condition children must be a list");
        }
        if (childrenObj instanceof List<?> rawList) {
            for (Object element : rawList) {
                if (!(element instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("Each condition child must be a map");
                }
                children.add(fromYaml(map, depth + 1));
            }
        }

        return new ConditionGroup(operator, requirements, children);
    }

    public static ConditionGroup fromLegacyConditions(List<Map<?, ?>> legacyConditions) {
        if (legacyConditions == null || legacyConditions.isEmpty()) {
            return new ConditionGroup(Operator.AND, Collections.emptyList(), Collections.emptyList());
        }
        return new ConditionGroup(Operator.AND, new ArrayList<>(legacyConditions), Collections.emptyList());
    }
}
