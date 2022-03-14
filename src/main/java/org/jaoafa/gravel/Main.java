package org.jaoafa.gravel;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public final class Main extends JavaPlugin implements Listener {
    static Main main;
    static JDA jda;
    public static File stateSaveTo;

    @Override
    public void onEnable() {
        main = this;
        stateSaveTo = new File(this.getDataFolder(), "nowState.yml");
        getServer().sendMessage(Component.text().append(
            Component.text("[Gravel]"),
            Component.space(),
            Component.text("プラグインが読み込まれました。", NamedTextColor.GREEN)
        ));

        if (!getServer().getPluginManager().isPluginEnabled("MyMaid4")) {
            getServer().sendMessage(Component.text().append(
                Component.text("[Gravel]"),
                Component.space(),
                Component.text("MyMaid4が有効化されていないか、存在しません。", NamedTextColor.RED)
            ));
            File mymaidFile = new File("plugins", "MyMaid4.jar");
            if (mymaidFile.exists()) {
                boolean bool = mymaidFile.delete();
                getServer().sendMessage(Component.text().append(
                    Component.text("[Gravel]"),
                    Component.space(),
                    Component.text("不具合が発生することを抑制するため、MyMaid4.jarを削除することに " + (bool ? "成功" : "失敗") + " しました。")
                ));
            }
        }

        checkMyMaid4();

        FileConfiguration config = getConfig();
        try {
            JDABuilder jdabuilder = JDABuilder.createDefault(config.getString("token"))
                // .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                .setAutoReconnect(true)
                .setBulkDeleteSplittingEnabled(false)
                .setContextEnabled(false);

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
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Event_ReceivedServerChat(), this);
    }

    @Override
    public void onDisable() {
        jda.getEventManager().getRegisteredListeners()
            .forEach(listener -> jda.getEventManager().unregister(listener));
        jda.shutdownNow();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        checkMyMaid4();
    }

    void checkMyMaid4() {
        if (Main.stateSaveTo.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(stateSaveTo);
            getServer().sendMessage(Component.text().append(
                Component.text("[Gravel]"),
                Component.space(),
                Component.text("現在ロードされているMyMaid4は ", NamedTextColor.GREEN),
                Component.text(String.format("%s/%s", yml.getString("user"), yml.getString("repo")), NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text(
                        String.format("https://github.com/%s/%s を開きます。", yml.getString("user"), yml.getString("repo"))
                    )))
                    .clickEvent(ClickEvent.openUrl(
                        String.format("https://github.com/%s/%s", yml.getString("user"), yml.getString("repo"))
                    )),
                Component.text(" の ", NamedTextColor.GREEN),
                Component.text(String.format("%sブランチ", yml.getString("branch")), NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text(
                        String.format("https://github.com/%s/%s/tree/%s を開きます。", yml.getString("user"), yml.getString("repo"), yml.getString("branch"))
                    )))
                    .clickEvent(ClickEvent.openUrl(
                        String.format("https://github.com/%s/%s/tree/%s", yml.getString("user"), yml.getString("repo"), yml.getString("branch"))
                    )),
                Component.text(" です。", NamedTextColor.GREEN),
                Component.text(" (ビルド日時: ", NamedTextColor.GREEN),
                Component.text(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date(yml.getLong("time") + 32400000)), NamedTextColor.GREEN),
                Component.text(")", NamedTextColor.GREEN)
            ));
        }
    }

    public static JavaPlugin getJavaPlugin() {
        return main;
    }

    public static JDA getJDA() {
        return jda;
    }
}
