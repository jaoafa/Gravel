package org.jaoafa.gravel;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Main extends JavaPlugin {
    static Main main;

    static JDA jda;
    @Override
    public void onEnable() {
        main = this;
        getServer().sendMessage(Component.text().append(
            Component.text("[Gravel]"),
            Component.space(),
            Component.text("プラグインが読み込まれました。", NamedTextColor.GREEN)
        ));

        FileConfiguration config = getConfig();
        try {
            JDABuilder jdabuilder = JDABuilder.createDefault(config.getString("token"))
                // .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                .setAutoReconnect(true)
                .setBulkDeleteSplittingEnabled(false)
                .setContextEnabled(false)
                .setEventManager(new AnnotatedEventManager());

            jdabuilder.addEventListeners(new Event_ReceivedServerChat());

            jda = jdabuilder.build().awaitReady();
        } catch (Exception e) {
            getLogger().warning("Discordへの接続に失敗しました。(" + e.getClass().getName() + " " + e.getMessage() + ")");
            getLogger().warning("プラグインを無効化します。");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }

        Objects.requireNonNull(getCommand("loadplugin")).setExecutor(new Cmd_LoadPlugin());
        Objects.requireNonNull(getCommand("changeperm")).setExecutor(new Cmd_ChangePerm());
        getServer().getPluginManager().registerEvents(new Event_ReceivedServerChat(), this);
    }

    @Override
    public void onDisable(){
        jda.getEventManager().getRegisteredListeners()
            .forEach(listener -> jda.getEventManager().unregister(listener));
        jda.shutdownNow();
    }

    public static JavaPlugin getJavaPlugin() {
        return main;
    }

    public static JDA getJDA() {
        return jda;
    }
}
