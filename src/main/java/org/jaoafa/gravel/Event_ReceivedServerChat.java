package org.jaoafa.gravel;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class Event_ReceivedServerChat implements Listener {
    @SubscribeEvent
    public void onReadyEvent(ReadyEvent event) {
        Bukkit.getServer().sendMessage(Component.text().append(
            Component.text("[Gravel]"),
            Component.space(),
            Component.text("ログイン完了:", NamedTextColor.GREEN),
            Component.space(),
            Component.text(event.getJDA().getSelfUser().getAsTag(), NamedTextColor.GREEN)
        ));
    }

    @SubscribeEvent
    public void onReceived(MessageReceivedEvent event) {
        if(event.getChannel().getIdLong() != 823229253637373962L){
            return;
        }
        if(event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()){
            return;
        }
        Bukkit.getServer().sendMessage(Component.text().append(
            Component.text("[Discord]", NamedTextColor.AQUA),
            Component.space(),
            Component.text(event.getAuthor().getAsTag()),
            Component.text(":"),
            Component.space(),
            Component.text(event.getMessage().getContentDisplay(), NamedTextColor.GREEN)
        ));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event){
        TextChannel channel = Main.getJDA().getTextChannelById(823229253637373962L);
        if(channel == null){
            return;
        }
        channel.sendMessage("**" + event.getPlayer().getName() + "**: " + PlainComponentSerializer.plain().serialize(event.message())).queue();
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event){
        TextChannel channel = Main.getJDA().getTextChannelById(823229253637373962L);
        if(channel == null){
            return;
        }
        channel.sendMessage("`" + event.getPlayer().getName() + "` がログインしました。").queue();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        TextChannel channel = Main.getJDA().getTextChannelById(823229253637373962L);
        if(channel == null){
            return;
        }
        channel.sendMessage("`" + event.getPlayer().getName() + "` がログアウトしました。").queue();
    }
}
