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
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestartTask extends BukkitRunnable {
    Player player;
    String user;
    String repo;
    String branch;

    public RestartTask(Player player, String user, String repo, String branch) {
        this.player = player;
        this.user = user;
        this.repo = repo;
        this.branch = branch;
    }

    @Override
    public void run() {
        Bukkit.getServer().sendMessage(Component.text(this.player.getName() + " によって " + user + "/" + repo + " リポジトリのクローン・ビルドが開始されました。", NamedTextColor.RED));

        if (!user.equals("jaoafa") || !repo.equals("MyMaid4")) {
            JSONObject info = getRepo(user, repo);

            if (info == null) {
                this.cancel();
                return;
            }

            if (!info.getBoolean("fork")) {
                sendMessage(player, "指定されたリポジトリ「" + user + "/" + repo + "」はフォークされたリポジトリではありません。");
                this.cancel();
                return;
            }

            if (!info.getJSONObject("source").getString("full_name").equals("jaoafa/MyMaid4")) {
                sendMessage(player, "指定されたリポジトリ「" + user + "/" + repo + "」はjaoafa/MyMaid4からフォークされたリポジトリではありません。");
                this.cancel();
                return;
            }
        }
        JSONObject branchInfo = getRepoBranch(user, repo, branch);
        if (branchInfo == null) {
            this.cancel();
            return;
        }
        String sha = branchInfo.getJSONObject("commit").getString("sha");
        player.sendMessage(Component.text().append(
            Component.text("[LoadPlugin]"),
            Component.space(),
            Component.text("最終コミット:", NamedTextColor.GREEN),
            Component.space(),
            Component.text(sha.substring(0, 7), NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl("https://github.com/" + user + "/" + repo + "/commit/" + sha))
        ));

        // 作業ディレクトリを削除
        Path dir = Paths.get("/papermc/work/");
        try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(path -> player.sendActionBar(Component.text(
                    path.getAbsolutePath() + ": " + (path.delete() ? "成功" : "失敗"
                    ))));
            boolean isDeletedDir = dir.toFile().delete();
            sendMessage(player, "作業ディレクトリの削除に " + (isDeletedDir ? "成功" : "失敗") + " しました。");
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        // クローン
        File cloneDir = new File("/papermc/work/" + user + "/" + repo + "/");
        if (!cloneDir.exists()) {
            boolean isCreatedDir = cloneDir.mkdirs();
            sendMessage(player, "cloneDirディレクトリの作成に " + (isCreatedDir ? "成功" : "失敗") + " しました。");
            if (!isCreatedDir) {
                this.cancel();
                return;
            }
        }

        String githubUrl = "https://github.com/" + user + "/" + repo;
        boolean cloneBool = runCommand(cloneDir, String.format("git clone %s .", githubUrl));
        sendMessage(player, "GitHubからのクローンに " + (cloneBool ? "成功" : "失敗") + " しました。");
        if (!cloneBool) {
            this.cancel();
            return;
        }

        // チェックアウト
        boolean checkoutBool = runCommand(cloneDir, String.format("git checkout %s", branch));
        sendMessage(player, "ブランチチェックアウトに " + (checkoutBool ? "成功" : "失敗") + " しました。");
        if (!checkoutBool) {
            this.cancel();
            return;
        }

        // ビルド
        boolean buildBool = runCommand(cloneDir, "mvn -Dmaven.repo.local=/papermc/mvnrepositorys clean package");
        sendMessage(player, "Mavenのビルドに " + (buildBool ? "成功" : "失敗") + " しました。");
        if (!buildBool) {
            this.cancel();
            return;
        }

        // プラグインコピー
        File[] files = new File(cloneDir, "target").listFiles();
        if (files == null) {
            sendMessage(player, "targetディレクトリのファイルをリストアップできませんでした。");
            this.cancel();
            return;
        }
        Optional<File> file = Arrays.stream(files)
            .filter(_file -> _file.getName().endsWith(".jar")).max(Comparator.comparingLong(File::length));
        if (!file.isPresent()) {
            sendMessage(player, "jarファイルが見つかりませんでした。");
            this.cancel();
            return;
        }
        File from = file.get();
        //File from = new File(cloneDir, "target/MyMaid4-1.0.jar");
        sendMessage(player, String.format("プラグインファイル: %s", from.getName()));

        File to = new File("/papermc/plugins/", from.getName());
        if (to.exists()) {
            boolean isDeleted = to.delete();
            sendMessage(player, "古いプラグインファイルの削除に " + (isDeleted ? "成功" : "失敗") + " しました。");
        }

        try {
            Files.copy(from.toPath(), to.toPath());
            sendMessage(player, "プラグインファイルのコピーに 成功 しました。");
        } catch (IOException e) {
            sendMessage(player, "プラグインファイルのコピーに 失敗 しました。");
            return;
        }

        // 作業ディレクトリを削除
        Path workdir = Paths.get("/papermc/work/");
        try (Stream<Path> walk = Files.walk(workdir, FileVisitOption.FOLLOW_LINKS)) {
            List<File> missDeletes = walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .filter(path -> {
                    player.sendActionBar(Component.text(
                        path.getAbsolutePath() + ": " + (path.delete() ? "成功" : "失敗"
                        )));
                    return path.delete();
                })
                .collect(Collectors.toList());
            sendMessage(player, "作業ディレクトリの削除に " + (missDeletes.isEmpty() ? "成功" : "失敗") + " しました。");
            System.out.println("MissDeletes:");
            System.out.println(missDeletes.stream().map(File::getPath).collect(Collectors.joining("\n")));
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        // リロード
        Bukkit.getServer().sendMessage(Component.text("プラグイン再読み込みのため、アップデートします。", NamedTextColor.RED));
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("playerName", player.getName());
        yml.set("user", user);
        yml.set("repo", repo);
        yml.set("branch", branch);
        yml.set("time", System.currentTimeMillis());
        try {
            yml.save(Main.stateSaveTo);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bukkit.reload();
    }

    boolean runCommand(File currentDir, String command) {
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
                while (true) {
                    String line = null;
                    try {
                        line = br.readLine();
                    } catch (IOException e) {
                        sendMessage(player, "処理に失敗しました: " + e.getClass().getName() + " | " + e.getMessage());
                        e.printStackTrace();
                    }
                    if (line == null) {
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
                sendMessage(player, "処理がタイムアウトしました。");
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            sendMessage(player, "処理に失敗しました: " + e.getClass().getName() + " | " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    JSONObject getRepo(String user, String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s", user, repo);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).get().build();
            JSONObject obj;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    sendMessage(player, String.format("リポジトリ情報を取得できませんでした: %d", response.code()));
                    return null;
                }
                obj = new JSONObject(Objects.requireNonNull(response.body()).string());
            }
            return obj;
        } catch (IOException e) {
            sendMessage(player, String.format("リポジトリ情報を取得できませんでした: %s", e.getMessage()));
            e.printStackTrace();
            return null;
        }
    }

    JSONObject getRepoBranch(String user, String repo, String branch) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches/%s", user, repo, branch);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).get().build();
            JSONObject obj;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    sendMessage(player, String.format("ブランチ情報を取得できませんでした: %d", response.code()));
                    return null;
                }
                obj = new JSONObject(Objects.requireNonNull(response.body()).string());
            }

            return obj;
        } catch (IOException e) {
            sendMessage(player, String.format("ブランチ情報を取得できませんでした: %s", e.getMessage()));
            e.printStackTrace();
            return null;
        }
    }

    void sendMessage(CommandSender player, String message) {
        player.sendMessage("[LoadPlugin] " + ChatColor.GREEN + message);
    }
}
