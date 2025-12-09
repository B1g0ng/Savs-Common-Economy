package savage.commoneconomy.util;

import com.google.gson.Gson;
import savage.commoneconomy.EconomyManager;
import savage.commoneconomy.SavsCommonEconomy;
import savage.commoneconomy.config.EconomyConfig;
import savage.savdbcore.config.DBCoreConfig;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Economy-specific Redis manager.
 * Wraps savdbcore's RedisManager and adds economy-specific pub/sub functionality.
 */
public class RedisManager {
    private static RedisManager instance;
    private final savage.savdbcore.redis.RedisManager coreRedis;
    private final Gson gson = new Gson();
    private final EconomyConfig.RedisConfig config;
    private net.minecraft.server.MinecraftServer server;

    private RedisManager(EconomyConfig.RedisConfig config) {
        this.config = config;
        
        // Convert economy config to DBCore config
        DBCoreConfig.RedisConfig coreConfig = new DBCoreConfig.RedisConfig();
        coreConfig.enabled = config.enabled;
        coreConfig.host = config.host;
        coreConfig.port = config.port;
        coreConfig.password = config.password;
        coreConfig.channel = config.channel;
        coreConfig.debugLogging = config.debugLogging;
        
        this.coreRedis = new savage.savdbcore.redis.RedisManager(coreConfig);
        this.coreRedis.setMessageHandler(this::handleMessage);
    }
    
    public void setServer(net.minecraft.server.MinecraftServer server) {
        this.server = server;
    }

    public static RedisManager getInstance() {
        if (instance == null) {
            EconomyConfig.RedisConfig config = EconomyManager.getInstance().getConfig().redis;
            instance = new RedisManager(config);
            if (config.enabled) {
                instance.connect();
            }
        }
        return instance;
    }

    private void connect() {
        coreRedis.connect();
    }

    public void publishBalanceUpdate(UUID uuid, BigDecimal newBalance) {
        publishTransaction(uuid, newBalance, null, null, null);
    }
    
    public void publishTransaction(UUID targetUuid, BigDecimal newBalance, String type, String sourcePlayer, String message) {
        if (!coreRedis.isConnected()) return;

        TransactionMessage msg = new TransactionMessage(
            targetUuid.toString(), 
            newBalance, 
            type, 
            sourcePlayer, 
            message
        );
        coreRedis.publish(msg);
        
        if (config.debugLogging) {
            SavsCommonEconomy.LOGGER.info("Redis: Published transaction for " + targetUuid + " -> $" + newBalance);
        }
    }

    private void handleMessage(String json) {
        try {
            TransactionMessage message = gson.fromJson(json, TransactionMessage.class);
            UUID uuid = UUID.fromString(message.uuid);

            // Invalidate local cache so next read fetches fresh data
            EconomyManager.getInstance().invalidateCache(uuid);
            
            // Notify player if they're online
            net.minecraft.server.network.ServerPlayerEntity player = getOnlinePlayer(uuid);
            if (config.debugLogging) {
                SavsCommonEconomy.LOGGER.info("Redis: Looking for player " + uuid + ", found: " + (player != null));
            }
            if (player != null && message.chatMessage != null) {
                if (config.debugLogging) {
                    SavsCommonEconomy.LOGGER.info("Redis: Sending message to player: " + message.chatMessage);
                }
                player.sendMessage(net.minecraft.text.Text.literal(message.chatMessage), false);
            } else if (config.debugLogging) {
                if (player == null) {
                    SavsCommonEconomy.LOGGER.info("Redis: Player not online on this server");
                }
                if (message.chatMessage == null) {
                    SavsCommonEconomy.LOGGER.info("Redis: No chat message in transaction");
                }
            }
            
            if (config.debugLogging) {
                SavsCommonEconomy.LOGGER.info("Redis: Received transaction for " + uuid + " -> $" + message.balance + " (cache invalidated)");
            }

        } catch (Exception e) {
            SavsCommonEconomy.LOGGER.warn("Failed to handle Redis message: " + json, e);
        }
    }
    
    private net.minecraft.server.network.ServerPlayerEntity getOnlinePlayer(UUID uuid) {
        if (server == null) return null;
        return server.getPlayerManager().getPlayer(uuid);
    }

    public void shutdown() {
        coreRedis.shutdown();
    }

    public boolean isConnected() {
        return coreRedis.isConnected();
    }

    private static class TransactionMessage {
        String uuid;
        BigDecimal balance;
        String type; // "pay", "give", "take", etc.
        String sourcePlayer; // Who initiated the transaction
        String chatMessage; // The message to show the player

        TransactionMessage(String uuid, BigDecimal balance, String type, String sourcePlayer, String chatMessage) {
            this.uuid = uuid;
            this.balance = balance;
            this.type = type;
            this.sourcePlayer = sourcePlayer;
            this.chatMessage = chatMessage;
        }
    }
}
