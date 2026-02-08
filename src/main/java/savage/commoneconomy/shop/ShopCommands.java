package savage.commoneconomy.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import savage.commoneconomy.EconomyManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopCommands {
    private static final Map<UUID, Boolean> removeMode = new HashMap<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("shop")
                .then(CommandManager.literal("create")
                        .requires(source -> savage.commoneconomy.util.PermissionsHelper.check(source, "savscommoneconomy.shop.create", true))
                        .then(CommandManager.literal("sell")
                                .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> createShop(ctx, false))))
                        .then(CommandManager.literal("buy")
                                .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> createShop(ctx, true)))))
                .then(CommandManager.literal("remove")
                        .requires(source -> savage.commoneconomy.util.PermissionsHelper.check(source, "savscommoneconomy.shop.remove", true))
                        .executes(ShopCommands::enterRemoveMode))
                .then(CommandManager.literal("info")
                        .requires(source -> savage.commoneconomy.util.PermissionsHelper.check(source, "savscommoneconomy.shop.info", true))
                        .executes(ShopCommands::shopInfo))
                .then(CommandManager.literal("setprice")
                        .requires(source -> savage.commoneconomy.util.PermissionsHelper.check(source, "savscommoneconomy.shop.create", true))
                        .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0))
                                .executes(ShopCommands::setPrice)))
                .then(CommandManager.literal("admin")
                        .requires(source -> savage.commoneconomy.util.PermissionsHelper.check(source, "savscommoneconomy.admin", 2))
                        .executes(ShopCommands::makeAdmin))
                .then(CommandManager.literal("list")
                        .requires(source -> savage.commoneconomy.util.PermissionsHelper.check(source, "savscommoneconomy.shop.list", true))
                        .executes(ShopCommands::listShops)));
    }

    private static int createShop(CommandContext<ServerCommandSource> context, boolean buying) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        double priceDouble = DoubleArgumentType.getDouble(context, "price");
        BigDecimal price = BigDecimal.valueOf(priceDouble);

        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) {
            context.getSource().sendError(Text.literal("您手里必须拿着物品才能创建商店!"));
            return 0;
        }

        HitResult hitResult = player.raycast(5.0, 0.0f, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendError(Text.literal("创建商店时您必须看向箱子!"));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        
        if (!(context.getSource().getWorld().getBlockState(pos).getBlock() instanceof net.minecraft.block.ChestBlock)) {
            context.getSource().sendError(Text.literal("创建商店时您必须看向箱子!"));
            return 0;
        }

        if (ShopManager.getInstance().isShopChest(pos)) {
            context.getSource().sendError(Text.literal("此位置已经存在一个商店!"));
            return 0;
        }

        String worldId = context.getSource().getWorld().getRegistryKey().getValue().toString();
        
        Shop shop = ShopManager.getInstance().createShop(
                pos,
                worldId,
                player.getUuid(),
                player.getName().getString(),
                heldItem.copy(),
                price,
                buying,
                ShopType.PLAYER
        );

        if (!ShopSignHelper.placeSign(context.getSource().getWorld(), pos, shop, player.getHorizontalFacing())) {
            context.getSource().sendError(Text.literal("警告: 无法放置告示牌! 商店已创建但是没有告示牌"));
        }

        String shopType = buying ? "收购" : "出售";
        context.getSource().sendFeedback(() -> Text.literal(
                "已为" + shopType + " " + heldItem.getName().getString() + 
                "创建商店,售价为" + EconomyManager.getInstance().format(price) + " "), false);

        return 1;
    }

    private static int enterRemoveMode(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        removeMode.put(player.getUuid(), true);
        context.getSource().sendFeedback(() -> Text.literal(
                "点击商店告示牌来移除,再次输入/shop remove 来取消操作"), false);
        return 1;
    }

    public static boolean isInRemoveMode(UUID playerId) {
        return removeMode.getOrDefault(playerId, false);
    }

    public static void exitRemoveMode(UUID playerId) {
        removeMode.remove(playerId);
    }

    private static int shopInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        HitResult hitResult = player.raycast(5.0, 0.0f, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendError(Text.literal("您必须看向商店箱子!"));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        Shop shop = ShopManager.getInstance().getShop(pos);

        if (shop == null) {
            context.getSource().sendError(Text.literal("此位置未找到商店!"));
            return 0;
        }

        String shopType = shop.isBuying() ? "Buying" : "Selling";
        String adminStatus = shop.isAdmin() ? " (Admin Shop)" : "";
        
        context.getSource().sendFeedback(() -> Text.literal("=== 商店信息 ==="), false);
        context.getSource().sendFeedback(() -> Text.literal("商店主人: " + shop.getOwnerName()), false);
        context.getSource().sendFeedback(() -> Text.literal("商店类型: " + shopType + adminStatus), false);
        context.getSource().sendFeedback(() -> Text.literal("商品: " + shop.getItem().getName().getString()), false);
        context.getSource().sendFeedback(() -> Text.literal("价格: " + EconomyManager.getInstance().format(shop.getPrice()) + " "), false);
        
        if (!shop.isAdmin()) {
            context.getSource().sendFeedback(() -> Text.literal("库存: " + shop.getStock()), false);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("库存: 无限"), false);
        }

        return 1;
    }

    private static int setPrice(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        double priceDouble = DoubleArgumentType.getDouble(context, "price");
        BigDecimal price = BigDecimal.valueOf(priceDouble);

        HitResult hitResult = player.raycast(5.0, 0.0f, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendError(Text.literal("您必须看向商店箱子!"));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        Shop shop = ShopManager.getInstance().getShop(pos);

        if (shop == null) {
            context.getSource().sendError(Text.literal("此位置未找到商店!"));
            return 0;
        }

        if (!shop.getOwnerId().equals(player.getUuid()) && !savage.commoneconomy.util.PermissionsHelper.check(context.getSource(), "savscommoneconomy.admin", 2)) {
            context.getSource().sendError(Text.literal("您不是这个商店的主人!"));
            return 0;
        }

        shop.setPrice(price);
        ShopManager.getInstance().save();

        context.getSource().sendFeedback(() -> Text.literal(
                "商品价格已更新 " + EconomyManager.getInstance().format(price) + " each."), false);

        return 1;
    }

    private static int makeAdmin(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        HitResult hitResult = player.raycast(5.0, 0.0f, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendError(Text.literal("您必须看向商店箱子!"));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        Shop shop = ShopManager.getInstance().getShop(pos);

        if (shop == null) {
            context.getSource().sendError(Text.literal("此位置未找到商店!"));
            return 0;
        }

        shop.setType(ShopType.ADMIN);
        ShopManager.getInstance().save();

        context.getSource().sendFeedback(() -> Text.literal(
                "Shop converted to admin shop (infinite stock)."), true);

        return 1;
    }

    private static int listShops(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        var shops = ShopManager.getInstance().getPlayerShops(player.getUuid());
        
        if (shops.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("您不是这个商店的主人"), false);
            return 1;
        }

        context.getSource().sendFeedback(() -> Text.literal("=== 您的商店 ==="), false);
        for (Shop shop : shops) {
            BlockPos pos = shop.getChestLocation();
            String shopType = shop.isBuying() ? "收购" : "出售";
            String location = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            
            context.getSource().sendFeedback(() -> Text.literal(
                    shopType + " " + shop.getItem().getName().getString() + 
                    " at " + location), false);
        }

        return 1;
    }
}
