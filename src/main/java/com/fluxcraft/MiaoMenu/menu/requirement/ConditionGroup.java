package com.fluxcraft.MiaoMenu.menu.requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ConditionGroup(Operator operator, List<Map<?, ?>> requirements, List<ConditionGroup> children) {
    public ConditionGroup {
        requirements = requirements != null ? Collections.unmodifiableList(requirements) : Collections.emptyList();
        children = children != null ? Collections.unmodifiableList(children) : Collections.emptyList();
    }

    public enum Operator {
        AND,
        OR
    }

    public static ConditionGroup fromYaml(Map<?, ?> yaml) {
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
                children.add(fromYaml(map));
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
