package com.mineserv.logging;

import com.mineserv.config.MineServConfig;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordLogAppender extends AbstractAppender {
    private static final ConcurrentLinkedQueue<String> LOG_QUEUE = new ConcurrentLinkedQueue<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private ScheduledExecutorService scheduler;
    private volatile TextChannel logChannel;

    public DiscordLogAppender() {
        super("MineservDiscordAppender", null,
                PatternLayout.newBuilder().withPattern("[%d{HH:mm:ss}] [%t/%level]: %msg").build(),
                false, Property.EMPTY_ARRAY);
    }

    /**
     * Called directly from CommandsMixin whenever an OP or Console executes a command.
     */
    public static void recordOpCommand(String executorName, String command) {
        String timestamp = TIME_FMT.format(Instant.now());
        LOG_QUEUE.add(String.format("[%s OP-CMD] %s executed: %s", timestamp, executorName, command));
    }

    public void startStreaming(TextChannel channel) {
        this.logChannel = channel;
        if (!this.isStarted()) {
            this.start();
        }
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.addAppender(this);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mineserv-Log-Flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::flushLogs,
                MineServConfig.flushIntervalMs,
                MineServConfig.flushIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    public void stopStreaming() {
        flushLogs();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.removeAppender(this);
        this.stop();
    }

    @Override
    public void append(LogEvent event) {
        if (logChannel == null) return;

        // If log_only_op_commands is true, skip general console logs (OP commands are queued via recordOpCommand)
        if (MineServConfig.logOnlyOpCommands) {
            return;
        }

        // Prevent infinite loop from JDA's own logs
        if (event.getLoggerName().startsWith("net.dv8tion")) return;

        if (event.getLevel().isMoreSpecificThan(Level.INFO)) {
            String timestamp = TIME_FMT.format(Instant.ofEpochMilli(event.getTimeMillis()));
            String formatted = String.format("[%s %s]: %s",
                    timestamp, event.getLevel().name(), event.getMessage().getFormattedMessage());
            LOG_QUEUE.add(formatted);
        }
    }

    private void flushLogs() {
        if (logChannel == null || LOG_QUEUE.isEmpty()) return;

        StringBuilder batch = new StringBuilder("```ansi\n");
        while (!LOG_QUEUE.isEmpty()) {
            String nextLine = LOG_QUEUE.peek();
            if (batch.length() + nextLine.length() + 10 > 1950) {
                break;
            }
            batch.append(LOG_QUEUE.poll().replace("`", "'")).append("\n");
        }
        batch.append("```");

        if (batch.length() > 11) {
            logChannel.sendMessage(batch.toString()).queue();
        }
    }
}