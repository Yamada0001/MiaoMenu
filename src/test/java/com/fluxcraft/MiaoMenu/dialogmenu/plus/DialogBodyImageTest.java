package com.fluxcraft.MiaoMenu.dialogmenu.plus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验 Dialog 的 {@code image:} body 何时改用 CE 的 {@code <image:...>} 标签。
 *
 * <p>主题图是整屏尺寸，用 32×32 的物品图标永远显示不出"背景"效果；
 * CE 会拦截 Dialog 数据包（{@code intercept-packets.dialog}），标签才能显示成大图。</p>
 *
 * <p>这里只测决策逻辑：{@code DialogBody.plainMessage(...)} 需要服务端注册表，
 * 在单元测试环境里无法构造。</p>
 */
class DialogBodyImageTest {
    @Test
    void extractsThemeIdFromImageMaterial() {
        assertEquals("halloween", Body.themeImageId("image:halloween"));
        assertEquals("spring_sakura", Body.themeImageId("  IMAGE:Spring_Sakura  "));
        assertEquals("miaomenu:halloween", Body.themeImageId("image:miaomenu:halloween"));
    }

    @Test
    void ignoresNonImageMaterials() {
        assertNull(Body.themeImageId(null));
        assertNull(Body.themeImageId("STONE"));
        assertNull(Body.themeImageId("craftengine:foo"));
        assertNull(Body.themeImageId("image:"));
        assertNull(Body.themeImageId("image:   "));
    }

    @Test
    void usesTagOnlyWhenCraftEngineDialogImageIsEnabled() {
        assertTrue(Body.shouldUseCraftEngineImage("image:halloween", true));
        assertFalse(Body.shouldUseCraftEngineImage("image:halloween", false),
                "关闭 dialog-image: tag 时应退回物品图标");
        assertFalse(Body.shouldUseCraftEngineImage("STONE", true),
                "普通材质永远不走图片标签");
    }

    @Test
    void buildsNamespacedImageTag() {
        assertEquals("<image:miaomenu:halloween>", Body.craftEngineImageTag("halloween"));
        assertEquals("<image:miaomenu:autumn_forest>", Body.craftEngineImageTag("AUTUMN_FOREST"),
                "主题 ID 应归一化为小写");
    }
}
