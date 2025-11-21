package savage.commoneconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import savage.commoneconomy.EconomyManager;
import savage.commoneconomy.EconomyManager.AccountData;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DebugCommands {
    // Fixed UUID for test user to prevent database pollution
    private static final UUID TEST_UUID = UUID.nameUUIDFromBytes("TestUser".getBytes());
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ecodebug")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("verify")
                        .executes(DebugCommands::runVerification))
                .then(CommandManager.literal("cleanup")
                        .executes(DebugCommands::runCleanup)));
    }

    private static int runVerification(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("Starting verification..."), false);

        EconomyManager manager = EconomyManager.getInstance();
        manager.createAccount(TEST_UUID, "TestUser");
        manager.setBalance(TEST_UUID, BigDecimal.ZERO);

        int threadCount = 10;
        int updatesPerThread = 10;
        BigDecimal amountPerUpdate = BigDecimal.TEN;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < updatesPerThread; j++) {
                        if (manager.addBalance(TEST_UUID, amountPerUpdate)) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long duration = System.currentTimeMillis() - startTime;
        BigDecimal finalBalance = manager.getBalance(TEST_UUID);
        BigDecimal expectedBalance = amountPerUpdate.multiply(BigDecimal.valueOf(threadCount * updatesPerThread));

        source.sendFeedback(() -> Text.literal("Verification completed in " + duration + "ms"), false);
        source.sendFeedback(() -> Text.literal("Threads: " + threadCount + ", Updates/Thread: " + updatesPerThread), false);
        source.sendFeedback(() -> Text.literal("Successful Updates: " + successCount.get() + "/" + (threadCount * updatesPerThread)), false);
        source.sendFeedback(() -> Text.literal("Final Balance: " + finalBalance + " (Expected for successes: " + amountPerUpdate.multiply(BigDecimal.valueOf(successCount.get())) + ")"), false);

        BigDecimal expectedForSuccesses = amountPerUpdate.multiply(BigDecimal.valueOf(successCount.get()));
        if (finalBalance.compareTo(expectedForSuccesses) == 0) {
            source.sendFeedback(() -> Text.literal("§aDATA CONSISTENCY CHECK: PASSED"), false);
            source.sendFeedback(() -> Text.literal("§7(Balance matches the number of successful updates)"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§cDATA CONSISTENCY CHECK: FAILED"), false);
            source.sendFeedback(() -> Text.literal("§c(Balance does not match successful updates!)"), false);
        }

        if (successCount.get() == threadCount * updatesPerThread) {
            source.sendFeedback(() -> Text.literal("§aTHROUGHPUT CHECK: PERFECT (100%)"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§eTHROUGHPUT CHECK: " + successCount.get() + "/" + (threadCount * updatesPerThread) + " succeeded"), false);
            source.sendFeedback(() -> Text.literal("§7(Some updates failed due to high contention, which is expected. Data is safe.)"), false);
        }
        
        source.sendFeedback(() -> Text.literal("§7Run '/ecodebug cleanup' to remove the test account."), false);
        
        return 1;
    }
    
    private static int runCleanup(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        EconomyManager manager = EconomyManager.getInstance();
        
        if (manager.hasAccount(TEST_UUID)) {
            manager.deleteAccount(TEST_UUID);
            source.sendFeedback(() -> Text.literal("§aTestUser account deleted successfully!"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§eTestUser account does not exist."), false);
        }
        
        return 1;
    }
}
