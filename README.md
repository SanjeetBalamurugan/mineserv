# MineServ

## Description
MineServ is a server-side Fabric mod for Minecraft 26.3 that provides remote server management, log streaming, chat bridging, account linking, verification, player inspection, and maintenance scheduling directly inside Discord.

## How to use MineServ

1. Install the `mineserv` mod JAR into your Minecraft server's `mods` folder along with Fabric API.
2. Start the server once to automatically generate the configuration file at `config/mindserv/main.toml`.
3. Fill in your Discord Bot Token, Log Channel ID, and Chat Channel ID in `config/mindserv/main.toml`.
4. Configure your Discord bot in the Discord Developer Portal with **Message Content Intent** enabled and invite it to your server with administrator or manage server permissions.
5. Restart your server. The bot will automatically come online and register its slash commands.
6. Use `/mineserv status`, `/mineserv startmaintanence`, `/mineserv inspect`, or in-game `/link <discord_id>` to manage your server!

For a full breakdown of all available commands, permissions, and configuration options, please check **FEATURES.md**.

### Example Configuration (`config/mindserv/main.toml`)
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

```
Copyright (C) 2026 SanjeetBalamurugan. All rights reserved.
Unauthorized copying of this file, via any medium, is strictly prohibited.