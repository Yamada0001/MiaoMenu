package com.fluxcraft.MiaoMenu.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 校验 {@code image:} 到资源包模型 ID 的映射。
 *
 * <p>主题资源包里的模型在 {@code assets/miaomenu/models/menu/<id>.json}，
 * 也就是模型 ID {@code miaomenu:menu/<id>}。早期实现漏了 {@code menu/}
 * 这一层，导致 item_model 指向不存在的模型，贴图全部退化成纸张。</p>
 */
class ThemeImageModelIdTest {
    @Test
    void shortFormGetsMenuSegment() {
        assertEquals("miaomenu:menu/spring_sakura",
                ItemResolver.themeImageModelId("spring_sakura"));
    }

    @Test
    void namespacedFormAlsoGetsMenuSegment() {
        assertEquals("miaomenu:menu/winter_snow",
                ItemResolver.themeImageModelId("miaomenu:winter_snow"));
    }

    @Test
    void explicitPathIsKeptAsIs() {
        assertEquals("miaomenu:menu/summer_beach",
                ItemResolver.themeImageModelId("miaomenu:menu/summer_beach"));
    }

    @Test
    void customNamespaceKeepsItsOwnNamespace() {
        assertEquals("myserver:menu/logo",
                ItemResolver.themeImageModelId("myserver:logo"));
    }

    @Test
    void blankInputIsRejected() {
        assertNull(ItemResolver.themeImageModelId(null));
        assertNull(ItemResolver.themeImageModelId(""));
        assertNull(ItemResolver.themeImageModelId("   "));
        assertNull(ItemResolver.themeImageModelId("miaomenu:"));
    }
}
