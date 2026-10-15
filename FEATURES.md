# MineServ - Remote Minecraft Server Administration

MineServ is a server-side Fabric mod designed for Minecraft 26.3 that bridges your server with Discord, providing remote administration, log streaming, chat bridging, account linking, and player inspection.

---

## 1. Discord Log Streaming & OP Auditing
- **Flexible Log Modes:** Toggle between streaming all `INFO+` server console logs or restricting the log channel exclusively to operator (OP) and console command executions.
- **Rate-Limit Safe:** Batches log events in memory and flushes them to Discord at configurable intervals (default: every 2.5 seconds).
- **Command Auditing:** Automatically intercepts `Commands#performPrefixedCommand` to record who executed each command and when.

---

## 2. Remote Maintenance Management
- **`/mineserv startmaintanence <timebeforeserverstopinminutes> [reason]`**: Schedules a graceful server shutdown. Broadcasts countdown warnings in-game at 10m, 5m, 3m, 1m, 30s, 15s, 10s, and every second from 5 down to 1. Automatically saves worlds and halts the server when the timer hits zero.
- **`/mineserv cancelmaintenance`**: Aborts an active maintenance countdown and notifies players in-game.

---

## 3. Server Status & Performance Diagnostics
- **`/mineserv status`**: Displays a real-time Discord embed containing:
  - Current TPS and MSPT (Milliseconds Per Tick)
  - Memory Usage (Used / Max MB)
  - Active maintenance timer status
  - Log channel operating mode
  - Online player count and exact usernames

---

## 4. Remote Console & Player Moderation
- **`/mineserv command <cmd>`**: Executes any console command on the Minecraft server and captures its text feedback directly inside the Discord reply using a custom `CommandSource`.
- **`/mineserv kick <player> [reason]`**: Instantly disconnects an online player with a custom reason.
- **`/mineserv ban <action> <player> <reason>` / `unban` / `list`**: Manages server bans directly from Discord.
- **`/mineserv whitelist <action> <player>`**: Manages server whitelist rules (`add`, `remove`, `list`, `on`, `off`, `reload`).

---

## 5. Two-Way Chat Bridge
- **Minecraft to Discord:** Broadcasts player chat messages, joins, and leaves to your designated Discord chat channel. Strips mass-mentions (`@everyone` and `@here`) to prevent spam.
- **Discord to Minecraft:** Relays messages sent in the Discord chat channel directly into the game with a formatted `[Discord] <Username>` prefix.

---

## 6. Account Linking & Bedrock Role Sync
- **In-Game Command (`/link <discord_id>`):** Generates a secure, time-sensitive 6-digit verification code for the player and attempts to send it via Discord DM.
- **Discord Command (`/verify <code>`):** Public command where players confirm their 6-digit code to complete the link.
- **Automatic Bedrock Detection:** Detects whether a player joined via Bedrock Edition using Floodgate UUID signatures, Floodgate reflection APIs, or Geyser username prefixes (`.` by default).
- **Role Assignment:** Automatically assigns configured Discord roles (`linked_role_id` and `bedrock_role_id`) upon verification and re-checks them every time the player joins the server.
- **`/mineserv whois <player>`**: Inspects an online player's Minecraft UUID, platform edition (Java or Bedrock), and linked Discord account.

---

## 7. Remote Player Inventory Inspector
- **`/mineserv inspect <player> [view]`**: Inspects an online player's live data in a Discord embed.
  - **Views:** `all`, `vitals` (Health, Hunger, XP, GameMode, Dimension, Coordinates), `inventory` (Armor, offhand, hotbar, and 36-slot inventory), or `enderchest`.
- **`/mineserv invremove <player> <container> <item> <count>`**: Removes specific items from a player's inventory, Ender Chest, or armor slots remotely.

---

## 8. Configuration (`config/mindserv/main.toml`)

MineServ auto-generates a structured TOML configuration file on first launch:

```toml
[discord]
bot_token = "PASTE_YOUR_BOT_TOKEN_HERE"
log_channel_id = "PASTE_YOUR_LOG_CHANNEL_ID_HERE"

[logging]
log_only_op_commands = true
flush_interval_ms = 2500

[chat_bridge]
enabled = true
chat_channel_id = "PASTE_YOUR_CHAT_CHANNEL_ID_HERE"

[linking]
guild_id = ""
bedrock_role_id = "PASTE_BEDROCK_ROLE_ID_HERE"
linked_role_id = ""
bedrock_username_prefix = "."

[maintenance]
default_reason = "Scheduled server maintenance"