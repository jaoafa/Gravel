package org.jaoafa.gravel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class Cmd_ChangePerm implements CommandExecutor {
    List<String> perms = Arrays.asList("admin", "moderator", "regular", "verified", "default");
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "このコマンドはプレイヤーからのみ実行できます。");
            return true;
        }

        if(args.length == 1) {
            if(!perms.contains(args[0])){
                sendMessage(sender, String.format("一つ目のパラメーターには %s のいずれかを指定できます。", String.join(", ", perms)));
                return true;
            }
            String cmd = String.format("lp user %s parent set %s", player.getName(), args[0]);
            boolean bool = Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
            sendMessage(player, String.format("権限グループ変更コマンドの実行に %s しました。", bool ? "成功" : "失敗"));
            return true;
        }
        sendMessage(sender, String.format("一つ目のパラメーターには %s のいずれかを指定できます。", String.join(", ", perms)));
        return true;
    }

    void sendMessage(CommandSender sender, String message){
        sender.sendMessage("[ChangePerm] " + ChatColor.GREEN + message);
    }
}
