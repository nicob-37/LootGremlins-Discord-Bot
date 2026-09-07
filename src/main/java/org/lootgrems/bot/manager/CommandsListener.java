package org.lootgrems.bot.manager;

import net.dv8tion.jda.api.EmbedBuilder;
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

import javax.swing.text.NumberFormatter;

public class CommandsListener extends ListenerAdapter {
    public List<SlashCommandEx> commands = new ArrayList<>();

    private final HypixelAPIAccess apiAccess = new HypixelAPIAccess();
    private final NumberFormat formatter = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);

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
        commands.add(new SlashCommandEx("update", "Restarts the bot", ID.NICO));
        commands.add(new SlashCommandEx("stop", "Stops the bot", ID.NICO));

        // GUILD COMMANDS
        commands.add(new SlashCommandEx("stats", "Networth and Level of player")
                .addOption(OptionType.STRING, "username", "Username of player", true));


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
                    event.reply("Restarting and checking for update...").queue(success -> {
                        try {
                            ProcessBuilder pb = new ProcessBuilder("setsid", "sh", "/home/ubuntu/LootGremlinsBot/update_bot.sh");
                            pb.start();
                            Thread.sleep(1000);
                            event.getJDA().shutdown();
                            System.exit(0);
                        } catch (Exception e) {
                            event.getChannel().sendMessage("Critical error: " + e.getMessage()).queue();
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
                            HypixelAPIAccess.SkyblockStats stats = apiAccess.getSkyCryptStats(ign);

                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle(ign + " [" + stats.cuteName() + "]");
                            eb.setColor(new Color(0x00FFFF));

                            eb.addField("SkyBlock Level", String.format("%,d", stats.sbLevel()), true);
                            eb.addField("Networth", formatter.format(stats.totalNetworth()), true);

                            event.getHook().sendMessageEmbeds(eb.build()).queue();
                        } catch (IllegalArgumentException e) {
                            event.getHook().sendMessage(e.getMessage()).queue();
                        } catch (Exception e) {
                            event.getHook().sendMessage("Error fetching stats: " + e.getMessage()).queue();
                        }
                    });
                }

            }

        }
        else {
            event.reply("Commands are currently disabled.").setEphemeral(true).queue();
        }
    }

}