package com.fluxcraft.MiaoMenu.utils;

import java.util.logging.Logger;

import org.bukkit.entity.Player;

import com.fluxcraft.MiaoMenu.menu.requirement.RequirementFeedbackHandler;
import com.fluxcraft.MiaoMenu.menu.requirement.RequirementResult;

public final class MenuUtils {
    private MenuUtils() {
    }

    public static boolean handleMenuNotFound(Player player, Object menu, String menuName) {
        if (menu != null) {
            return false;
        }
        player.sendMessage(Lang.get("message.menu-not-found")
                .replace("{0}", menuName == null ? "?" : menuName));
        return true;
    }

    /** 与上一条相同，但额外把查找失败写入日志，便于管理员定位写错的菜单名。 */
    public static boolean handleMenuNotFound(Logger logger, Player player, Object menu, String menuName) {
        if (menu != null) {
            return false;
        }
        if (logger != null) {
            logger.warning("Menu '" + menuName + "' was requested by " + player.getName() + " but is not loaded.");
        }
        player.sendMessage(Lang.get("message.menu-not-found")
                .replace("{0}", menuName == null ? "?" : menuName));
        return true;
    }

    public static boolean handleRequirementDenied(
            RequirementFeedbackHandler handler,
            Player player,
            RequirementResult result
    ) {
        if (result == null || result.allowed()) {
            return false;
        }
        if (handler != null) {
            handler.handle(player, result);
        }
        return true;
    }
}
