package dev.lone.itemsadder.api.FontImages;

import org.bukkit.permissions.Permissible;

public final class FontImageWrapper {
    private static Permissible lastPermissible;

    private FontImageWrapper() {
    }

    public static String replaceFontImages(String text) {
        return text.replace(":logo:", "IA");
    }

    public static String replaceFontImages(Permissible permissible, String text) {
        lastPermissible = permissible;
        return text.replace(":logo:", "IA_PLAYER");
    }

    public static Permissible lastPermissible() {
        return lastPermissible;
    }

    public static void reset() {
        lastPermissible = null;
    }
}
