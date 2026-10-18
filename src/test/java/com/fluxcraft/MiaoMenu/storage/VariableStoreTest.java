package com.fluxcraft.MiaoMenu.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fluxcraft.MiaoMenu.MiaoMenu;

class VariableStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndResolvesGlobalAndPlayerVariables() {
        UUID playerId = UUID.randomUUID();
        MiaoMenu plugin = plugin();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        VariableStore store = store(plugin, new ManualScheduler());
        store.setGlobal("season", "summer");
        store.setPlayer(playerId, "visits", "2");
        assertEquals("3", store.addPlayer(playerId, "visits", 1));
        assertTrue(store.flush());

        VariableStore reloaded = store(plugin, new ManualScheduler());
        reloaded.load();

        assertEquals("summer", reloaded.getGlobal("season").orElseThrow());
        assertEquals("3", reloaded.getPlayer(playerId, "visits").orElseThrow());
        assertEquals(
                "summer/3",
                reloaded.resolve(player, "%miao_global_season%/{player:visits}")
        );
    }

    @Test
    void rejectsInvalidKeysAndNonNumericAddition() {
        MiaoMenu plugin = plugin();
        VariableStore store = store(plugin, new ManualScheduler());

        assertThrows(IllegalArgumentException.class, () -> store.setGlobal("bad key", "value"));
        store.setGlobal("text", "hello");
        assertThrows(IllegalArgumentException.class, () -> store.addGlobal("text", 1));
    }

    @Test
    void mergesBurstOfChangesIntoOneScheduledSave() {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        VariableStore store = store(plugin, scheduler);

        for (int index = 0; index < 25; index++) {
            store.setGlobal("key" + index, "value" + index);
        }

        assertEquals(1, scheduler.tasks.size(), "同一窗口内的多次变更应只排一次落盘");
        assertTrue(store.hasPendingChanges());
        assertFalse(Files.exists(storageFile()), "未到合并窗口前不应写盘");

        scheduler.runNext();

        assertFalse(store.hasPendingChanges());
        assertTrue(Files.exists(storageFile()));
    }

    @Test
    void changesMadeDuringSaveAreNotLost() {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        VariableStore store = store(plugin, scheduler);

        store.setGlobal("first", "1");
        // 模拟"落盘期间又发生变更"：先排一个任务，再在任务执行前追加变更。
        store.setGlobal("second", "2");
        scheduler.runNext();

        assertFalse(store.hasPendingChanges(), "两次变更应被同一个快照覆盖");
        VariableStore reloaded = store(plugin, new ManualScheduler());
        reloaded.load();
        assertEquals("1", reloaded.getGlobal("first").orElseThrow());
        assertEquals("2", reloaded.getGlobal("second").orElseThrow());
    }

    @Test
    void writeFailureKeepsDirtyStateAndSchedulesRetry() throws Exception {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        VariableStore store = store(plugin, scheduler);

        // 用同名目录占位，让原子替换必然失败。
        Files.createDirectory(storageFile());
        store.setGlobal("season", "winter");

        assertFalse(store.flush(), "写入失败应返回 false");
        assertTrue(store.hasPendingChanges(), "失败后必须保留脏状态");

        scheduler.runNext();
        assertTrue(store.hasPendingChanges());
        assertEquals(1, scheduler.tasks.size(), "失败后应重新排队重试");

        Files.delete(storageFile());
        scheduler.runNext();

        assertFalse(store.hasPendingChanges(), "重试成功后应清掉脏状态");
        assertTrue(Files.isRegularFile(storageFile()));
    }

    @Test
    void shutdownWritesPendingChangesSynchronously() {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        VariableStore store = store(plugin, scheduler);

        store.setGlobal("season", "autumn");
        store.shutdown();

        assertFalse(scheduler.tasks.isEmpty() == false, "关闭时应丢弃未执行的延时任务");
        assertTrue(Files.exists(storageFile()), "关闭时必须完成最终保存");

        VariableStore reloaded = store(plugin, new ManualScheduler());
        reloaded.load();
        assertEquals("autumn", reloaded.getGlobal("season").orElseThrow());
    }

    @Test
    void concurrentMutationsAreAllPersisted() throws Exception {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        VariableStore store = store(plugin, scheduler);

        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
            int id = threadIndex;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int index = 0; index < perThread; index++) {
                        store.setGlobal("t" + id + "_" + index, "v" + index);
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发写入应在超时前完成");
        assertTrue(failures.isEmpty(), () -> "并发写入失败：" + failures);

        store.shutdown();

        VariableStore reloaded = store(plugin, new ManualScheduler());
        reloaded.load();
        for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
            for (int index = 0; index < perThread; index++) {
                String key = "t" + threadIndex + "_" + index;
                assertEquals("v" + index, reloaded.getGlobal(key).orElse(null), "丢失变量：" + key);
            }
        }
    }

    @Test
    void evictsIdlePlayerFromMemoryButReloadsFromDisk() {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        UUID playerId = UUID.randomUUID();
        // TTL = 0 表示不按时间淘汰；这里用容量上限触发淘汰。
        VariableStore store = store(plugin, scheduler, 1, 0L);

        store.setPlayer(playerId, "visits", "7");
        assertTrue(store.flush(), "先落盘，条目才允许被淘汰");
        store.setPlayer(UUID.randomUUID(), "visits", "1");

        // 触发一次清理（保存时会执行淘汰）
        store.flush();

        // 被淘汰的玩家仍能从磁盘读回自己的数据
        assertEquals("7", store.getPlayer(playerId, "visits").orElseThrow());
    }

    @Test
    void neverEvictsPlayerWithUnsavedChanges() {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        VariableStore store = store(plugin, scheduler, 1, 0L);

        // 两个条目都是脏的，容量上限为 1 也不能丢任何一个
        store.setPlayer(first, "visits", "1");
        store.setPlayer(second, "visits", "2");
        store.flush();

        assertEquals("1", store.getPlayer(first, "visits").orElseThrow());
        assertEquals("2", store.getPlayer(second, "visits").orElseThrow());
    }

    @Test
    void ttlEvictionKeepsDataOnDisk() {
        MiaoMenu plugin = plugin();
        ManualScheduler scheduler = new ManualScheduler();
        UUID playerId = UUID.randomUUID();
        // TTL = 1ms，配合等待确保条目过期
        VariableStore store = store(plugin, scheduler, 0, 1L);

        store.setPlayer(playerId, "visits", "9");
        assertTrue(store.flush());

        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        store.flush();

        assertEquals("9", store.getPlayer(playerId, "visits").orElseThrow());
    }

    private Path storageFile() {
        return temporaryDirectory.resolve("storage.yml");
    }

    private MiaoMenu plugin() {
        MiaoMenu plugin = mock(MiaoMenu.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
        return plugin;
    }

    private VariableStore store(MiaoMenu plugin, ManualScheduler scheduler) {
        return store(plugin, scheduler, 0, 0L);
    }

    private VariableStore store(MiaoMenu plugin, ManualScheduler scheduler,
                                int maxPlayers, long ttlMillis) {
        return new VariableStore(plugin, scheduler, 0L, maxPlayers, ttlMillis);
    }

    /** 手工驱动的调度器：测试自己决定何时执行落盘。 */
    static final class ManualScheduler implements VariableStore.SaveScheduler {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void schedule(Runnable task, long delayMillis) {
            tasks.add(task);
        }

        @Override
        public void shutdown() {
            tasks.clear();
        }

        void runNext() {
            assertFalse(tasks.isEmpty(), "没有待执行的落盘任务");
            tasks.remove(0).run();
        }
    }
}
