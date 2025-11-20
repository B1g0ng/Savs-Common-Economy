package savage.commoneconomy.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

public class ShopManager {
    private static ShopManager instance;
    private final Map<BlockPos, Shop> shops = new HashMap<>();
    private final Map<UUID, Set<BlockPos>> playerShops = new HashMap<>();
    private final File shopsFile;
    private final Gson gson;

    private ShopManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("savs-common-economy");
        configDir.toFile().mkdirs();
        this.shopsFile = configDir.resolve("shops.json").toFile();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public static ShopManager getInstance() {
        if (instance == null) {
            instance = new ShopManager();
        }
        return instance;
    }

    public Shop createShop(BlockPos pos, String worldId, UUID ownerId, String ownerName, ItemStack item, 
                          BigDecimal price, boolean buying, ShopType type) {
        UUID shopId = UUID.randomUUID();
        int initialStock = 0;
        
        Shop shop = new Shop(shopId, worldId, pos, ownerId, ownerName, type, item, price, buying, initialStock);
        shops.put(pos, shop);
        
        playerShops.computeIfAbsent(ownerId, k -> new HashSet<>()).add(pos);
        
        save();
        return shop;
    }

    public Shop getShop(BlockPos pos) {
        return shops.get(pos);
    }

    public void removeShop(BlockPos pos) {
        Shop shop = shops.remove(pos);
        if (shop != null) {
            Set<BlockPos> ownerShops = playerShops.get(shop.getOwnerId());
            if (ownerShops != null) {
                ownerShops.remove(pos);
                if (ownerShops.isEmpty()) {
                    playerShops.remove(shop.getOwnerId());
                }
            }
            save();
        }
    }

    public Set<Shop> getPlayerShops(UUID playerId) {
        Set<BlockPos> positions = playerShops.get(playerId);
        if (positions == null) return Collections.emptySet();
        
        Set<Shop> result = new HashSet<>();
        for (BlockPos pos : positions) {
            Shop shop = shops.get(pos);
            if (shop != null) {
                result.add(shop);
            }
        }
        return result;
    }

    public java.util.Collection<Shop> getAllShops() {
        return shops.values();
    }

    public boolean isShopChest(BlockPos pos) {
        return shops.containsKey(pos);
    }

    public void save() {
        try (FileWriter writer = new FileWriter(shopsFile)) {
            List<ShopData> shopDataList = new ArrayList<>();
            for (Shop shop : shops.values()) {
                shopDataList.add(new ShopData(shop));
            }
            gson.toJson(new ShopsContainer(shopDataList), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (!shopsFile.exists()) return;
        
        try (FileReader reader = new FileReader(shopsFile)) {
            Type type = new TypeToken<ShopsContainer>() {}.getType();
            ShopsContainer container = gson.fromJson(reader, type);
            
            if (container != null && container.shops != null) {
                for (ShopData data : container.shops) {
                    Shop shop = data.toShop();
                    shops.put(shop.getChestLocation(), shop);
                    playerShops.computeIfAbsent(shop.getOwnerId(), k -> new HashSet<>())
                              .add(shop.getChestLocation());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ShopsContainer {
        List<ShopData> shops;
        
        ShopsContainer(List<ShopData> shops) {
            this.shops = shops;
        }
    }

    private static class ShopData {
        String shopId;
        String worldId;
        BlockPosData chestLocation;
        String ownerId;
        String ownerName;
        String type;
        String itemId;
        int itemCount;
        String itemNbt;
        double price;
        boolean buying;
        int stock;

        ShopData(Shop shop) {
            this.shopId = shop.getShopId().toString();
            this.worldId = shop.getWorldId();
            // Default to overworld if null (migration support)
            if (this.worldId == null) this.worldId = "minecraft:overworld";
            
            this.chestLocation = new BlockPosData(shop.getChestLocation());
            this.ownerId = shop.getOwnerId().toString();
            this.ownerName = shop.getOwnerName();
            this.type = shop.getType().name();
            
            ItemStack item = shop.getItem();
            this.itemId = net.minecraft.registry.Registries.ITEM.getId(item.getItem()).toString();
            this.itemCount = item.getCount();
            this.itemNbt = "{}";
            
            this.price = shop.getPrice().doubleValue();
            this.buying = shop.isBuying();
            this.stock = shop.getStock();
        }

        Shop toShop() {
            UUID shopId = UUID.fromString(this.shopId);
            BlockPos pos = chestLocation.toBlockPos();
            UUID ownerId = UUID.fromString(this.ownerId);
            ShopType shopType = ShopType.valueOf(this.type);
            
            // Default to overworld if missing (migration support)
            String wId = this.worldId != null ? this.worldId : "minecraft:overworld";
            
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(
                net.minecraft.util.Identifier.of(this.itemId));
            ItemStack itemStack = new ItemStack(item, this.itemCount);
            
            BigDecimal price = BigDecimal.valueOf(this.price);
            
            return new Shop(shopId, wId, pos, ownerId, this.ownerName, shopType, 
                          itemStack, price, this.buying, this.stock);
        }
    }

    private static class BlockPosData {
        int x, y, z;
        
        BlockPosData(BlockPos pos) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
        }
        
        BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }
}
