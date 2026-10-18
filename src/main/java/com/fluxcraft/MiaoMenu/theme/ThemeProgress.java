package com.fluxcraft.MiaoMenu.theme;

import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import com.fluxcraft.MiaoMenu.MiaoMenu;
import com.fluxcraft.MiaoMenu.utils.Lang;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/**
 * 主题下载进度展示。
 *
 * <p>用 BossBar 显示百分比与当前文件，避免刷屏；控制台退回纯文本。进度按
 * "已完成文件数" 为主、当前文件字节数为辅：服务端不给 {@code Content-Length}
 * 时仍能显示文件级进度。</p>
 *
 * <p>所有方法都在主线程调用（{@link #updateFile}/{@link #updateBytes} 内部做了
 * 线程安全处理），下载线程只负责上报数字。</p>
 */
final class ThemeProgress {
    private static final int BAR_SEGMENTS = 20;

    private final MiaoMenu plugin;
    private final CommandSender requester;
    private final String themeId;
    private final @Nullable Player player;
    private volatile @Nullable BossBar bossBar;
    private volatile boolean started;
    private volatile String currentFile = "";
    private volatile int currentIndex;
    private volatile int totalFiles;
    private volatile long currentDone;
    private volatile long currentTotal = -1L;
    private volatile int lastPercent = -1;

    ThemeProgress(MiaoMenu plugin, CommandSender requester, String themeId) {
        this.plugin = plugin;
        this.requester = requester;
        this.themeId = themeId;
        this.player = requester instanceof Player p ? p : null;
    }

    /** 开始展示（主线程）。 */
    void start() {
        if (player == null) {
            return;
        }
        BossBar bar = BossBar.bossBar(
                Component.text(Lang.get("theme.progress.starting").replace("{0}", themeId)),
                0.0F,
                BossBar.Color.PINK,
                BossBar.Overlay.PROGRESS
        );
        bossBar = bar;
        player.showBossBar(bar);
        started = true;
    }

    /** 开始下载某个文件（下载线程调用）。 */
    void updateFile(int index, int total, String target, long knownTotalBytes) {
        currentIndex = index;
        totalFiles = total;
        currentFile = target;
        currentDone = 0L;
        currentTotal = knownTotalBytes;
        push();
    }

    /** 当前文件已下载字节（下载线程调用）。 */
    void updateBytes(int index, int total, long done, long totalBytes) {
        currentIndex = index;
        totalFiles = total;
        currentDone = done;
        currentTotal = totalBytes;
        push();
    }

    /** 收尾：移除 BossBar（主线程）。 */
    void finish() {
        BossBar bar = bossBar;
        bossBar = null;
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
    }

    /**
     * 把进度推给主线程。
     *
     * <p>同一百分比不重复推送，避免每 8KB 就跨线程调度一次。</p>
     */
    private void push() {
        if (!started || player == null) {
            return;
        }
        int percent = percent();
        if (percent == lastPercent) {
            return;
        }
        lastPercent = percent;
        int index = currentIndex;
        int total = totalFiles;
        long done = currentDone;
        long size = currentTotal;
        String file = currentFile;
        String theme = themeId;
        Runnable task = () -> {
            BossBar bar = bossBar;
            if (bar == null) {
                return;
            }
            bar.progress(Math.min(1.0F, Math.max(0.0F, percent / 100.0F)));
            bar.name(Component.text(describe(theme, percent, index, total, done, size, file)));
        };
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        com.fluxcraft.MiaoMenu.foliacall.FoliaFactory.getAdapter()
                .runTaskLaterForEntity(plugin, player, task, 0L);
    }

    private int percent() {
        int total = Math.max(1, totalFiles);
        double fileProgress = 0.0D;
        if (currentTotal > 0L) {
            fileProgress = Math.min(1.0D, (double) currentDone / (double) currentTotal);
        }
        double overall = ((double) currentIndex + fileProgress) / (double) total;
        return (int) Math.round(Math.min(1.0D, Math.max(0.0D, overall)) * 100.0D);
    }

    private String describe(
            String theme,
            int percent,
            int index,
            int total,
            long done,
            long size,
            String file
    ) {
        String template = size > 0L
                ? Lang.get("theme.progress.line")
                : Lang.get("theme.progress.line-unknown-size");
        return template
                .replace("{0}", theme)
                .replace("{1}", bar(percent))
                .replace("{2}", String.valueOf(percent))
                .replace("{3}", String.valueOf(Math.min(index + 1, total)))
                .replace("{4}", String.valueOf(total))
                .replace("{5}", humanSize(done))
                .replace("{6}", size > 0L ? humanSize(size) : "?")
                .replace("{7}", file == null ? "" : file);
    }

    private static String bar(int percent) {
        int filled = Math.round(BAR_SEGMENTS * (percent / 100.0F));
        StringBuilder builder = new StringBuilder(BAR_SEGMENTS);
        for (int index = 0; index < BAR_SEGMENTS; index++) {
            builder.append(index < filled ? '█' : '░');
        }
        return builder.toString();
    }

    static String humanSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + "B";
        }
        double value = bytes / 1024.0D;
        if (value < 1024.0D) {
            return String.format(Locale.ROOT, "%.1fKB", value);
        }
        return String.format(Locale.ROOT, "%.1fMB", value / 1024.0D);
    }
}
