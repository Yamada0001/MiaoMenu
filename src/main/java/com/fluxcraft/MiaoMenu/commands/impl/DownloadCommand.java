package com.fluxcraft.MiaoMenu.commands.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.theme.ThemeManifest;
import com.fluxcraft.MiaoMenu.theme.ThemeRepository;
import com.fluxcraft.MiaoMenu.utils.Lang;
import com.fluxcraft.MiaoMenu.utils.MenuText;

/**
 * {@code /<main> download [主题ID] [--force]}：下载并安装主题菜单。
 *
 * <ul>
 *   <li>不带参数：列出远端可用主题，玩家可直接点击安装；</li>
 *   <li>带主题 ID：下载并安装，随后自动热重载菜单并重发资源包；</li>
 *   <li>{@code --force}：覆盖已存在的同名菜单与资源文件；</li>
 *   <li>{@code cancel}：取消正在进行的下载。</li>
 * </ul>
 */
public class DownloadCommand implements PluginCommand {
    private static final String PERMISSION = ThemeRepository.PERMISSION;
    private static final String FORCE_FLAG = "--force";
    private static final String CANCEL_ARG = "cancel";

    private final MiaoMenu plugin;

    public DownloadCommand(MiaoMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Lang.get("message.no-permission"));
            return;
        }
        ThemeRepository repository = plugin.getThemeRepository();
        if (repository == null) {
            sender.sendMessage(Lang.get("theme.unavailable"));
            return;
        }

        boolean force = false;
        String themeId = null;
        for (String arg : args) {
            if (FORCE_FLAG.equalsIgnoreCase(arg)) {
                force = true;
            } else if (CANCEL_ARG.equalsIgnoreCase(arg)) {
                cancel(sender, repository);
                return;
            } else if (themeId == null) {
                themeId = arg;
            }
        }

        if (themeId == null) {
            listThemes(sender, repository);
            return;
        }
        if (!themeId.matches("[A-Za-z0-9_-]{1,64}")) {
            sender.sendMessage(unknownTheme(repository, themeId));
            return;
        }
        repository.installAsync(sender, themeId, force);
    }

    private void cancel(CommandSender sender, ThemeRepository repository) {
        String running = repository.installingThemeId();
        if (running == null) {
            sender.sendMessage(Lang.get("theme.cancel-none"));
            return;
        }
        if (repository.cancelInstall()) {
            sender.sendMessage(Lang.get("theme.cancel-requested").replace("{0}", running));
            return;
        }
        sender.sendMessage(Lang.get("theme.cancel-none"));
    }

    private void listThemes(CommandSender sender, ThemeRepository repository) {
        ThemeManifest cached = repository.cachedManifest();
        if (cached != null) {
            sendThemeList(sender, cached);
            return;
        }
        sender.sendMessage(Lang.get("theme.listing"));
        repository.loadManifestAsync().thenAccept(manifest -> {
            if (manifest == null) {
                String reason = repository.lastManifestError();
                sender.sendMessage(Lang.get("theme.list-failed")
                        .replace("{0}", reason == null ? "-" : reason));
                return;
            }
            sendThemeList(sender, manifest);
        });
    }

    private String unknownTheme(ThemeRepository repository, String themeId) {
        String message = Lang.get("theme.unknown")
                .replace("{0}", themeId)
                .replace("{1}", Lang.get("command.main-alias"));
        ThemeManifest manifest = repository.cachedManifest();
        if (manifest != null && !manifest.themes().isEmpty()) {
            message += "\n" + Lang.get("theme.unknown-suggest").replace("{0}", suggest(manifest, themeId));
        }
        return message;
    }

    /** 用编辑距离找最接近的主题 ID，减少"手输写错"的挫败感。 */
    private static String suggest(ThemeManifest manifest, String typed) {
        String normalized = typed.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ThemeManifest.Theme theme : manifest.themes()) {
            int distance = distance(normalized, theme.id());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = theme.id();
            }
        }
        return best == null ? typed : best;
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private void sendThemeList(CommandSender sender, ThemeManifest manifest) {
        if (manifest.themes().isEmpty()) {
            sender.sendMessage(Lang.get("theme.empty"));
            return;
        }
        sender.sendMessage(Lang.get("theme.list-header")
                .replace("{0}", String.valueOf(manifest.themes().size())));
        String mainAlias = Lang.get("command.main-alias");
        boolean clickable = sender instanceof Player;
        for (ThemeManifest.Theme theme : manifest.themes()) {
            String line = Lang.get("theme.list-entry")
                    .replace("{0}", theme.id())
                    .replace("{1}", theme.displayName())
                    .replace("{2}", theme.version() == null ? "-" : theme.version())
                    .replace("{3}", theme.description() == null ? "" : theme.description())
                    .replace("{4}", String.valueOf(theme.files().size()));
            if (!clickable) {
                sender.sendMessage(line);
                continue;
            }
            String command = "/" + mainAlias + " download " + theme.id();
            sender.sendMessage(MenuText.clickableCommand(
                    line,
                    command,
                    Lang.get("theme.list-entry-hover").replace("{0}", command)
            ));
        }
        sender.sendMessage(Lang.get("theme.list-usage").replace("{0}", mainAlias));
        if (clickable) {
            sender.sendMessage(Lang.get("theme.list-click-hint"));
        }
    }

    /**
     * 补全主题 ID、{@code --force} 与 {@code cancel}；清单未缓存时按需触发一次后台加载。
     */
    @Override
    public @Nullable List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        ThemeRepository repository = plugin.getThemeRepository();
        if (repository == null) {
            return List.of();
        }
        repository.ensureManifestLoading();
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (String id : repository.cachedThemeIds()) {
                if (id.startsWith(typed)) {
                    suggestions.add(id);
                }
            }
            if (repository.installingThemeId() != null && CANCEL_ARG.startsWith(typed)) {
                suggestions.add(CANCEL_ARG);
            }
            if (FORCE_FLAG.startsWith(typed)) {
                suggestions.add(FORCE_FLAG);
            }
            return suggestions;
        }
        if (args.length == 2 && FORCE_FLAG.startsWith(typed)) {
            return List.of(FORCE_FLAG);
        }
        return List.of();
    }

    @Override
    public boolean hasAccess(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }

    @Override
    public String errorMessageKey() {
        return "theme.error";
    }
}
