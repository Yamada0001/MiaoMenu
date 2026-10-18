package com.fluxcraft.MiaoMenu.commands.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.commands.PluginCommand;
import com.fluxcraft.MiaoMenu.theme.ThemeManifest;
import com.fluxcraft.MiaoMenu.theme.ThemeRepository;
import com.fluxcraft.MiaoMenu.utils.Lang;

/**
 * {@code /<main> download [主题ID] [--force]}：下载并安装主题菜单。
 *
 * <ul>
 *   <li>不带参数：列出远端可用主题（并刷新清单）；</li>
 *   <li>带主题 ID：下载并安装，随后自动热重载菜单；</li>
 *   <li>{@code --force}：覆盖已存在的同名菜单与资源文件。</li>
 * </ul>
 */
public class DownloadCommand implements PluginCommand {
    private static final String PERMISSION = ThemeRepository.PERMISSION;
    private static final String FORCE_FLAG = "--force";

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
            } else if (themeId == null) {
                themeId = arg;
            }
        }

        if (themeId == null) {
            listThemes(sender, repository);
            return;
        }
        if (!themeId.matches("[A-Za-z0-9_-]{1,64}")) {
            sender.sendMessage(unknownTheme(themeId));
            return;
        }
        repository.installAsync(sender, themeId, force);
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
                sender.sendMessage(Lang.get("theme.list-failed"));
                return;
            }
            sendThemeList(sender, manifest);
        });
    }

    private String unknownTheme(String themeId) {
        return Lang.get("theme.unknown")
                .replace("{0}", themeId)
                .replace("{1}", Lang.get("command.main-alias"));
    }

    private void sendThemeList(CommandSender sender, ThemeManifest manifest) {        if (manifest.themes().isEmpty()) {
            sender.sendMessage(Lang.get("theme.empty"));
            return;
        }
        sender.sendMessage(Lang.get("theme.list-header")
                .replace("{0}", String.valueOf(manifest.themes().size())));
        for (ThemeManifest.Theme theme : manifest.themes()) {
            sender.sendMessage(Lang.get("theme.list-entry")
                    .replace("{0}", theme.id())
                    .replace("{1}", theme.displayName())
                    .replace("{2}", theme.version() == null ? "-" : theme.version())
                    .replace("{3}", theme.description() == null ? "" : theme.description()));
        }
        sender.sendMessage(Lang.get("theme.list-usage").replace("{0}", Lang.get("command.main-alias")));
    }

    /**
     * 补全主题 ID 与 {@code --force}；清单未缓存时按需触发一次后台加载（带退避）。
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
