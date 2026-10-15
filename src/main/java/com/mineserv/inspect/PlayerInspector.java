package com.mineserv.inspect;

import com.mineserv.linking.LinkManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PlayerInspector {

    public static MessageEmbed buildInspectionEmbed(ServerPlayer player, String view) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Player Inspection: " + player.getName().getString() + " (" + view.toUpperCase() + ")")
                .setColor(Color.CYAN)
                .setTimestamp(Instant.now());

        if (view.equals("all") || view.equals("vitals")) {
            appendVitals(embed, player);
        }

        if (view.equals("all") || view.equals("inventory")) {
            appendEquipmentAndInventory(embed, player);
        }

        if (view.equals("all") || view.equals("enderchest")) {
            appendEnderChest(embed, player);
        }

        return embed.build();
    }

    private static void appendVitals(EmbedBuilder embed, ServerPlayer player) {
        boolean bedrock = LinkManager.isBedrockPlayer(player);
        String linkedId = LinkManager.getLinkedDiscordId(player.getUUID());
        String discordInfo = linkedId != null ? "<@" + linkedId + "> (`" + linkedId + "`)" : "`Not Linked`";

        String dimension = player.level().dimension().toString();
        String coords = String.format("X: %d, Y: %d, Z: %d",
                player.getBlockX(), player.getBlockY(), player.getBlockZ());

        String healthAndFood = String.format("%.1f / %.1f HP | Food: %d/20",
                player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel());

        String xpAndMode = String.format("Level %d | Mode: %s",
                player.experienceLevel, player.gameMode.getGameModeForPlayer().getName().toUpperCase());

        embed.addField("UUID", "`" + player.getUUID() + "`", false);
        embed.addField("Platform", bedrock ? "`Bedrock Edition`" : "`Java Edition`", true);
        embed.addField("Discord", discordInfo, true);
        embed.addField("Vitals", "`" + healthAndFood + "`", true);
        embed.addField("Experience & Mode", "`" + xpAndMode + "`", true);
        embed.addField("Location (" + dimension + ")", "`" + coords + "`", false);
    }

    private static void appendEquipmentAndInventory(EmbedBuilder embed, ServerPlayer player) {
        StringBuilder equipText = new StringBuilder();
        equipText.append("Head:    ").append(formatStack(player.getItemBySlot(EquipmentSlot.HEAD))).append("\n");
        equipText.append("Chest:   ").append(formatStack(player.getItemBySlot(EquipmentSlot.CHEST))).append("\n");
        equipText.append("Legs:    ").append(formatStack(player.getItemBySlot(EquipmentSlot.LEGS))).append("\n");
        equipText.append("Feet:    ").append(formatStack(player.getItemBySlot(EquipmentSlot.FEET))).append("\n");
        equipText.append("MainHand:").append(formatStack(player.getMainHandItem())).append("\n");
        equipText.append("OffHand: ").append(formatStack(player.getOffhandItem()));

        embed.addField("Equipped Armor & Hands", "```text\n" + equipText + "\n```", false);

        Inventory inv = player.getInventory();
        List<String> mainItems = new ArrayList<>();
        // Slots 0-8 are hotbar, 9-35 are main inventory
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                String slotLabel = (i < 9) ? String.format("H%d", i + 1) : String.format("%02d", i);
                mainItems.add("[" + slotLabel + "] " + formatStack(stack));
            }
        }

        String invBody = mainItems.isEmpty() ? "Empty" : truncateLines(mainItems, 950);
        embed.addField("Main Inventory (" + mainItems.size() + "/36 slots used)", "```text\n" + invBody + "\n```",
                false);
    }

    private static void appendEnderChest(EmbedBuilder embed, ServerPlayer player) {
        Container enderChest = player.getEnderChestInventory();
        List<String> ecItems = new ArrayList<>();

        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            ItemStack stack = enderChest.getItem(i);
            if (!stack.isEmpty()) {
                ecItems.add(String.format("[%02d] %s", i + 1, formatStack(stack)));
            }
        }

        String ecBody = ecItems.isEmpty() ? "Empty" : truncateLines(ecItems, 950);
        embed.addField("Ender Chest (" + ecItems.size() + "/" + enderChest.getContainerSize() + " slots used)",
                "```text\n" + ecBody + "\n```", false);
    }

    private static String formatStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "None";
        }
        String name = stack.getHoverName().getString().replace("`", "'");
        int count = stack.getCount();

        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" x").append(count);

        if (stack.isDamageableItem() && stack.getDamageValue() > 0) {
            int maxDurability = stack.getMaxDamage();
            int remaining = maxDurability - stack.getDamageValue();
            sb.append(" (Durability: ").append(remaining).append("/").append(maxDurability).append(")");
        }

        ItemEnchantments enchantments = stack.getEnchantments();
        if (!enchantments.isEmpty()) {
            sb.append(" [Enchanted]");
        }

        return sb.toString();
    }

    private static String truncateLines(List<String> lines, int maxLength) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (sb.length() + line.length() + 25 > maxLength) {
                sb.append("... (+").append(lines.size() - i).append(" more items)");
                break;
            }
            if (!sb.isEmpty())
                sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }
}