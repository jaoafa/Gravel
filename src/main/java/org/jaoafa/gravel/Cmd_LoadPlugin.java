package org.jaoafa.gravel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.naming.Name;
import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Cmd_LoadPlugin extends BukkitRunnable implements CommandExecutor {
    static BukkitTask task = null;
    CommandSender sender;
    Player player;
    String user = "jaoafa";
    String repo = "MyMaid4";
    String branch = "master";
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!(sender instanceof Player)){
            sendMessage(sender, "このコマンドはプレイヤーからのみ実行できます。");
            return true;
        }
        this.sender = sender;
        this.player = (Player) sender;

        if(args.length == 1 && args[0].equalsIgnoreCase("stop")){
            if(task == null || task.isCancelled()){
                sendMessage(sender, "タスクは既に終了しています。");
                return true;
            }
            sendMessage(sender, "タスクを終了します。");
            task.cancel();
            this.cancel();
            task = null;
            return true;
        }

        if(args.length == 1){
            user = args[0];
        }else if(args.length == 2){
            user = args[0];
            repo = args[1];
        }else if(args.length == 3){
            user = args[0];
            repo = args[1];
            branch = args[2];
        }

        if(task != null && !task.isCancelled() && !this.isCancelled()){
            sendMessage(sender, "別の処理が動作しているようです。強制終了は /loadplugin stop で行えます。 ");
            return true;
        }

        sendMessage(sender, "処理を開始します。");
        task = this.runTaskAsynchronously(Main.getJavaPlugin());
        return true;
    }

    @Override
    public void run() {
        Bukkit.getServer().sendMessage(Component.text(this.player.getName() + " によって " + user + "/" + repo + " リポジトリのクローン・ビルドが開始されました。", NamedTextColor.RED));

        if(!user.equals("jaoafa") || !repo.equals("MyMaid4")){
            JSONObject info = getRepo(user, repo);

            if(info == null){
                task = null;
                return;
            }

            if(!info.getBoolean("fork")){
                sendMessage(sender, "指定されたリポジトリ「" + user + "/" + repo + "」はフォークされたリポジトリではありません。");
                task = null;
                return;
            }

            if(!info.getJSONObject("source").getString("full_name").equals("jaoafa/MyMaid4")){
                sendMessage(sender, "指定されたリポジトリ「" + user + "/" + repo + "」はjaoafa/MyMaid4からフォークされたリポジトリではありません。");
                task = null;
                return;
            }
        }
        JSONObject branchInfo = getRepoBranch(user, repo, branch);
        if(branchInfo == null){
            task = null;
            return;
        }
        String sha = branchInfo.getJSONObject("commit").getString("sha");
        sender.sendMessage(Component.text().append(
            Component.text("[LoadPlugin]"),
            Component.space(),
            Component.text("最終コミット:", NamedTextColor.GREEN),
            Component.space(),
            Component.text(sha.substring(0, 7), NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl("https://github.com/" + user + "/" + repo + "/commit/" + sha))
        ));

        // 作業ディレクトリを削除
        Path dir = Paths.get("/papermc/work/");
        try(Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS))
        {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(path -> player.sendActionBar(Component.text(
                    path.getAbsolutePath() + ": " + (path.delete() ? "成功" : "失敗"
                    ))));
            boolean isDeletedDir = dir.toFile().delete();
            sendMessage(sender, "作業ディレクトリの削除に " + (isDeletedDir ? "成功" : "失敗") + " しました。");
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        // クローン
        File cloneDir = new File("/papermc/work/" + user + "/" + repo + "/");
        if(!cloneDir.exists()){
            boolean isCreatedDir = cloneDir.mkdirs();
            sendMessage(sender, "cloneDirディレクトリの作成に " + (isCreatedDir ? "成功" : "失敗") + " しました。");
            if(!isCreatedDir){
                task = null;
                return;
            }
        }

        String githubUrl = "https://github.com/" + user + "/" + repo;
        boolean cloneBool = runCommand(cloneDir, String.format("git clone %s .", githubUrl));
        sendMessage(sender, "GitHubからのクローンに " + (cloneBool ? "成功" : "失敗") + " しました。");
        if(!cloneBool){
            task = null;
            return;
        }

        // ビルド
        boolean buildBool = runCommand(cloneDir, "mvn -Dmaven.repo.local=/papermc/mvnrepositorys clean package");
        sendMessage(sender, "Mavenのビルドに " + (buildBool ? "成功" : "失敗") + " しました。");
        if(!buildBool){
            task = null;
            return;
        }

        // プラグインコピー
        Optional<File> file = Arrays.stream(new File(cloneDir, "target").listFiles())
            .filter(_file -> _file.getName().endsWith(".jar")).max(Comparator.comparingLong(File::length));
        if(!file.isPresent()){
            sendMessage(sender, "jarファイルが見つかりませんでした。");
            task = null;
            return;
        }
        File from = file.get();
        //File from = new File(cloneDir, "target/MyMaid4-1.0.jar");
        sendMessage(sender, String.format("プラグインファイル: %s", from.getName()));

        File to = new File("/papermc/plugins/", from.getName());
        if(to.exists()){
            boolean isDeleted = to.delete();
            sendMessage(sender, "古いプラグインファイルの削除に " + (isDeleted ? "成功" : "失敗") + " しました。");
        }

        try {
            Files.copy(from.toPath(), to.toPath());
            sendMessage(sender, "プラグインファイルのコピーに 成功 しました。");
        } catch (IOException e) {
            sendMessage(sender, "プラグインファイルのコピーに 失敗 しました。");
            task = null;
            return;
        }

        // 作業ディレクトリを削除
        Path workdir = Paths.get("/papermc/work/");
        try(Stream<Path> walk = Files.walk(workdir, FileVisitOption.FOLLOW_LINKS))
        {
            boolean isDeletedDir = walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .allMatch(path -> {
                    player.sendActionBar(Component.text(
                        path.getAbsolutePath() + ": " + (path.delete() ? "成功" : "失敗"
                        )));
                    return path.delete();
                });
            sendMessage(sender, "作業ディレクトリの削除に " + (isDeletedDir ? "成功" : "失敗") + " しました。");
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        // リロード
        Bukkit.getServer().sendMessage(Component.text("プラグイン再読み込みのため、アップデートします。", NamedTextColor.RED));
        task = null;
        Bukkit.reload();
    }

    boolean runCommand(File currentDir, String command){
        try {
            Process p;
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(command.split(" "));
            pb.directory(currentDir);
            pb.redirectErrorStream(true);
            p = pb.start();
            new Thread(() -> {
                InputStream is = p.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                while(true) {
                    String line = null;
                    try {
                        line = br.readLine();
                    } catch (IOException e) {
                        sendMessage(sender, "処理に失敗しました: " + e.getClass().getName() + " | " + e.getMessage());
                        e.printStackTrace();
                    }
                    if(line == null) {
                        break;
                    }
                    System.out.println(line);
                    this.player.sendActionBar(Component.text(line));
                }
            }).start();

            boolean end = p.waitFor(10, TimeUnit.MINUTES);
            if (end) {
                return p.exitValue() == 0;
            } else {
                sendMessage(sender, "処理がタイムアウトしました。");
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            sendMessage(sender, "処理に失敗しました: " + e.getClass().getName() + " | " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    JSONObject getRepo(String user, String repo){
        try {
            String url = String.format("https://api.github.com/repos/%s/%s", user, repo);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).get().build();
            JSONObject obj;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    sendMessage(sender, String.format("リポジトリ情報を取得できませんでした: %d", response.code()));
                    return null;
                }
                obj = new JSONObject(Objects.requireNonNull(response.body()).string());
            }
            return obj;
        }catch(IOException e){
            sendMessage(sender, String.format("リポジトリ情報を取得できませんでした: %s", e.getMessage()));
            e.printStackTrace();
            return null;
        }
    }
    JSONObject getRepoBranch(String user, String repo, String branch){
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches/%s", user, repo, branch);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).get().build();
            JSONObject obj;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    sendMessage(sender, String.format("ブランチ情報を取得できませんでした: %d", response.code()));
                    return null;
                }
                obj = new JSONObject(Objects.requireNonNull(response.body()).string());
            }

            return obj;
        }catch(IOException e){
            sendMessage(sender, String.format("ブランチ情報を取得できませんでした: %s", e.getMessage()));
            e.printStackTrace();
            return null;
        }
    }

    void sendMessage(CommandSender sender, String message){
        sender.sendMessage("[LoadPlugin] " + ChatColor.GREEN + message);
    }
}
