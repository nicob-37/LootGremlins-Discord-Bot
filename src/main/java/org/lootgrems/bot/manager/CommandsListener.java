package org.lootgrems.bot.manager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lootgrems.bot.ID;

public class CommandsListener extends ListenerAdapter {
    public List<SlashCommandEx> commands = new ArrayList<>();

    private final HypixelAPIAccess apiAccess = new HypixelAPIAccess(getResolvedApiKey());
    private final NumberFormat formatter = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);

    private static String getResolvedApiKey() {
        String key = System.getenv("HYPIXEL_API_KEY");
        if (key != null && !key.isBlank()) return key;

        try {
            java.io.File envFile = new java.io.File(".env");
            if (envFile.exists()) {
                for (String line : java.nio.file.Files.readAllLines(envFile.toPath())) {
                    if (line.trim().startsWith("HYPIXEL_API_KEY=")) {
                        return line.substring("HYPIXEL_API_KEY=".length()).trim();
                    }
                }
            }
        } catch (Exception ignored) {}

        System.err.println("WARNING: HYPIXEL_API_KEY not found in env or .env file!");
        return "";
    }

    boolean commandsEnabled = true;
    boolean pushingGlobal = false;

    public void onReady(@NotNull net.dv8tion.jda.api.events.session.ReadyEvent event) {
        initCommands(event);
    }

    public static class SlashCommandEx {
        String name, description;
        List<String> authorizedUsers = new ArrayList<>();
        SlashCommandData data;

        public SlashCommandEx(String name, String description) {
            this(name, description, new String[0]);
        }

        public SlashCommandEx(String name, String description, String... allowedUsers) {
            this.name = name;
            this.description = description;

            if (allowedUsers != null) {
                this.authorizedUsers.addAll(java.util.Arrays.asList(allowedUsers));
            }

            this.data = Commands.slash(this.name, this.description)
                    .setContexts(
                            InteractionContextType.GUILD,
                            InteractionContextType.BOT_DM,
                            InteractionContextType.PRIVATE_CHANNEL)
                    .setIntegrationTypes(
                            IntegrationType.GUILD_INSTALL,
                            IntegrationType.USER_INSTALL);
        }

        public SlashCommandEx addOption(OptionType type, String name, String desc, boolean required) {
            this.data.addOption(type, name, desc, required);
            return this;
        }

        public SlashCommandEx addOptions(OptionData... options) {
            this.data.addOptions(options);
            return this;
        }
    }

    public void initCommands(@NotNull net.dv8tion.jda.api.events.session.ReadyEvent event) {
        commands.clear();

        var guild = event.getJDA().getGuildById(ID.LOOT_GREMLINS);

        // ------------------------Commands----------------------------
        commands.add(new SlashCommandEx("ping", "Pong"));

        // ADMIN COMMANDS
        commands.add(new SlashCommandEx("update", "Restarts the bot", ID.NICO)
                .addOption(OptionType.STRING, "api-key", "API Key for Hypixel", false));
        commands.add(new SlashCommandEx("stop", "Stops the bot", ID.NICO));

        // GUILD COMMANDS
        commands.add(new SlashCommandEx("stats", "Networth and Level of player")
                .addOption(OptionType.STRING, "username", "Username of player", true));

        commands.add(new SlashCommandEx("gremlin", "Use this to get your Gremlin Role in the Guild!")
                .addOption(OptionType.STRING, "username", "Your Minecraft IGN", true));


        // ------------------------------------------------------------
        List<SlashCommandData> jdaData = new ArrayList<>();
        for (SlashCommandEx ex : commands) { jdaData.add(ex.data); }

        if (guild != null) {
            guild.updateCommands().addCommands(jdaData).queue();
            System.out.println("Custom Commands Synced in Loot Gremlins");
        }

        if (pushingGlobal) {
            event.getJDA().updateCommands().addCommands(jdaData).queue();
        }

        event.getJDA().updateCommands().queue(
                s -> System.out.println("Flushed all global commands successfully.")
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        SlashCommandEx commandEx = commands.stream()
                .filter(cmd -> cmd.name.equalsIgnoreCase(event.getName()))
                .findFirst()
                .orElse(null);

        if (commandEx == null) return;

        if (!commandEx.authorizedUsers.isEmpty()) {
            if (!commandEx.authorizedUsers.contains(event.getUser().getId())) {
                event.reply("Nice try, " + event.getUser().getEffectiveName() + ". You aren't authorized to use this.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
        }

        if (event.getUser().getId().equals(ID.NICO) || commandsEnabled) {

            switch (event.getName().toLowerCase()) {

                case "ping" -> {
                    event.reply("Pong! (" + event.getJDA().getGatewayPing() + "ms)").queue();
                }

                case "update" -> {
                    OptionMapping keyOption = event.getOption("api_key");

                    if (keyOption != null) {
                        String newKey = keyOption.getAsString().trim();
                        try {
                            updateEnvFile("HYPIXEL_API_KEY", newKey);
                        } catch (Exception e) {
                            event.reply("Failed to update .env: " + e.getMessage()).setEphemeral(true).queue();
                            return;
                        }
                    }

                    event.reply("Updating environment and restarting...").setEphemeral(true).queue(success -> {
                        try {
                            ProcessBuilder pb = new ProcessBuilder("setsid", "sh", "/home/ubuntu/LootGremlinsBot/update_bot.sh");
                            pb.start();
                            Thread.sleep(1000);
                            event.getJDA().shutdown();
                            System.exit(0);
                        } catch (Exception e) {
                            event.getChannel().sendMessage("Critical error during restart: " + e.getMessage()).queue();
                        }
                    });
                }

                case "stop" -> {
                    event.reply("Shutting off bot").queue();
                    event.getJDA().shutdown();
                    System.exit(0);
                }

                case "stats" -> {
                    OptionMapping ignOption = event.getOption("username");
                    if (ignOption == null) {
                        event.reply("Please provide a username.").setEphemeral(true).queue();
                        return;
                    }
                    String ign = ignOption.getAsString();

                    event.deferReply().queue();

                    Thread.ofVirtual().start(() -> {
                        try {
                            String uuid = apiAccess.getUuidFromUsername(ign);
                            HypixelAPIAccess.SkyblockStats stats = apiAccess.getActiveProfileStats(uuid);

                            String cuteName = (stats.cuteName() != null && !stats.cuteName().isBlank())
                                    ? stats.cuteName()
                                    : "Default";

                            double nw = Double.isNaN(stats.totalNetworth()) || Double.isInfinite(stats.totalNetworth())
                                    ? 0.0
                                    : stats.totalNetworth();

                            String formattedNw;
                            try {
                                formattedNw = formatter.format(nw);
                            } catch (Exception ex) {
                                formattedNw = String.format("%,.0f", nw);
                            }
                            if (formattedNw == null || formattedNw.isBlank()) {
                                formattedNw = "0";
                            }

                            int sblevel = Math.max(0, stats.sbLevel());

                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle(ign + " [" + cuteName + "]");
                            if (uuid != null && !uuid.isBlank()) {
                                eb.setThumbnail("https://mc-heads.net/avatar/" + uuid + "/100");
                            }

                            eb.addField("SkyBlock Level", String.valueOf(sblevel), true);
                            eb.addField("Networth", formattedNw, true);

                            eb.setColor(getLevelColor(sblevel));

                            event.getHook().sendMessageEmbeds(eb.build()).queue();

                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            event.getHook().sendMessage("Input error: " + (e.getMessage() != null ? e.getMessage() : "Unknown")).queue();
                        } catch (Exception e) {
                            e.printStackTrace();
                            event.getHook().sendMessage("Error fetching stats: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())).queue();
                        }
                    });
                }

                case "gremlin" -> {
                    OptionMapping ignOption = event.getOption("username");
                    if (ignOption == null) {
                        event.reply("Please provide a username.").setEphemeral(true).queue();
                        return;
                    }
                    String ign = ignOption.getAsString();

                    event.deferReply().queue();

                    net.dv8tion.jda.api.entities.Member member = event.getMember();
                    net.dv8tion.jda.api.entities.Guild guild = event.getGuild();

                    if (guild != null && member != null) {
                        if (guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.NICKNAME_MANAGE)
                                && guild.getSelfMember().canInteract(member)) {

                            member.modifyNickname(ign).queue(
                                    success -> System.out.println("Updated nickname for " + member.getUser().getName() + " to " + ign),
                                    error -> System.err.println("Could not update nickname: " + error.getMessage())
                            );
                        }
                    }

                    Thread.ofVirtual().start(() -> {
                        try {
                            String uuid = apiAccess.getUuidFromUsername(ign);
                            HypixelAPIAccess.SkyblockStats stats = apiAccess.getActiveProfileStats(uuid);

                            String cuteName = (stats.cuteName() != null && !stats.cuteName().isBlank())
                                    ? stats.cuteName()
                                    : "Default";

                            double nw = Double.isNaN(stats.totalNetworth()) || Double.isInfinite(stats.totalNetworth())
                                    ? 0.0
                                    : stats.totalNetworth();

                            String formattedNw;
                            try {
                                formattedNw = formatter.format(nw);
                            } catch (Exception ex) {
                                formattedNw = String.format("%,.0f", nw);
                            }
                            if (formattedNw == null || formattedNw.isBlank()) {
                                formattedNw = "0";
                            }

                            int sblevel = Math.max(0, stats.sbLevel());

                            if (guild != null && member != null) {
                                String earnedRoleId = calculateGremlinRole(sblevel, nw);

                                if (guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)
                                        && guild.getSelfMember().canInteract(member)) {

                                    for (Role currentRole : member.getRoles()) {
                                        if (ID.ALL_GREMLIN_ROLES.contains(currentRole.getId())
                                                && (earnedRoleId == null || !currentRole.getId().equals(earnedRoleId))) {
                                            if (guild.getSelfMember().canInteract(currentRole)) {
                                                guild.removeRoleFromMember(member, currentRole).queue();
                                            }
                                        }
                                    }

                                    if (earnedRoleId != null && !earnedRoleId.isBlank()) {
                                        Role targetRole = guild.getRoleById(earnedRoleId);
                                        if (targetRole != null && !member.getRoles().contains(targetRole)) {
                                            if (guild.getSelfMember().canInteract(targetRole)) {
                                                guild.addRoleToMember(member, targetRole).queue();
                                            }
                                        }
                                    }
                                }
                            }

                            // Build Embed
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle("Congrats " + ign + " [" + cuteName + "] !");
                            if (uuid != null && !uuid.isBlank()) {
                                eb.setThumbnail("https://mc-heads.net/avatar/" + uuid + "/100");
                            }

                            eb.addField("SkyBlock Level", String.valueOf(sblevel), true);
                            eb.addField("Networth", formattedNw, true);

                            eb.setColor(getLevelColor(sblevel));

                            event.getHook().sendMessageEmbeds(eb.build()).queue();

                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            event.getHook().sendMessage("Input error: " + (e.getMessage() != null ? e.getMessage() : "Unknown")).queue();
                        } catch (Exception e) {
                            e.printStackTrace();
                            event.getHook().sendMessage("Error fetching stats: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())).queue();
                        }
                    });
                }

            }

        }
        else {
            event.reply("Commands are currently disabled.").setEphemeral(true).queue();
        }
    }

    private static synchronized void updateEnvFile(String key, String value) throws java.io.IOException {
        java.io.File envFile = new java.io.File(".env");
        java.util.List<String> lines = envFile.exists()
                ? new java.util.ArrayList<>(java.nio.file.Files.readAllLines(envFile.toPath()))
                : new java.util.ArrayList<>();

        boolean updated = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(key + "=")) {
                lines.set(i, key + "=" + value);
                updated = true;
                break;
            }
        }

        if (!updated) {
            lines.add(key + "=" + value);
        }

        java.nio.file.Files.write(envFile.toPath(), lines,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static Color getLevelColor(int level) {
        if (level >= 480) return new Color(0xAA0000); // Dark Red
        if (level >= 440) return new Color(0xFF5555); // Red
        if (level >= 400) return new Color(0xFFAA00); // Gold
        if (level >= 360) return new Color(0xAA00AA); // Dark Purple
        if (level >= 320) return new Color(0xFF55FF); // Light Purple / Pink
        if (level >= 280) return new Color(0x5555FF); // Blue
        if (level >= 240) return new Color(0x00AAAA); // Cyan
        if (level >= 200) return new Color(0x55FFFF); // Aqua
        if (level >= 160) return new Color(0x00AA00); // Dark Green
        if (level >= 120) return new Color(0x55FF55); // Lime Green
        if (level >= 80)  return new Color(0xFFFF55); // Yellow
        if (level >= 40)  return new Color(0xFFFFFF); // White
        return new Color(0xAAAAAA);                    // Gray
    }

    public static String calculateGremlinRole(int level, double networth) {
        if (level >= 500 && networth >= 25_000_000_000.0) return ID.RICH_GREMLIN;
        if (level >= 360 && networth >= 8_000_000_000.0) return ID.COOL_GREMLIN;
        if (level >= 200) return ID.GREMLIN;
        return ID.LIL_GREMLIN;
    }

}