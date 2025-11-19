package savage.commoneconomy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EconomyCommands {

    private static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGESTION_PROVIDER = (context, builder) -> {
        List<String> suggestions = new ArrayList<>();
        // Add online players
        suggestions.addAll(context.getSource().getPlayerNames());
        // Add offline players from cache
        suggestions.addAll(EconomyManager.getInstance().getOfflinePlayerNames());
        return CommandSource.suggestMatching(suggestions, builder);
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("bal")
                .executes(EconomyCommands::checkSelfBalance)
                .then(CommandManager.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .executes(EconomyCommands::checkOtherBalance)));

        dispatcher.register(CommandManager.literal("balance")
                .executes(EconomyCommands::checkSelfBalance)
                .then(CommandManager.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .executes(EconomyCommands::checkOtherBalance)));

        dispatcher.register(CommandManager.literal("pay")
                .then(CommandManager.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(EconomyCommands::pay))));

        dispatcher.register(CommandManager.literal("givemoney")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(EconomyCommands::giveMoney))));

        dispatcher.register(CommandManager.literal("takemoney")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(EconomyCommands::takeMoney))));

        dispatcher.register(CommandManager.literal("setmoney")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                .executes(EconomyCommands::setMoney))));

        dispatcher.register(CommandManager.literal("resetmoney")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("target", StringArgumentType.string())
                        .suggests(PLAYER_SUGGESTION_PROVIDER)
                        .executes(EconomyCommands::resetMoney)));
    }

    private static UUID getTargetUUID(CommandContext<ServerCommandSource> context, String targetName) throws CommandSyntaxException {
        if (targetName.equals("@s")) {
            return context.getSource().getPlayerOrThrow().getUuid();
        }
        
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);
        if (target != null) {
            return target.getUuid();
        }
        return EconomyManager.getInstance().getUUID(targetName);
    }

    private static String getTargetName(CommandContext<ServerCommandSource> context, String targetName) throws CommandSyntaxException {
        if (targetName.equals("@s")) {
            return context.getSource().getPlayerOrThrow().getName().getString();
        }

        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);
        if (target != null) {
            return target.getName().getString();
        }
        return targetName;
    }

    private static int checkSelfBalance(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BigDecimal balance = EconomyManager.getInstance().getBalance(player.getUuid());
        context.getSource().sendFeedback(() -> Text.literal("Your balance: " + EconomyManager.getInstance().format(balance)), false);
        return 1;
    }

    private static int checkOtherBalance(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target");
        UUID targetUUID = getTargetUUID(context, targetName);
        String displayName = getTargetName(context, targetName);

        if (targetUUID == null) {
            context.getSource().sendError(Text.literal("Player not found or has never joined."));
            return 0;
        }

        BigDecimal balance = EconomyManager.getInstance().getBalance(targetUUID);
        context.getSource().sendFeedback(() -> Text.literal(displayName + "'s balance: " + EconomyManager.getInstance().format(balance)), false);
        return 1;
    }

    private static int pay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity sourcePlayer = context.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(context, "target");
        double amountDouble = DoubleArgumentType.getDouble(context, "amount");
        BigDecimal amount = BigDecimal.valueOf(amountDouble);

        UUID targetUUID = getTargetUUID(context, targetName);
        String displayName = getTargetName(context, targetName);

        if (targetUUID == null) {
            context.getSource().sendError(Text.literal("Player not found or has never joined."));
            return 0;
        }

        if (sourcePlayer.getUuid().equals(targetUUID)) {
            context.getSource().sendError(Text.literal("You cannot pay yourself."));
            return 0;
        }

        if (EconomyManager.getInstance().removeBalance(sourcePlayer.getUuid(), amount)) {
            EconomyManager.getInstance().addBalance(targetUUID, amount);
            String formattedAmount = EconomyManager.getInstance().format(amount);
            context.getSource().sendFeedback(() -> Text.literal("Paid " + formattedAmount + " to " + displayName), false);
            
            ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetUUID);
            if (target != null) {
                target.sendMessage(Text.literal("Received " + formattedAmount + " from " + sourcePlayer.getName().getString()));
            }
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Insufficient funds."));
            return 0;
        }
    }

    private static int giveMoney(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target");
        double amountDouble = DoubleArgumentType.getDouble(context, "amount");
        BigDecimal amount = BigDecimal.valueOf(amountDouble);
        String formattedAmount = EconomyManager.getInstance().format(amount);

        UUID targetUUID = getTargetUUID(context, targetName);
        String displayName = getTargetName(context, targetName);

        if (targetUUID == null) {
            context.getSource().sendError(Text.literal("Player not found or has never joined."));
            return 0;
        }

        EconomyManager.getInstance().addBalance(targetUUID, amount);
        context.getSource().sendFeedback(() -> Text.literal("Gave " + formattedAmount + " to " + displayName), true);
        
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(Text.literal("Received " + formattedAmount + " (Admin Gift)"));
        }
        return 1;
    }

    private static int takeMoney(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target");
        double amountDouble = DoubleArgumentType.getDouble(context, "amount");
        BigDecimal amount = BigDecimal.valueOf(amountDouble);
        String formattedAmount = EconomyManager.getInstance().format(amount);

        UUID targetUUID = getTargetUUID(context, targetName);
        String displayName = getTargetName(context, targetName);

        if (targetUUID == null) {
            context.getSource().sendError(Text.literal("Player not found or has never joined."));
            return 0;
        }

        if (!EconomyManager.getInstance().removeBalance(targetUUID, amount)) {
            EconomyManager.getInstance().setBalance(targetUUID, BigDecimal.ZERO);
            context.getSource().sendFeedback(() -> Text.literal("Took as much as possible from " + displayName + " (now " + EconomyManager.getInstance().format(BigDecimal.ZERO) + ")"), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("Took " + formattedAmount + " from " + displayName), true);
        }
        return 1;
    }

    private static int setMoney(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target");
        double amountDouble = DoubleArgumentType.getDouble(context, "amount");
        BigDecimal amount = BigDecimal.valueOf(amountDouble);
        String formattedAmount = EconomyManager.getInstance().format(amount);

        UUID targetUUID = getTargetUUID(context, targetName);
        String displayName = getTargetName(context, targetName);

        if (targetUUID == null) {
            context.getSource().sendError(Text.literal("Player not found or has never joined."));
            return 0;
        }

        EconomyManager.getInstance().setBalance(targetUUID, amount);
        context.getSource().sendFeedback(() -> Text.literal("Set " + displayName + "'s balance to " + formattedAmount), true);
        
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(Text.literal("Your balance has been set to " + formattedAmount));
        }
        return 1;
    }

    private static int resetMoney(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String targetName = StringArgumentType.getString(context, "target");

        UUID targetUUID = getTargetUUID(context, targetName);
        String displayName = getTargetName(context, targetName);

        if (targetUUID == null) {
            context.getSource().sendError(Text.literal("Player not found or has never joined."));
            return 0;
        }

        EconomyManager.getInstance().resetBalance(targetUUID);
        BigDecimal newBalance = EconomyManager.getInstance().getBalance(targetUUID);
        String formattedAmount = EconomyManager.getInstance().format(newBalance);

        context.getSource().sendFeedback(() -> Text.literal("Reset " + displayName + "'s balance to " + formattedAmount), true);
        
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(Text.literal("Your balance has been reset to " + formattedAmount));
        }
        return 1;
    }
}

