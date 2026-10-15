package com.mineserv.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MineServConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineservConfig");
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("mindserv");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("main.toml");

    public static String botToken = "PASTE_YOUR_BOT_TOKEN_HERE";
    public static String logChannelId = "PASTE_YOUR_LOG_CHANNEL_ID_HERE";
    public static volatile boolean logOnlyOpCommands = true;
    public static int flushIntervalMs = 2500;

    public static boolean chatBridgeEnabled = true;
    public static String chatChannelId = "PASTE_YOUR_CHAT_CHANNEL_ID_HERE";

    public static String guildId = "";
    public static String bedrockRoleId = "PASTE_BEDROCK_ROLE_ID_HERE";
    public static String linkedRoleId = "";
    public static String bedrockUsernamePrefix = ".";

    public static String defaultMaintenanceReason = "Scheduled server maintenance";

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(CONFIG_FILE)) {
                writeDefaultToml();
            }

            try (CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_FILE).autosave().build()) {
                config.load();
                botToken = config.getOrElse("discord.bot_token", botToken);
                logChannelId = config.getOrElse("discord.log_channel_id", logChannelId);
                logOnlyOpCommands = config.getOrElse("logging.log_only_op_commands", true);
                flushIntervalMs = Math.max(2000, config.getOrElse("logging.flush_interval_ms", 2500));

                chatBridgeEnabled = config.getOrElse("chat_bridge.enabled", true);
                chatChannelId = config.getOrElse("chat_bridge.chat_channel_id", chatChannelId);

                guildId = config.getOrElse("linking.guild_id", "");
                bedrockRoleId = config.getOrElse("linking.bedrock_role_id", bedrockRoleId);
                linkedRoleId = config.getOrElse("linking.linked_role_id", "");
                bedrockUsernamePrefix = config.getOrElse("linking.bedrock_username_prefix", ".");

                defaultMaintenanceReason = config.getOrElse("maintenance.default_reason", defaultMaintenanceReason);
            }
            LOGGER.info("[MineServ] Loaded config/mindserv/main.toml");
        } catch (Exception e) {
            LOGGER.error("[MineServ] Failed to load config/mindserv/main.toml", e);
        }
    }

    public static void setLogOnlyOpCommands(boolean value) {
        logOnlyOpCommands = value;
        try (CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_FILE).autosave().build()) {
            config.load();
            config.set("logging.log_only_op_commands", value);
            config.save();
        } catch (Exception e) {
            LOGGER.error("[MineServ] Failed to update log_only_op_commands in main.toml", e);
        }
    }

    private static void writeDefaultToml() throws IOException {
        String defaultContent = """
                [discord]
                # Your Discord Bot Token from https://discord.com/developers/applications
                bot_token = "PASTE_YOUR_BOT_TOKEN_HERE"
                # The Discord Text Channel ID where server logs / OP commands are sent
                log_channel_id = "PASTE_YOUR_LOG_CHANNEL_ID_HERE"

                [logging]
                # If true, ONLY commands executed by Operators (OPs) or Console are logged to Discord.
                # If false, all INFO/WARN/ERROR server console logs are streamed to Discord.
                log_only_op_commands = true
                # Interval in milliseconds to batch and flush logs (keep >= 2000 to avoid rate limits)
                flush_interval_ms = 2500

                [chat_bridge]
                # Enable two-way chat between Minecraft and a Discord channel
                enabled = true
                # The Discord Text Channel ID for player chat (leave blank to disable)
                chat_channel_id = "PASTE_YOUR_CHAT_CHANNEL_ID_HERE"

                [linking]
                # Optional Discord Server (Guild) ID. If left blank, MineServ uses the server of log_channel_id.
                guild_id = ""
                # Discord Role ID given to Bedrock Edition players when they link their account
                bedrock_role_id = "PASTE_BEDROCK_ROLE_ID_HERE"
                # Optional Discord Role ID given to all linked players (both Java and Bedrock)
                linked_role_id = ""
                # Username prefix used by Geyser/Floodgate for Bedrock players
                bedrock_username_prefix = "."

                [maintenance]
                # Default reason shown to players when maintenance is scheduled
                default_reason = "Scheduled server maintenance"
                """;
        Files.writeString(CONFIG_FILE, defaultContent);
    }

    public static boolean isConfigured() {
        return !botToken.isBlank()
                && !botToken.equals("PASTE_YOUR_BOT_TOKEN_HERE")
                && !logChannelId.isBlank()
                && !logChannelId.equals("PASTE_YOUR_LOG_CHANNEL_ID_HERE");
    }

    public static boolean isChatBridgeActive() {
        return chatBridgeEnabled
                && !chatChannelId.isBlank()
                && !chatChannelId.equals("PASTE_YOUR_CHAT_CHANNEL_ID_HERE");
    }

    public static boolean hasBedrockRoleConfigured() {
        return !bedrockRoleId.isBlank()
                && !bedrockRoleId.equals("PASTE_BEDROCK_ROLE_ID_HERE");
    }

    public static boolean hasLinkedRoleConfigured() {
        return !linkedRoleId.isBlank()
                && !linkedRoleId.equals("PASTE_LINKED_ROLE_ID_HERE");
    }
}