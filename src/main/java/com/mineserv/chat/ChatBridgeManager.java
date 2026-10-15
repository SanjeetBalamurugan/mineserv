package com.mineserv.chat;

import com.mineserv.config.MineServConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;

public class ChatBridgeManager {
    private static volatile JDA jdaInstance;
    private static volatile MinecraftServer serverInstance;

    public static void init(MinecraftServer server, JDA jda) {
        serverInstance = server;
        jdaInstance = jda;

        // Minecraft -> Discord: Player Chat
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (!MineServConfig.isChatBridgeActive() || jdaInstance == null) return;
            TextChannel chatChannel = jdaInstance.getTextChannelById(MineServConfig.chatChannelId);
            if (chatChannel == null) return;

            String playerName = sender.getName().getString();
            String content = sanitizeForDiscord(message.decoratedContent().getString());

            MessageCreateBuilder msgBuilder = new MessageCreateBuilder()
                    .setContent("**<" + playerName + ">** " + content)
                    .setAllowedMentions(Collections.emptyList());

            chatChannel.sendMessage(msgBuilder.build()).queue();
        });

        // Minecraft -> Discord: Player Join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, mcServer) -> {
            sendBridgeNotice("[+] **" + handler.player.getName().getString() + "** joined the server.");
        });

        // Minecraft -> Discord: Player Leave
        ServerPlayConnectionEvents.DISCONNECT.register((handler, mcServer) -> {
            sendBridgeNotice("[-] **" + handler.player.getName().getString() + "** left the server.");
        });
    }

    /**
     * Discord -> Minecraft: Called from MineServ#onMessageReceived
     */
    public static void onDiscordMessage(MessageReceivedEvent event) {
        if (!MineServConfig.isChatBridgeActive() || serverInstance == null) return;
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;
        if (!event.getChannel().getId().equals(MineServConfig.chatChannelId)) return;

        String author = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getAuthor().getEffectiveName();
        String text = event.getMessage().getContentDisplay().trim();

        if (!event.getMessage().getAttachments().isEmpty()) {
            String attachmentLinks = event.getMessage().getAttachments().stream()
                    .map(Message.Attachment::getFileName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("file");
            text = text.isEmpty() ? "[Attachment: " + attachmentLinks + "]" : text + " [Attachment: " + attachmentLinks + "]";
        }

        if (text.isEmpty()) return;
        final String finalMessage = text;

        serverInstance.execute(() -> {
            Component formatted = Component.empty()
                    .append(Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.literal("<" + author + "> ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(finalMessage).withStyle(ChatFormatting.WHITE));

            serverInstance.getPlayerList().broadcastSystemMessage(formatted, false);
        });
    }

    private static void sendBridgeNotice(String notice) {
        if (!MineServConfig.isChatBridgeActive() || jdaInstance == null) return;
        TextChannel chatChannel = jdaInstance.getTextChannelById(MineServConfig.chatChannelId);
        if (chatChannel != null) {
            chatChannel.sendMessage(notice).queue();
        }
    }

    private static String sanitizeForDiscord(String input) {
        return input.replace("@everyone", "@\u200Beveryone")
                    .replace("@here", "@\u200Bhere");
    }
}