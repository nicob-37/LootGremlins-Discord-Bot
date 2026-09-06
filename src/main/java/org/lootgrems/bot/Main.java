package org.lootgrems.bot;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.lootgrems.bot.manager.CommandsListener;

import java.util.EnumSet;

public class Main {

    static CommandsListener commandsListener = new CommandsListener();

    public static void main(String args[]) {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("DISCORD_TOKEN");

        if (token == null || token.isEmpty()) {
            System.err.println("!!! Check .env file Discord Token is missing !!!");
            return;
        }

        JDABuilder builder = JDABuilder.createDefault(token);

        builder.enableIntents(EnumSet.allOf(GatewayIntent.class));
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE);


        builder.addEventListeners(
               commandsListener
        );

        JDA bot = builder.build();
    }

}
