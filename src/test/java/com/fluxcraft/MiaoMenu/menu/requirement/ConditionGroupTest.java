package com.fluxcraft.MiaoMenu.menu.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConditionGroupTest {
    @Test
    void parsesNestingUpToTheLimit() {
        Map<String, Object> yaml = nested(ConditionGroup.MAX_NESTING_DEPTH);

        ConditionGroup group = ConditionGroup.fromYaml(yaml);

        assertEquals(ConditionGroup.MAX_NESTING_DEPTH, depthOf(group));
    }

    @Test
    void rejectsNestingBeyondTheLimitWhileParsing() {
        Map<String, Object> yaml = nested(ConditionGroup.MAX_NESTING_DEPTH + 1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> ConditionGroup.fromYaml(yaml));

        assertTrue(error.getMessage().contains("too deep"), () -> "错误信息不明确：" + error.getMessage());
    }

    @Test
    void rejectsDeepNestingEvenWhenItIsTheOnlyProblem() {
        // 100 层嵌套：必须在解析期就失败，而不是递归到底再校验
        Map<String, Object> yaml = nested(100);

        assertThrows(IllegalArgumentException.class, () -> ConditionGroup.fromYaml(yaml));
    }

    @Test
    void parsesFlatGroupWithRequirementsAndOperator() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("operator", "OR");
        yaml.put("requirements", List.of(Map.of("type", "permission", "permission", "miao.test")));

        ConditionGroup group = ConditionGroup.fromYaml(yaml);

        assertEquals(ConditionGroup.Operator.OR, group.operator());
        assertEquals(1, group.requirements().size());
        assertTrue(group.children().isEmpty());
    }

    /** 构造 depth 层 children 嵌套，最内层带一条 requirement。 */
    private static Map<String, Object> nested(int depth) {
        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("requirements", List.of(Map.of("type", "permission", "permission", "miao.test")));
        if (depth == 0) {
            return leaf;
        }
        Map<String, Object> current = leaf;
        for (int level = 0; level < depth; level++) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("children", List.of(current));
            current = parent;
        }
        return current;
    }

    private static int depthOf(ConditionGroup group) {
        return depth(group, 0);
    }

    private static int depth(ConditionGroup group, int current) {
        int deepest = current;
        for (ConditionGroup child : group.children()) {
            deepest = Math.max(deepest, depth(child, current + 1));
        }
        return deepest;
    }
}
