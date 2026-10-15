package com.mineserv.linking;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mineserv.config.MineServConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class LinkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineServ-Linking");
    private static final Path LINKS_FILE = MineServConfig.CONFIG_DIR.resolve("links.toml");
    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^[0-9]{17,20}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CODE_EXPIRY_MS = 5 * 60 * 1000L; // 5 minutes

    // Maps Minecraft Player UUID (String) -> Discord User ID (String)
    private static final Map<String, String> LINKED_ACCOUNTS = new ConcurrentHashMap<>();

    // Maps 6-digit Code -> PendingLink record
    private static final Map<String, PendingLink> PENDING_CODES = new ConcurrentHashMap<>();

    public record PendingLink(
            UUID playerUuid,
            String playerName,
            String discordId,
            boolean isBedrock,
            long expiresAt
    ) {}

    public static void load() {
        try {
            if (!Files.exists(MineServConfig.CONFIG_DIR)) {
                Files.createDirectories(MineServConfig.CONFIG_DIR);
            }
            if (!Files.exists(LINKS_FILE)) {
                Files.writeString(LINKS_FILE, "# MineServ Linked Accounts (UUID = \"DiscordID\")\n[links]\n");
            }

            try (CommentedFileConfig config = CommentedFileConfig.builder(LINKS_FILE).autosave().build()) {
                config.load();
                LINKED_ACCOUNTS.clear();
                com.electronwill.nightconfig.core.Config linksSection = config.get("links");
                if (linksSection != null) {
                    for (com.electronwill.nightconfig.core.Config.Entry entry : linksSection.entrySet()) {
                        LINKED_ACCOUNTS.put(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            }
            LOGGER.info("[MineServ] Loaded {} linked Discord accounts.", LINKED_ACCOUNTS.size());
        } catch (Exception e) {
            LOGGER.error("[MineServ] Failed to load config/mindserv/links.toml", e);
        }
    }

    private static void saveLink(UUID playerUuid, String discordId) {
        LINKED_ACCOUNTS.put(playerUuid.toString(), discordId);
        try (CommentedFileConfig config = CommentedFileConfig.builder(LINKS_FILE).autosave().build()) {
            config.load();
            config.set("links." + playerUuid, discordId);
            config.save();
        } catch (Exception e) {
            LOGGER.error("[MineServ] Failed to save link to links.toml", e);
        }
    }

    public static boolean unlinkAccount(UUID playerUuid) {
        String removed = LINKED_ACCOUNTS.remove(playerUuid.toString());
        if (removed == null) return false;

        try (CommentedFileConfig config = CommentedFileConfig.builder(LINKS_FILE).autosave().build()) {
            config.load();
            config.remove("links." + playerUuid);
            config.save();
        } catch (Exception e) {
            LOGGER.error("[MineServ] Failed to remove link from links.toml", e);
        }
        return true;
    }

    public static String getLinkedDiscordId(UUID playerUuid) {
        return LINKED_ACCOUNTS.get(playerUuid.toString());
    }

    public static boolean isBedrockPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (uuid.getMostSignificantBits() == 0L || uuid.version() == 0) {
            return true;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object apiInstance = getInstance.invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            if ((boolean) isFloodgatePlayer.invoke(apiInstance, uuid)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        String prefix = MineServConfig.bedrockUsernamePrefix;
        return !prefix.isEmpty() && player.getName().getString().startsWith(prefix);
    }

    /**
     * Step 1 (In-Game): Generates a 6-digit verification code for the player and sends instructions.
     */
    public static void initiateLink(ServerPlayer player, String discordIdInput, JDA jda) {
        String discordId = discordIdInput.trim();
        if (!DISCORD_ID_PATTERN.matcher(discordId).matches()) {
            player.sendSystemMessage(Component.literal("[MineServ] Invalid Discord User ID. Right-click your profile in Discord and click Copy User ID.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (jda == null) {
            player.sendSystemMessage(Component.literal("[MineServ] Discord bot is currently offline. Please try again later.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("[MineServ] Could not resolve Discord server. Check guild_id or log_channel_id in main.toml.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        boolean bedrock = isBedrockPlayer(player);

        guild.retrieveMemberById(discordId).queue(
                member -> {
                    // Remove any previous pending codes for this player
                    PENDING_CODES.values().removeIf(p -> p.playerUuid().equals(player.getUUID()));

                    String code = generateSixDigitCode();
                    long expiresAt = System.currentTimeMillis() + CODE_EXPIRY_MS;
                    PENDING_CODES.put(code, new PendingLink(
                            player.getUUID(),
                            player.getName().getString(),
                            discordId,
                            bedrock,
                            expiresAt
                    ));

                    // Attempt to DM the user on Discord as a convenience
                    member.getUser().openPrivateChannel().queue(
                            dm -> dm.sendMessage("**[MineServ] Account Link Request**\n"
                                    + "Minecraft Player: `" + player.getName().getString() + "`\n"
                                    + "Verification Code: **`" + code + "`**\n"
                                    + "Run `/verify code:" + code + "` inside the Discord server within 5 minutes to confirm.").queue(null, ignored -> {}),
                            ignored -> {}
                    );

                    player.sendSystemMessage(Component.literal(
                            "[MineServ] Your 6-digit verification code is: " + code
                    ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    player.sendSystemMessage(Component.literal(
                            "[MineServ] Run /verify code:" + code + " in the Discord server within 5 minutes to complete linking."
                    ).withStyle(ChatFormatting.YELLOW));
                },
                error -> player.sendSystemMessage(Component.literal(
                        "[MineServ] Could not find a member with Discord ID " + discordId + " in the Discord server."
                ).withStyle(ChatFormatting.RED))
        );
    }

    /**
     * Step 2 (In Discord): Verifies the 6-digit code when the user runs /verify <code>.
     */
    public static void handleDiscordVerify(SlashCommandInteractionEvent event, MinecraftServer mcServer) {
        String code = event.getOption("code").getAsString().trim();
        long now = System.currentTimeMillis();

        // Purge expired codes
        PENDING_CODES.values().removeIf(p -> p.expiresAt() < now);

        PendingLink pending = PENDING_CODES.get(code);
        if (pending == null) {
            event.reply("[Error] Invalid or expired verification code. Run `/link <discord_id>` in Minecraft to get a new 6-digit code.")
                    .setEphemeral(true).queue();
            return;
        }

        // Ensure the Discord user running /verify is the exact user requested in /link
        if (!event.getUser().getId().equals(pending.discordId())) {
            event.reply("[Error] This verification code belongs to a different Discord user.")
                    .setEphemeral(true).queue();
            return;
        }

        PENDING_CODES.remove(code);
        saveLink(pending.playerUuid(), pending.discordId());

        Guild guild = event.getGuild();
        if (guild == null) {
            guild = resolveGuild(event.getJDA());
        }
        if (guild != null) {
            assignConfiguredRoles(guild, pending.discordId(), pending.isBedrock());
        }

        String platform = pending.isBedrock() ? "Bedrock Edition" : "Java Edition";
        String roleNotice = (pending.isBedrock() && MineServConfig.hasBedrockRoleConfigured())
                ? "\n- **Role Assigned:** Bedrock Edition role granted."
                : "";

        event.reply("**[MineServ] Account Linked Successfully**\n"
                + "- **Minecraft Account:** `" + pending.playerName() + "`\n"
                + "- **Platform:** `" + platform + "`"
                + roleNotice).setEphemeral(true).queue();

        // Notify the player in-game if they are still online
        if (mcServer != null) {
            mcServer.execute(() -> {
                ServerPlayer onlinePlayer = mcServer.getPlayerList().getPlayer(pending.playerUuid());
                if (onlinePlayer != null) {
                    onlinePlayer.sendSystemMessage(Component.literal(
                            "[MineServ] Your Minecraft account is now linked to Discord user " + event.getUser().getName() + " (" + platform + ")."
                    ).withStyle(ChatFormatting.GREEN));
                }
            });
        }
    }

    public static void syncRolesOnJoin(ServerPlayer player, JDA jda) {
        String discordId = getLinkedDiscordId(player.getUUID());
        if (discordId == null || jda == null) return;

        Guild guild = resolveGuild(jda);
        if (guild == null) return;

        assignConfiguredRoles(guild, discordId, isBedrockPlayer(player));
    }

    private static void assignConfiguredRoles(Guild guild, String discordId, boolean isBedrock) {
        UserSnowflake user = UserSnowflake.fromId(discordId);

        if (MineServConfig.hasLinkedRoleConfigured()) {
            Role linkedRole = guild.getRoleById(MineServConfig.linkedRoleId);
            if (linkedRole != null) {
                guild.addRoleToMember(user, linkedRole).queue(
                        null,
                        err -> LOGGER.warn("[MineServ] Failed to assign linked_role_id to {}: {}", discordId, err.getMessage())
                );
            }
        }

        if (isBedrock && MineServConfig.hasBedrockRoleConfigured()) {
            Role bedrockRole = guild.getRoleById(MineServConfig.bedrockRoleId);
            if (bedrockRole != null) {
                guild.addRoleToMember(user, bedrockRole).queue(
                        null,
                        err -> LOGGER.warn("[MineServ] Failed to assign bedrock_role_id to {}: {}", discordId, err.getMessage())
                );
            } else {
                LOGGER.warn("[MineServ] Could not find Bedrock role with ID: {}", MineServConfig.bedrockRoleId);
            }
        }
    }

    private static String generateSixDigitCode() {
        String code;
        do {
            code = String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (PENDING_CODES.containsKey(code));
        return code;
    }

    private static Guild resolveGuild(JDA jda) {
        if (!MineServConfig.guildId.isBlank()) {
            Guild g = jda.getGuildById(MineServConfig.guildId.trim());
            if (g != null) return g;
        }
        TextChannel logChannel = jda.getTextChannelById(MineServConfig.logChannelId);
        return logChannel != null ? logChannel.getGuild() : null;
    }
}