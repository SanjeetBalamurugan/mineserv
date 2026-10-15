package com.mineserv.mixin;

import com.mineserv.logging.DiscordLogAppender;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public class CommandsMixin {
    @Inject(method = "performPrefixedCommand", at = @At("HEAD"))
    private void mineserv$onCommandExecuted(CommandSourceStack source, String command, CallbackInfo ci) {
        // Check if the executor has Moderator / OP permissions (or is Server Console)
        if (source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) {
            String normalizedCmd = command.startsWith("/") ? command : "/" + command;
            DiscordLogAppender.recordOpCommand(source.getTextName(), normalizedCmd);
        }
    }
}