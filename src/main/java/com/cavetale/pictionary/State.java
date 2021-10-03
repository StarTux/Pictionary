package com.cavetale.pictionary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

public final class State {
    String worldName = "";
    Cuboid canvas = Cuboid.ZERO;
    Map<UUID, User> users = new HashMap<>();
    Set<UUID> guessedRight = new HashSet<>();
    Phase phase = Phase.IDLE;
    int playTicks = 0;
    int endTicks = 0;
    String secretPhrase = "";
    String publicPhrase = "";
    UUID drawerUuid = null;
    long lastDrawTime = 0;
    Vec3i lastDrawBlock = null;
    int totalTimeInTicks;
    int ticksLeft;
    int ticksUntilReveal;
    int guessPoints = 3;
    List<String> wordList = new ArrayList<>();
    transient BossBar bossBar;
    public static final int TICKS_PER_LETTER = 300;
    private int ticksPerReveal = 500;
    boolean event;

    public enum Phase {
        IDLE,
        PLAY,
        END;
    }

    public User userOf(Player player) {
        return users.computeIfAbsent(player.getUniqueId(), u -> new User(player));
    }

    public User userOf(UUID uuid) {
        return users.get(uuid);
    }

    public void setCanvas(World world, Cuboid cuboid) {
        this.canvas = cuboid;
        this.worldName = world.getName();
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public boolean isIn(World world) {
        return worldName.equals(world.getName());
    }

    void tick() {
        if (getWorld() == null) {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
            return;
        }
        switch (phase) {
        case IDLE: return;
        case PLAY: tickPlay(); break;
        case END: tickEnd(); break;
        default: break;
        }
    }

    void tickPlay() {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(ChatColor.WHITE + publicPhrase, BarColor.PURPLE, BarStyle.SOLID);
        } else {
            bossBar.setTitle(ChatColor.WHITE + publicPhrase);
        }
        for (Player player : getWorld().getPlayers()) {
            bossBar.addPlayer(player);
        }
        for (Player player : bossBar.getPlayers()) {
            if (!player.isValid() || !player.getWorld().equals(getWorld())) {
                bossBar.removePlayer(player);
            }
        }
        if (playTicks > 0) {
            if (ticksUntilReveal > 0) {
                ticksUntilReveal -= 1;
            } else {
                solveOneLetter();
                ticksUntilReveal = ticksPerReveal;
            }
        }
        if (playTicks % 10 == 0) {
            Player drawer = getDrawer();
            if (drawer != null) {
                drawer.sendActionBar(Component.join(JoinConfiguration.noSeparators(),
                                                    Component.text("Secret: ", NamedTextColor.GRAY),
                                                    Component.text(secretPhrase, NamedTextColor.WHITE)));
            }
        }
        if (ticksLeft <= 0 || getDrawer() == null) {
            for (Player target : getWorld().getPlayers()) {
                target.sendMessage(Component.text("\nTime's up! The word was: " + secretPhrase + "\n", NamedTextColor.RED));
            }
            endGame();
            return;
        }
        if (!guessedRight.isEmpty()) {
            int notGuessed = 0;
            for (Player player : getEligiblePlayers()) {
                if (player.isPermissionSet("group.streamer") && player.hasPermission("group.streamer")) {
                    continue;
                }
                if (!isDrawer(player) && !guessedRight.contains(player.getUniqueId())) notGuessed += 1;
            }
            if (notGuessed == 0) {
                for (Player target : getWorld().getPlayers()) {
                    target.sendMessage(Component.text("\nEverybody guessed the word: " + secretPhrase + "\n", NamedTextColor.GREEN));
                    target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.2f, 2.0f);
                }
                endGame();
                return;
            }
        }
        if (totalTimeInTicks > 0) {
            double progress = totalTimeInTicks > 0
                ? (double) ticksLeft / (double) totalTimeInTicks
                : 0;
            bossBar.setProgress(Math.min(1, Math.max(0, progress)));
        }
        ticksLeft -= 1;
        playTicks += 1;
    }

    void tickEnd() {
        endTicks += 1;
        if (endTicks >= 400) {
            startNewGame();
            return;
        }
    }

    void startNewGame() {
        Random random = ThreadLocalRandom.current();
        List<Player> eligible = getEligiblePlayers();
        if (eligible.isEmpty()) return;
        eligible.sort((a, b) -> Long.compare(userOf(a).lastDrawTime, userOf(b).lastDrawTime));
        long min = userOf(eligible.get(0)).lastDrawTime;
        while (userOf(eligible.get(eligible.size() - 1)).lastDrawTime > min) {
            eligible.remove(eligible.size() - 1);
        }
        Collections.shuffle(eligible, random);
        Player player = eligible.get(0);
        startGame(player);
    }

    void solveOneLetter() {
        Random random = ThreadLocalRandom.current();
        int index = -1;
        int found = 0;
        for (int i = 0; i < publicPhrase.length(); i += 1) {
            char c = publicPhrase.charAt(i);
            if (c != '_') continue;
            found += 1;
            if (random.nextInt(found) != 0) continue;
            index = i;
        }
        if (index >= 0) {
            char[] chars = publicPhrase.toCharArray();
            chars[index] = secretPhrase.charAt(index);
            publicPhrase = new String(chars);
        } else {
            for (Player target : getWorld().getPlayers()) {
                target.sendMessage(Component.text("\nTime's up! The word was: " + secretPhrase + "\n", NamedTextColor.RED));
            }
            endGame();
        }
    }

    public boolean isDrawer(Player player) {
        return player.getUniqueId().equals(drawerUuid);
    }

    void startGame(Player player) {
        Random random = ThreadLocalRandom.current();
        if (wordList.isEmpty()) {
            wordList = PictionaryPlugin.instance.getWordList();
            Collections.shuffle(wordList);
            PictionaryPlugin.instance.getLogger().info(wordList.size() + " words loaded from disk");
        }
        String word = wordList.remove(0);
        startGame(player, word);
    }

    void startGame(Player drawer, String phrase) {
        userOf(drawer).lastDrawTime = System.currentTimeMillis(); // create
        drawerUuid = drawer.getUniqueId();
        phase = Phase.PLAY;
        playTicks = 0;
        secretPhrase = phrase;
        publicPhrase = phrase.replaceAll("[^ ]", "_");
        Title title = Title.title(Component.text().color(NamedTextColor.GREEN).append(drawer.displayName()).build(),
                                  Component.text("It's your turn!", NamedTextColor.GREEN),
                                  Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO));
        for (Player target : getWorld().getPlayers()) {
            target.showTitle(title);
            target.sendMessage(Component.text()
                               .append(Component.text("\nIt's "))
                               .append(drawer.displayName())
                               .append(Component.text("'s turn!\n"))
                               .color(NamedTextColor.GREEN));
            target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.2f, 2.0f);
        }
        if (drawer.getGameMode() == GameMode.CREATIVE) {
            drawer.getInventory().setItem(0, new ItemStack(Material.BLACK_DYE));
            drawer.getInventory().setItem(1, new ItemStack(Material.WHITE_DYE));
            drawer.getInventory().setItem(2, new ItemStack(Material.RED_DYE));
            drawer.getInventory().setItem(3, new ItemStack(Material.LIME_DYE));
            drawer.getInventory().setItem(4, new ItemStack(Material.LIGHT_BLUE_DYE));
            drawer.getInventory().setItem(5, new ItemStack(Material.YELLOW_DYE));
            drawer.getInventory().setItem(6, new ItemStack(Material.ORANGE_DYE));
            drawer.getInventory().setItem(7, new ItemStack(Material.PURPLE_DYE));
            drawer.getInventory().setItem(8, new ItemStack(Material.PINK_DYE));
            drawer.getInventory().setItem(9, new ItemStack(Material.LIGHT_GRAY_DYE));
            drawer.getInventory().setItem(10, new ItemStack(Material.GRAY_DYE));
            drawer.getInventory().setItem(11, new ItemStack(Material.GREEN_DYE));
            drawer.getInventory().setItem(12, new ItemStack(Material.BROWN_DYE));
            drawer.getInventory().setItem(13, new ItemStack(Material.CYAN_DYE));
            drawer.getInventory().setItem(14, new ItemStack(Material.MAGENTA_DYE));
            drawer.getInventory().setItem(15, new ItemStack(Material.BLUE_DYE));
        }
        clearCanvas();
        lastDrawBlock = null;
        lastDrawTime = 0L;
        final int phraseLength = phrase.replace(" ", "").length();
        totalTimeInTicks = Math.min(15, phraseLength) * TICKS_PER_LETTER;
        ticksPerReveal = totalTimeInTicks / phraseLength;
        int warmupTicks = 600;
        totalTimeInTicks += warmupTicks;
        ticksUntilReveal = ticksPerReveal + warmupTicks;
        ticksLeft = totalTimeInTicks + 20;
        guessedRight.clear();
        guessPoints = 3;
    }

    public Player getDrawer() {
        if (drawerUuid == null) return null;
        Player player = Bukkit.getPlayer(drawerUuid);
        if (player == null) return null;
        if (!player.getWorld().getName().equals(worldName)) return null;
        return player;
    }

    public void endGame() {
        phase = Phase.END;
        endTicks = 0;
        publicPhrase = secretPhrase;
    }

    public void stop() {
        cleanUp();
        phase = Phase.IDLE;
    }

    public void cleanUp() {
        if (bossBar != null) bossBar.removeAll();
    }

    public void clearCanvas() {
        World world = getWorld();
        for (int y = canvas.ay; y <= canvas.by; y += 1) {
            for (int z = canvas.az; z <= canvas.bz; z += 1) {
                for (int x = canvas.ax; x <= canvas.bx; x += 1) {
                    world.getBlockAt(x, y, z).setType(Material.WHITE_CONCRETE);
                }
            }
        }
    }

    public List<User> rankScore() {
        return new ArrayList<>(users.values()).stream()
            .sorted((a, b) -> Integer.compare(b.score, a.score))
            .collect(Collectors.toList());
    }

    void draw(Player player, boolean thick) {
        Block block = player.getTargetBlock(100);
        if (block == null) return;
        if (!canvas.contains(block)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;
        Material mat = item.getType();
        String name = mat.name();
        if (!name.endsWith("_DYE")) return;
        name = name.substring(0, name.length() - 4);
        Material to;
        try {
            to = Material.valueOf(name + "_CONCRETE");
        } catch (IllegalArgumentException iae) {
            return;
        }
        long now = System.currentTimeMillis();
        long diff = (now - lastDrawTime);
        if (diff < 400L && lastDrawBlock != null) {
            Vector dir = new Vector(lastDrawBlock.x - block.getX(),
                                    lastDrawBlock.y - block.getY(),
                                    lastDrawBlock.z - block.getZ());
            int length = (int) Math.ceil(dir.length());
            if (length == 0) dir = new Vector(0, 1, 0);
            BlockIterator iter = new BlockIterator(getWorld(), block.getLocation().add(0.5, 0.5, 0.5).toVector(), dir, 0.0, length);
            for (int i = 0; i <= length; i += 1) {
                if (!iter.hasNext()) break;
                Block next = iter.next();
                draw(player, next, to, thick);
            }
        } else {
            draw(player, block, to, thick);
        }
        lastDrawTime = now;
        lastDrawBlock = Vec3i.of(block);
    }

    void draw(Player player, Block block, Material mat, boolean thick) {
        if (!canvas.contains(block)) return;
        block.setType(mat);
        if (thick) {
            for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.UP, BlockFace.DOWN)) {
                Block b = block.getRelative(face);
                if (canvas.contains(b)) {
                    b.setType(mat);
                }
            }
        }
    }

    public boolean onGuess(PictionaryPlugin plugin, Player player, String msg) {
        if (!msg.equalsIgnoreCase(secretPhrase)) return false;
        plugin.getServer().getScheduler().runTask(plugin, () -> guessCallback(plugin, player, msg));
        return true;
    }

    void guessCallback(PictionaryPlugin plugin, Player player, String msg) {
        if (!msg.equalsIgnoreCase(secretPhrase)) return;
        if (!isIn(player.getWorld())) return;
        if (phase != State.Phase.PLAY) return;
        if (isDrawer(player)) return;
        // Guessed right!
        if (guessedRight.contains(player.getUniqueId())) return;
        guessedRight.add(player.getUniqueId());
        plugin.getLogger().info("Guessed right: " + player.getName());
        userOf(player).score += guessPoints;
        if (guessPoints > 1) guessPoints -= 1;
        Player drawer = getDrawer();
        userOf(drawer).score += 1;
        for (Player target : getWorld().getPlayers()) {
            target.sendMessage(Component.text()
                               .append(player.displayName())
                               .append(Component.text(" guessed the phrase!"))
                               .color(NamedTextColor.GREEN));
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 2.0f);
        }
        if (event) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + drawer.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
        }
    }

    List<Player> getEligiblePlayers() {
        return getWorld().getPlayers().stream()
            .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
            .collect(Collectors.toList());
    }

    boolean isEligible(Player player) {
        return player.isValid() && player.getWorld().equals(getWorld()) && player.getGameMode() != GameMode.SPECTATOR;
    }
}
