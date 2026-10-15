package com.mineserv.maintenance;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class MaintenanceManager {
    private static int ticksRemaining = -1;
    private static boolean active = false;
    private static String maintenanceReason = "Scheduled server maintenance";

    public static synchronized boolean startMaintenance(MinecraftServer server, int minutes, String reason) {
        if (active) return false;
        active = true;
        ticksRemaining = minutes * 60 * 20;
        maintenanceReason = (reason == null || reason.isBlank()) ? "Scheduled server maintenance" : reason;

        if (minutes == 0) {
            broadcastWarning(server, "Server is shutting down immediately! Reason: " + maintenanceReason);
        } else {
            broadcastWarning(server, "Maintenance scheduled (" + maintenanceReason + "). Server stopping in " + minutes + " minute(s)!");
        }
        return true;
    }

    public static synchronized boolean cancelMaintenance(MinecraftServer server) {
        if (!active) return false;
        active = false;
        ticksRemaining = -1;
        Component msg = Component.literal("[Mineserv] Scheduled maintenance has been cancelled!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        server.getPlayerList().broadcastSystemMessage(msg, false);
        return true;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized int getSecondsRemaining() {
        return active ? Math.max(0, ticksRemaining / 20) : -1;
    }

    public static synchronized void onServerTick(MinecraftServer server) {
        if (!active || ticksRemaining < 0) return;

        if (ticksRemaining > 0 && ticksRemaining % 20 == 0) {
            int secondsLeft = ticksRemaining / 20;
            if (secondsLeft == 600 || secondsLeft == 300 || secondsLeft == 180 || secondsLeft == 60) {
                broadcastWarning(server, "Server stopping for maintenance in " + (secondsLeft / 60) + " minute(s)! (" + maintenanceReason + ")");
            } else if (secondsLeft == 30 || secondsLeft == 15 || secondsLeft == 10 || (secondsLeft <= 5 && secondsLeft >= 1)) {
                broadcastWarning(server, "Server stopping for maintenance in " + secondsLeft + " second(s)!");
            }
        }

        if (ticksRemaining == 0) {
            active = false;
            broadcastWarning(server, "Server is shutting down now: " + maintenanceReason);
            server.halt(false);
        }

        ticksRemaining--;
    }

    private static void broadcastWarning(MinecraftServer server, String text) {
        Component message = Component.literal("[Mineserv] " + text)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}