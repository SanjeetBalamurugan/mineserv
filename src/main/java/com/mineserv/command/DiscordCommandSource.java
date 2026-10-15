package com.mineserv.command;

import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

public class DiscordCommandSource implements CommandSource {
    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void sendSystemMessage(Component component) {
        if (!buffer.isEmpty()) {
            buffer.append("\n");
        }
        buffer.append(component.getString());
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return true;
    }

    public String getOutput() {
        String result = buffer.toString().trim();
        if (result.isEmpty()) {
            return "Command executed successfully (no output returned).";
        }
        // Keep output within Discord's 2000-character message limit
        if (result.length() > 1850) {
            return result.substring(0, 1850) + "\n... (truncated)";
        }
        return result;
    }
}