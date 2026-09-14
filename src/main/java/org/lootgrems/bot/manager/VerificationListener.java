package org.lootgrems.bot.manager;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.lootgrems.bot.ID;

import java.time.Duration;

public class VerificationListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.getChannel().getId().equals(ID.ONE_TIME_VERIFICATION)) {
            return;
        }

        if (event.getAuthor().isBot()) return;

        String authorId = event.getAuthor().getId();
        if (authorId.equals(ID.NICO) || authorId.equals(ID.UNHEALTHYER)) {
            return;
        }

        if (event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE)) {
            event.getMessage().delete().queue(null, error -> {});
        }

        if (event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND)) {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " Please use the `/gremlin` slash command to verify.")
                    .delay(Duration.ofSeconds(5))
                    .flatMap(net.dv8tion.jda.api.entities.Message::delete)
                    .queue(null, error -> {});
        }
    }
}