package com.mineserv.command;

import com.mineserv.MineServ;
import com.mineserv.linking.LinkManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class MinecraftCommands {

    public static void register(MineServ mod) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /link <discord_id>
            dispatcher.register(Commands.literal("link")
                    .then(Commands.argument("discord_id", StringArgumentType.word())
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                String discordId = StringArgumentType.getString(context, "discord_id");

                                player.sendSystemMessage(Component.literal("[MineServ] Generating 6-digit verification code for Discord ID " + discordId + "...")
                                        .withStyle(ChatFormatting.GRAY));
                                LinkManager.initiateLink(player, discordId, mod.getJda());
                                return 1;
                            })
                    )
            );

            // /unlink
            dispatcher.register(Commands.literal("unlink")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        boolean removed = LinkManager.unlinkAccount(player.getUUID());
                        if (removed) {
                            player.sendSystemMessage(Component.literal("[MineServ] Your Discord account has been unlinked.")
                                    .withStyle(ChatFormatting.YELLOW));
                        } else {
                            player.sendSystemMessage(Component.literal("[MineServ] Your account is not currently linked.")
                                    .withStyle(ChatFormatting.RED));
                        }
                        return 1;
                    })
            );
        });
    }
}