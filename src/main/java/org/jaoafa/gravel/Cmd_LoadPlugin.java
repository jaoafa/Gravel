package org.jaoafa.gravel;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class Cmd_LoadPlugin implements CommandExecutor {
    static BukkitTask task = null;
    Player player;
    String user = "jaoafa";
    String repo = "MyMaid4";
    String branch = "master";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "このコマンドはプレイヤーからのみ実行できます。");
            return true;
        }
        this.player = (Player) sender;

        if(args.length == 1 && args[0].equalsIgnoreCase("stop")){
            if(task == null || task.isCancelled()){
                sendMessage(sender, "タスクは既に終了しています。");
                return true;
            }
            sendMessage(sender, "タスクを終了します。");
            task.cancel();
            task = null;
            return true;
        }

        if(args.length == 1){
            user = args[0];
        } else if (args.length == 2) {
            user = args[0];
            repo = args[1];
        } else if (args.length == 3) {
            user = args[0];
            repo = args[1];
            branch = args[2];
        }

        if (task != null && !task.isCancelled()) {
            sendMessage(sender, "別の処理が動作しているようです。強制終了は /loadplugin stop で行えます。");
            return true;
        }
        RestartTask restartTask = new RestartTask(player, user, repo, branch);

        sendMessage(sender, "処理を開始します。");
        try {
            task = restartTask.runTaskAsynchronously(Main.getJavaPlugin());
        } catch (IllegalStateException e) {
            sendMessage(sender, "既に動作しているようです。強制終了は /loadplugin stop で行えます。");
        }
        return true;
    }

    void sendMessage(CommandSender sender, String message){
        sender.sendMessage("[LoadPlugin] " + ChatColor.GREEN + message);
    }

}
