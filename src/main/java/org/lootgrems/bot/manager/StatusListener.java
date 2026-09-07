package org.lootgrems.bot.manager;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class StatusListener extends ListenerAdapter {

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        event.getJDA().getPresence().setActivity(Activity.customStatus("Run /gremlin | Ping FakeNico for help"));
    }
}
