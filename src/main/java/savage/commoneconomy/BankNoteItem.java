package savage.commoneconomy;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.math.BigDecimal;
import java.util.List;

public class BankNoteItem extends Item {

    public BankNoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient()) {
            NbtComponent nbtComponent = stack.getComponents().get(DataComponentTypes.CUSTOM_DATA);
            if (nbtComponent != null) {
                NbtCompound nbt = nbtComponent.copyNbt();
                if (nbt.contains("Value")) {
                    double valueDouble = nbt.getDouble("Value").orElse(0.0);
                    BigDecimal value = BigDecimal.valueOf(valueDouble);
                    EconomyManager.getInstance().addBalance(user.getUuid(), value);
                    user.sendMessage(Text.literal("Redeemed bank note for " + EconomyManager.getInstance().format(value)).formatted(Formatting.GREEN), true);
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                }
            }
            user.sendMessage(Text.literal("Invalid Bank Note").formatted(Formatting.RED), true);
        }
        return ActionResult.PASS;
    }

    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtComponent nbtComponent = stack.getComponents().get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent != null) {
            NbtCompound nbt = nbtComponent.copyNbt();
            if (nbt.contains("Value")) {
                double valueDouble = nbt.getDouble("Value").orElse(0.0);
                BigDecimal value = BigDecimal.valueOf(valueDouble);
                tooltip.add(Text.literal("Value: " + EconomyManager.getInstance().format(value)).formatted(Formatting.GOLD));
            } else {
                tooltip.add(Text.literal("Value: Unknown").formatted(Formatting.GRAY));
            }
        } else {
            tooltip.add(Text.literal("Value: Unknown").formatted(Formatting.GRAY));
        }
    }
}
