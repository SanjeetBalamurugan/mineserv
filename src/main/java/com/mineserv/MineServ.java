package com.mineserv;

import com.mineserv.chat.ChatBridgeManager;
import com.mineserv.command.DiscordCommandSource;
import com.mineserv.command.MinecraftCommands;
import com.mineserv.config.MineServConfig;
import com.mineserv.inspect.PlayerInspector;
import com.mineserv.linking.LinkManager;
import com.mineserv.logging.DiscordLogAppender;
import com.mineserv.maintenance.MaintenanceManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MineServ extends ListenerAdapter implements DedicatedServerModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("MineServ");
	private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_.]{1,16}$");

	private volatile JDA jda;
	private MinecraftServer mcServer;
	private final DiscordLogAppender logAppender = new DiscordLogAppender();

	@Override
	public void onInitializeServer() {
		MineServConfig.load();
		LinkManager.load();
		MinecraftCommands.register(this);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			LinkManager.syncRolesOnJoin(handler.player, this.jda);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			this.mcServer = server;
			if (!MineServConfig.isConfigured()) {
				LOGGER.warn(
						"[MineServ] Bot token or log channel ID is not configured. Edit config/mindserv/main.toml and restart.");
				return;
			}
			Thread.ofVirtual().name("MineServ-JDA-Init").start(this::startDiscordBot);
		});

		ServerTickEvents.END_SERVER_TICK.register(MaintenanceManager::onServerTick);

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (jda != null) {
				TextChannel channel = jda.getTextChannelById(MineServConfig.logChannelId);
				if (channel != null) {
					channel.sendMessage("**[MineServ] Server is shutting down.**").complete();
				}
				logAppender.stopStreaming();
				jda.shutdown();
			}
		});
	}

	public JDA getJda() {
		return jda;
	}

	private void startDiscordBot() {
		try {
			jda = JDABuilder.createDefault(
					MineServConfig.botToken,
					GatewayIntent.GUILD_MESSAGES,
					GatewayIntent.MESSAGE_CONTENT)
					.addEventListeners(this)
					.build()
					.awaitReady();

			ChatBridgeManager.init(mcServer, jda);

			jda.updateCommands().addCommands(
					// Public command for players to verify their 6-digit code
					Commands.slash("verify", "Confirm your 6-digit Minecraft account link code")
							.addOption(OptionType.STRING, "code", "The 6-digit code from /link in Minecraft", true),

					// Administrator command suite
					Commands.slash("mineserv", "Remote Minecraft 26.3 Server Administration")
							.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
							.addSubcommands(
									new SubcommandData("startmaintanence", "Schedule a server maintenance shutdown")
											.addOption(OptionType.INTEGER, "timebeforeserverstopinminutes",
													"Minutes until the server stops (0 for immediate)", true)
											.addOption(OptionType.STRING, "reason",
													"Optional reason broadcast to players", false),
									new SubcommandData("cancelmaintenance", "Cancel an active maintenance countdown"),
									new SubcommandData("status",
											"View live server TPS, MSPT, memory usage, and online players"),
									new SubcommandData("logmode",
											"Toggle whether the log channel records ONLY OP commands or ALL console logs")
											.addOption(OptionType.BOOLEAN, "op_only",
													"True = only OP commands, False = all server logs", true),
									new SubcommandData("command",
											"Execute a console command on the Minecraft server and view its output")
											.addOption(OptionType.STRING, "cmd",
													"The command to execute (without leading slash)", true),
									new SubcommandData("kick", "Kick an online player from the server")
											.addOption(OptionType.STRING, "player", "Exact player username", true)
											.addOption(OptionType.STRING, "reason", "Kick reason", false),
									new SubcommandData("whitelist", "Manage the server whitelist")
											.addOptions(
													new OptionData(OptionType.STRING, "action",
															"Whitelist action to perform", true)
															.addChoice("add", "add")
															.addChoice("remove", "remove")
															.addChoice("list", "list")
															.addChoice("on", "on")
															.addChoice("off", "off")
															.addChoice("reload", "reload"),
													new OptionData(OptionType.STRING, "player",
															"Player username (required for add and remove)", false)),
									new SubcommandData("ban", "Ban, unban, or list banned players")
											.addOptions(
													new OptionData(OptionType.STRING, "action", "Ban action to perform",
															true)
															.addChoice("ban", "ban")
															.addChoice("unban", "unban")
															.addChoice("list", "list"),
													new OptionData(OptionType.STRING, "player",
															"Player username (required for ban and unban)", false),
													new OptionData(OptionType.STRING, "reason",
															"Ban reason (optional, used when banning)", false)),
									new SubcommandData("inspect",
											"Inspect an online player's inventory, Ender Chest, armor, and vitals")
											.addOptions(
													new OptionData(OptionType.STRING, "player",
															"Online player username", true),
													new OptionData(OptionType.STRING, "view",
															"Specific section to inspect (defaults to all)", false)
															.addChoice("all", "all")
															.addChoice("inventory", "inventory")
															.addChoice("enderchest", "enderchest")
															.addChoice("vitals", "vitals")),
									new SubcommandData("whois",
											"Check a player's edition (Java/Bedrock) and linked Discord account")
											.addOption(OptionType.STRING, "player", "Online player username", true),
									new SubcommandData("reload",
											"Reload config/mindserv/main.toml and links.toml from disk"),
									new SubcommandData("invremove",
											"Remove items from an online player's inventory or Ender Chest")
											.addOptions(
													new OptionData(OptionType.STRING, "player",
															"Online player username", true),
													new OptionData(OptionType.STRING, "container",
															"Container to modify", true)
															.addChoice("inventory", "inventory")
															.addChoice("enderchest", "enderchest")
															.addChoice("armor", "armor"),
													new OptionData(OptionType.STRING, "item",
															"Item name keyword (e.g. diamond, sword)", true),
													new OptionData(OptionType.INTEGER, "count",
															"Maximum quantity to remove (default 64)", false))))
					.queue();

			TextChannel logChannel = jda.getTextChannelById(MineServConfig.logChannelId);
			if (logChannel != null) {
				logAppender.startStreaming(logChannel);
				String modeText = MineServConfig.logOnlyOpCommands ? "OP Commands Only" : "Full Server Console";
				logChannel.sendMessage("**[MineServ] Online.** Streaming mode: `" + modeText + "`.").queue();
			} else {
				LOGGER.warn("[MineServ] Could not find Discord channel with ID: {}", MineServConfig.logChannelId);
			}
		} catch (Exception e) {
			LOGGER.error("[MineServ] Failed to initialize Discord bot", e);
		}
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent event) {
		ChatBridgeManager.onDiscordMessage(event);
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
		if (event.getName().equals("verify")) {
			LinkManager.handleDiscordVerify(event, mcServer);
			return;
		}

		if (!event.getName().equals("mineserv") || event.getSubcommandName() == null)
			return;

		switch (event.getSubcommandName()) {
			case "startmaintanence" -> handleStartMaintenance(event);
			case "cancelmaintenance" -> handleCancelMaintenance(event);
			case "status" -> handleStatus(event);
			case "logmode" -> handleLogMode(event);
			case "command" -> handleConsoleCommand(event);
			case "kick" -> handleKick(event);
			case "whitelist" -> handleWhitelist(event);
			case "ban" -> handleBan(event);
			case "inspect" -> handleInspect(event);
			case "whois" -> handleWhois(event);
			case "reload" -> handleReload(event);
		}
	}

	private void handleStartMaintenance(SlashCommandInteractionEvent event) {
		int minutes = event.getOption("timebeforeserverstopinminutes").getAsInt();
		String reason = event.getOption("reason", MineServConfig.defaultMaintenanceReason, OptionMapping::getAsString);

		if (minutes < 0) {
			event.reply("[Error] `timebeforeserverstopinminutes` must be 0 or greater.").setEphemeral(true).queue();
			return;
		}

		mcServer.execute(() -> {
			boolean started = MaintenanceManager.startMaintenance(mcServer, minutes, reason);
			if (started) {
				DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(),
						"/mineserv startmaintanence " + minutes + " (" + reason + ")");
				event.reply(
						"**Maintenance started**\n- **Stop in:** `" + minutes + " minute(s)`\n- **Reason:** " + reason)
						.queue();
			} else {
				event.reply(
						"[Warning] A maintenance countdown is already running. Use `/mineserv cancelmaintenance` first.")
						.setEphemeral(true).queue();
			}
		});
	}

	private void handleCancelMaintenance(SlashCommandInteractionEvent event) {
		mcServer.execute(() -> {
			boolean cancelled = MaintenanceManager.cancelMaintenance(mcServer);
			if (cancelled) {
				DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(),
						"/mineserv cancelmaintenance");
				event.reply("**Maintenance countdown cancelled.** Players have been notified in-game.").queue();
			} else {
				event.reply("No maintenance countdown is currently active.").setEphemeral(true).queue();
			}
		});
	}

	private void handleStatus(SlashCommandInteractionEvent event) {
		mcServer.execute(() -> {
			double mspt = mcServer.getAverageTickTimeNanos() / 1_000_000.0;
			double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 0.001));

			Runtime rt = Runtime.getRuntime();
			long usedMemMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
			long maxMemMb = rt.maxMemory() / (1024 * 1024);

			List<ServerPlayer> players = mcServer.getPlayerList().getPlayers();
			String playerNames = players.isEmpty()
					? "None"
					: players.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));

			String maintStatus = MaintenanceManager.isActive()
					? "Stopping in " + MaintenanceManager.getSecondsRemaining() + "s"
					: "Normal";

			EmbedBuilder embed = new EmbedBuilder()
					.setTitle("MineServ Status - Minecraft 26.3")
					.setColor(MaintenanceManager.isActive() ? Color.ORANGE : Color.GREEN)
					.addField("TPS / MSPT", String.format("`%.1f TPS` (`%.2f ms`)", tps, mspt), true)
					.addField("Memory", String.format("`%d MB / %d MB`", usedMemMb, maxMemMb), true)
					.addField("Maintenance", "`" + maintStatus + "`", true)
					.addField("Log Channel Mode",
							MineServConfig.logOnlyOpCommands ? "`OP Commands Only`" : "`All Console Logs`", true)
					.addField("Online Players (" + players.size() + "/" + mcServer.getMaxPlayers() + ")",
							"```" + playerNames + "```", false)
					.setTimestamp(Instant.now());

			event.replyEmbeds(embed.build()).queue();
		});
	}

	private void handleLogMode(SlashCommandInteractionEvent event) {
		boolean opOnly = event.getOption("op_only").getAsBoolean();
		MineServConfig.setLogOnlyOpCommands(opOnly);
		DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(),
				"/mineserv logmode op_only:" + opOnly);

		String desc = opOnly
				? "Log channel will now **only log OP commands**."
				: "Log channel will now stream **all INFO+ server console logs**.";
		event.reply(desc + "\n*(Saved to `config/mindserv/main.toml`)*").queue();
	}

	private void handleConsoleCommand(SlashCommandInteractionEvent event) {
		String rawCmd = event.getOption("cmd").getAsString().trim();
		String cleanCmd = rawCmd.startsWith("/") ? rawCmd.substring(1) : rawCmd;

		event.deferReply().queue();

		mcServer.execute(() -> {
			DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(), "/" + cleanCmd);

			DiscordCommandSource customSource = new DiscordCommandSource();
			CommandSourceStack sourceStack = mcServer.createCommandSourceStack().withSource(customSource);

			mcServer.getCommands().performPrefixedCommand(sourceStack, cleanCmd);

			String output = customSource.getOutput().replace("`", "'");
			event.getHook().sendMessage("**Executed:** `/" + cleanCmd + "`\n```text\n" + output + "\n```").queue();
		});
	}

	private void handleKick(SlashCommandInteractionEvent event) {
		String targetName = event.getOption("player").getAsString().trim();
		String reason = event.getOption("reason", "Kicked by an administrator via Discord.",
				OptionMapping::getAsString);

		mcServer.execute(() -> {
			ServerPlayer player = mcServer.getPlayerList().getPlayerByName(targetName);
			if (player == null) {
				event.reply("[Error] Player `" + targetName + "` is not online.").setEphemeral(true).queue();
				return;
			}
			player.connection.disconnect(Component.literal(reason));
			DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(),
					"/kick " + targetName + " (" + reason + ")");
			event.reply("Kicked **" + player.getName().getString() + "** - Reason: *" + reason + "*").queue();
		});
	}

	private void handleWhitelist(SlashCommandInteractionEvent event) {
		String action = event.getOption("action").getAsString();
		String player = event.getOption("player", "", OptionMapping::getAsString).trim();

		if ((action.equals("add") || action.equals("remove"))) {
			if (player.isEmpty()) {
				event.reply("[Error] The `player` option is required when using `/mineserv whitelist " + action + "`.")
						.setEphemeral(true).queue();
				return;
			}
			if (!VALID_USERNAME.matcher(player).matches()) {
				event.reply("[Error] Invalid Minecraft username: `" + player + "`.").setEphemeral(true).queue();
				return;
			}
		}

		String mcCommand = (action.equals("add") || action.equals("remove"))
				? "whitelist " + action + " " + player
				: "whitelist " + action;

		event.deferReply().queue();

		mcServer.execute(() -> {
			DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(), "/" + mcCommand);

			DiscordCommandSource customSource = new DiscordCommandSource();
			CommandSourceStack sourceStack = mcServer.createCommandSourceStack().withSource(customSource);

			mcServer.getCommands().performPrefixedCommand(sourceStack, mcCommand);

			String output = customSource.getOutput().replace("`", "'");
			event.getHook().sendMessage("**Whitelist (" + action + "):**\n```text\n" + output + "\n```").queue();
		});
	}

	private void handleBan(SlashCommandInteractionEvent event) {
		String action = event.getOption("action").getAsString();
		String player = event.getOption("player", "", OptionMapping::getAsString).trim();
		String reason = event.getOption("reason", "Banned by an administrator via Discord.", OptionMapping::getAsString)
				.trim();

		if (action.equals("ban") || action.equals("unban")) {
			if (player.isEmpty()) {
				event.reply("[Error] The `player` option is required when using `/mineserv ban " + action + "`.")
						.setEphemeral(true).queue();
				return;
			}
			if (!VALID_USERNAME.matcher(player).matches()) {
				event.reply("[Error] Invalid Minecraft username: `" + player + "`.").setEphemeral(true).queue();
				return;
			}
		}

		String mcCommand = switch (action) {
			case "ban" -> "ban " + player + " " + reason;
			case "unban" -> "pardon " + player;
			default -> "banlist players";
		};

		event.deferReply().queue();

		mcServer.execute(() -> {
			DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(), "/" + mcCommand);

			DiscordCommandSource customSource = new DiscordCommandSource();
			CommandSourceStack sourceStack = mcServer.createCommandSourceStack().withSource(customSource);

			mcServer.getCommands().performPrefixedCommand(sourceStack, mcCommand);

			String output = customSource.getOutput().replace("`", "'");
			event.getHook().sendMessage("**Ban Manager (" + action + "):**\n```text\n" + output + "\n```").queue();
		});
	}

	private void handleInspect(SlashCommandInteractionEvent event) {
		String targetName = event.getOption("player").getAsString().trim();
		String view = event.getOption("view", "all", OptionMapping::getAsString);

		mcServer.execute(() -> {
			ServerPlayer player = mcServer.getPlayerList().getPlayerByName(targetName);
			if (player == null) {
				event.reply("[Error] Player `" + targetName + "` is not online.").setEphemeral(true).queue();
				return;
			}

			DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(),
					"/mineserv inspect " + targetName + " (" + view + ")");
			event.replyEmbeds(PlayerInspector.buildInspectionEmbed(player, view)).queue();
		});
	}

	private void handleWhois(SlashCommandInteractionEvent event) {
		String targetName = event.getOption("player").getAsString().trim();

		mcServer.execute(() -> {
			ServerPlayer player = mcServer.getPlayerList().getPlayerByName(targetName);
			if (player == null) {
				event.reply("[Error] Player `" + targetName + "` is not online.").setEphemeral(true).queue();
				return;
			}

			boolean bedrock = LinkManager.isBedrockPlayer(player);
			String linkedId = LinkManager.getLinkedDiscordId(player.getUUID());
			String discordMention = linkedId != null ? "<@" + linkedId + "> (`" + linkedId + "`)" : "Not Linked";

			EmbedBuilder embed = new EmbedBuilder()
					.setTitle("Player Lookup: " + player.getName().getString())
					.setColor(Color.CYAN)
					.addField("UUID", "`" + player.getUUID() + "`", false)
					.addField("Platform", bedrock ? "`Bedrock Edition`" : "`Java Edition`", true)
					.addField("Discord Account", discordMention, true)
					.setTimestamp(Instant.now());

			event.replyEmbeds(embed.build()).queue();
		});
	}

	private void handleReload(SlashCommandInteractionEvent event) {
		MineServConfig.load();
		LinkManager.load();
		event.reply("Reloaded `config/mindserv/main.toml` and `config/mindserv/links.toml`.").queue();
	}

	private void handleInvRemove(SlashCommandInteractionEvent event) {
		String targetName = event.getOption("player").getAsString().trim();
		String container = event.getOption("container").getAsString().trim();
		String itemName = event.getOption("item").getAsString().trim().toLowerCase();
		int count = event.getOption("count", 64, OptionMapping::getAsInt);

		event.deferReply().queue();

		mcServer.execute(() -> {
			ServerPlayer player = mcServer.getPlayerList().getPlayerByName(targetName);
			if (player == null) {
				event.getHook().sendMessage("[Error] Player `" + targetName + "` is not online.").setEphemeral(true)
						.queue();
				return;
			}

			int removedCount = 0;

			switch (container) {
				case "inventory" -> {
					for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
						ItemStack stack = player.getInventory().getItem(i);
						if (!stack.isEmpty() && stack.getItem().getDescriptionId().toLowerCase().contains(itemName)) {
							int toRemove = Math.min(count - removedCount, stack.getCount());
							stack.shrink(toRemove);
							removedCount += toRemove;
							if (removedCount >= count)
								break;
						}
					}
				}
				case "enderchest" -> {
					PlayerEnderChestContainer ec = player.getEnderChestInventory();
					for (int i = 0; i < ec.getContainerSize(); i++) {
						ItemStack stack = ec.getItem(i);
						if (!stack.isEmpty() && stack.getItem().getDescriptionId().toLowerCase().contains(itemName)) {
							int toRemove = Math.min(count - removedCount, stack.getCount());
							stack.shrink(toRemove);
							removedCount += toRemove;
							if (removedCount >= count)
								break;
						}
					}
				}
				case "armor" -> {
					for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
							EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
						ItemStack stack = player.getItemBySlot(slot);
						if (!stack.isEmpty() && stack.getItem().getDescriptionId().toLowerCase().contains(itemName)) {
							player.setItemSlot(slot, ItemStack.EMPTY);
							removedCount += stack.getCount();
							break;
						}
					}
				}
			}

			DiscordLogAppender.recordOpCommand("Discord:" + event.getUser().getName(),
					"/mineserv invremove " + targetName + " " + container + " " + itemName + " count:" + count);

			event.getHook().sendMessage("Removed **" + removedCount + "** matching item(s) containing `" + itemName
					+ "` from " + targetName + "'s `" + container + "`.").queue();
		});
	}
}