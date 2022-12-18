package com.cavetale.pictionary;

import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.destroystokyo.paper.MaterialTags;
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
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class State {
    public static final int TICKS_PER_LETTER = 20 * 20;
    public static final int WARM_UP_TICKS = 20 * 90;
    protected String worldName = "";
    protected Cuboid canvas = Cuboid.ZERO;
    protected Map<UUID, User> users = new HashMap<>();
    protected Map<UUID, Integer> scores = new HashMap<>();
    protected Set<UUID> guessedRight = new HashSet<>();
    protected Phase phase = Phase.IDLE;
    protected int pickTicks = 0;
    protected int playTicks = 0;
    protected int endTicks = 0;
    protected String secretPhrase = "";
    protected String publicPhrase = "";
    protected UUID drawerUuid = null;
    protected long lastDrawTime = 0;
    protected long lastSoundTime = 0;
    protected Vec3i lastDrawBlock = null;
    protected int totalTimeInTicks;
    protected int ticksLeft;
    protected int ticksUntilReveal;
    protected List<String> wordList = new ArrayList<>();
    protected List<String> wordChoices = new ArrayList<>();
    protected transient BossBar bossBar;
    protected transient BossBar drawerBossBar;
    protected int ticksPerReveal = 500;
    protected boolean event;
    protected transient List<Highscore> highscore = List.of();
    protected transient List<Component> highscoreLines = List.of();

    protected void enable() {
        computeHighscore();
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

    protected void tick() {
        if (getWorld() == null) return;
        if (bossBar == null) {
            bossBar = BossBar.bossBar(empty(), 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        }
        if (drawerBossBar == null) {
            drawerBossBar = BossBar.bossBar(empty(), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        }
        switch (phase) {
        case IDLE: return;
        case PICK: tickPick(); break;
        case PLAY: tickPlay(); break;
        case END: tickEnd(); break;
        default: break;
        }
    }

    private void tickPick() {
        bossBar.name(text("Getting Ready", GRAY));
        drawerBossBar.name(text("Pick a Word", YELLOW, BOLD));
        final int totalTicks = 20 * 20;
        final int remainingTicks = totalTicks - pickTicks;
        if (remainingTicks < 0) {
            startNewGame();
            return;
        }
        final float progress = Math.max(0.0f, Math.min(1.0f, (float) remainingTicks / (float) totalTicks));
        bossBar.progress(progress);
        drawerBossBar.progress(progress);
        pickTicks += 1;
    }

    private void tickPlay() {
        bossBar.name(text(publicPhrase, WHITE));
        drawerBossBar.name(join(noSeparators(), text(tiny("draw this: "), GRAY), text(secretPhrase, YELLOW)));
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
        }
        if (ticksLeft <= 0 || getDrawer() == null) {
            for (Player target : getWorld().getPlayers()) {
                target.sendMessage(text("\nTime's up! The word was: " + secretPhrase + "\n", RED));
            }
            if (event) rewardDrawer();
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
                    target.sendMessage(text("\nEverybody guessed the word: " + secretPhrase + "\n", GREEN));
                    target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.2f, 2.0f);
                }
                if (event) rewardDrawer();
                endGame();
                return;
            }
        }
        if (totalTimeInTicks > 0) {
            float progress = totalTimeInTicks > 0
                ? (float) ticksLeft / (float) totalTimeInTicks
                : 0.0f;
            bossBar.progress(Math.min(1.0f, Math.max(0.0f, progress)));
            drawerBossBar.progress(Math.min(1.0f, Math.max(0.0f, progress)));
        }
        ticksLeft -= 1;
        playTicks += 1;
    }

    private void tickEnd() {
        endTicks += 1;
        if (endTicks >= 400) {
            startNewGame();
            return;
        }
    }

    protected void startNewGame() {
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

    private void solveOneLetter() {
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
                target.sendMessage(text("\nTime's up! The word was: " + secretPhrase + "\n", RED));
            }
            if (event) rewardDrawer();
            endGame();
        }
    }

    public boolean isDrawer(Player player) {
        return player.getUniqueId().equals(drawerUuid);
    }

    /**
     * Prepare and enter the PICK phase.
     */
    protected void startGame(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.MASTER, 1.5f, 1.5f);
        userOf(player).lastDrawTime = System.currentTimeMillis();
        Random random = ThreadLocalRandom.current();
        wordChoices = new ArrayList<>();
        for (int i = 0; i < 3; i += 1) {
            if (wordList.isEmpty()) {
                wordList = PictionaryPlugin.instance.getWordList();
                Collections.shuffle(wordList);
                PictionaryPlugin.instance.getLogger().info(wordList.size() + " words loaded from disk");
            }
            String phrase = wordList.remove(wordList.size() - 1);
            phrase = toCamelCase(" ", List.of(phrase.split(" ")));
            wordChoices.add(phrase);
        }
        drawerUuid = player.getUniqueId();
        phase = Phase.PICK;
        pickTicks = 0;
        openPickBook(player);
    }

    protected void openPickBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(m -> {
                if (m instanceof BookMeta meta) {
                    meta.author(text("Cavetale"));
                    meta.title(text("Cavepaint"));
                    List<Component> lines = new ArrayList<>();
                    lines.add(text("Choose a Phrase to Draw", DARK_BLUE));
                    int i = 0;
                    for (String word : wordChoices) {
                        final int index = i++;
                        lines.add(empty());
                        lines.add(join(noSeparators(), text((index + 1) + ") ", GRAY), text(word, BLUE, UNDERLINED))
                                  .hoverEvent(showText(text(word, GRAY)))
                                  .clickEvent(runCommand("/cavepaint word " + index)));
                    }
                    meta.pages(List.of(join(separator(newline()), lines)));
                }
            });
        player.openBook(book);
        player.sendMessage(text("Please pick a word. Click here if you closed the book by accident", GREEN, BOLD)
                           .hoverEvent(showText(text("Choose a Phrase to Draw", GRAY)))
                           .clickEvent(runCommand("/cavepaint open")));
    }

    protected void pickWord(Player player, int index) {
        if (phase != Phase.PICK || !isDrawer(player) || index < 0 || index >= wordChoices.size()) {
            return;
        }
        startGame(player, wordChoices.get(index));
    }

    /**
     * Switch to the PLAY phase and load the word.
     */
    protected void startGame(Player drawer, String phrase) {
        PictionaryPlugin.instance.getLogger().info("New drawer: " + drawer.getName() + ", " + phrase);
        drawerUuid = drawer.getUniqueId();
        phase = Phase.PLAY;
        playTicks = 0;
        secretPhrase = phrase;
        publicPhrase = phrase.replaceAll("[^ ]", "_");
        Title title = Title.title(text().color(GREEN).append(drawer.displayName()).build(),
                                  text("It's your turn!", GREEN),
                                  Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO));
        for (Player target : getWorld().getPlayers()) {
            target.showTitle(title);
            target.sendMessage(text()
                               .append(text("\nIt's "))
                               .append(drawer.displayName())
                               .append(text("'s turn!\n"))
                               .color(GREEN));
            target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.2f, 2.0f);
        }
        if (drawer.getGameMode() == GameMode.CREATIVE) {
            int index = 0;
            drawer.getInventory().setItem(index++, Mytems.BLIND_EYE.createItemStack());
            for (Material dye : List.of(Material.BLACK_DYE,
                                        Material.WHITE_DYE,
                                        Material.RED_DYE,
                                        Material.LIME_DYE,
                                        Material.LIGHT_BLUE_DYE,
                                        Material.YELLOW_DYE,
                                        Material.ORANGE_DYE,
                                        Material.PURPLE_DYE,
                                        Material.PINK_DYE,
                                        Material.LIGHT_GRAY_DYE,
                                        Material.GRAY_DYE,
                                        Material.GREEN_DYE,
                                        Material.BROWN_DYE,
                                        Material.CYAN_DYE,
                                        Material.MAGENTA_DYE,
                                        Material.BLUE_DYE)) {
                drawer.getInventory().setItem(index++, new ItemStack(dye));
            }
        }
        clearCanvas();
        lastDrawBlock = null;
        lastDrawTime = 0L;
        final int phraseLength = phrase.replace(" ", "").length();
        totalTimeInTicks = Math.min(15, phraseLength) * TICKS_PER_LETTER;
        ticksPerReveal = totalTimeInTicks / phraseLength;
        totalTimeInTicks += WARM_UP_TICKS;
        ticksUntilReveal = ticksPerReveal + WARM_UP_TICKS;
        ticksLeft = totalTimeInTicks + 20;
        guessedRight.clear();
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

    private void rewardDrawer() {
        Player drawer = getDrawer();
        if (drawer == null) return;
        String drawerName = drawer.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + drawerName);
        List<String> titles = List.of("Cavepaint", "Pixel");
        String cmd = "titles unlockset " + drawerName + " " + String.join(" ", titles);
        PictionaryPlugin.instance.getLogger().info("Running command: " + cmd);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    public void stop() {
        cleanUp();
        phase = Phase.IDLE;
    }

    public void cleanUp() { }

    public void clearCanvas() {
        World world = getWorld();
        for (int y = canvas.ay; y <= canvas.by; y += 1) {
            for (int z = canvas.az; z <= canvas.bz; z += 1) {
                for (int x = canvas.ax; x <= canvas.bx; x += 1) {
                    Block block = world.getBlockAt(x, y, z);
                    if (MaterialTags.CONCRETES.isTagged(block.getType())) {
                        block.setType(Material.WHITE_CONCRETE);
                    }
                }
            }
        }
    }

    protected void draw(Player player, boolean thick, boolean fill) {
        Block block = player.getTargetBlock(100);
        if (block == null) return;
        if (!canvas.contains(block)) return;
        if (!MaterialTags.CONCRETES.isTagged(block.getType())) return;
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
        if (thick && fill) {
            fill(player, block, block.getType(), to);
            player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, SoundCategory.MASTER, 0.5f, 1.3f);
            lastDrawTime = 0;
            lastDrawBlock = null;
            return;
        }
        long now = System.currentTimeMillis();
        long diff = (now - lastDrawTime);
        boolean doDrawLine = diff < 300L && lastDrawBlock != null;
        if (doDrawLine) {
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
        Vec3i newDrawnBlock = Vec3i.of(block);
        if (now - lastSoundTime > 1000L) {
            lastSoundTime = now;
            if (thick) {
                player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, SoundCategory.MASTER, 0.5f, 1.5f);
            } else if (doDrawLine && !newDrawnBlock.equals(lastDrawBlock)) {
                player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.MASTER, 0.5f, 0.5f);
            }
        }
        lastDrawTime = now;
        lastDrawBlock = newDrawnBlock;
    }

    private void draw(Player player, Block block, Material mat, boolean thick) {
        if (!canvas.contains(block)) return;
        if (!MaterialTags.CONCRETES.isTagged(block.getType())) return;
        block.setType(mat);
        if (thick) {
            for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.UP, BlockFace.DOWN)) {
                Block b = block.getRelative(face);
                if (canvas.contains(b) && MaterialTags.CONCRETES.isTagged(b.getType())) {
                    b.setType(mat);
                }
            }
        }
    }

    private void fill(Player player, Block block, Material from, Material to) {
        if (from == to) return;
        if (!canvas.contains(block)) return;
        if (block.getType() != from) return;
        block.setType(to, false);
        fill(player, block.getRelative(1, 0, 0), from, to);
        fill(player, block.getRelative(-1, 0, 0), from, to);
        fill(player, block.getRelative(0, 1, 0), from, to);
        fill(player, block.getRelative(0, -1, 0), from, to);
        fill(player, block.getRelative(0, 0, 1), from, to);
        fill(player, block.getRelative(0, 0, -1), from, to);
    }

    public boolean onGuess(PictionaryPlugin plugin, Player player, String msg) {
        if (msg == null) return false;
        if (phase != Phase.PLAY) return false;
        if (!msg.toLowerCase().contains(secretPhrase.toLowerCase())) return false;
        plugin.getServer().getScheduler().runTask(plugin, () -> guessCallback(plugin, player, msg));
        return true;
    }

    protected int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    protected void addScore(UUID uuid, int value) {
        scores.put(uuid, Math.max(0, getScore(uuid) + value));
    }

    protected void guessCallback(PictionaryPlugin plugin, Player player, String msg) {
        if (!msg.toLowerCase().contains(secretPhrase.toLowerCase())) return;
        if (!isIn(player.getWorld())) return;
        if (phase != Phase.PLAY) return;
        if (isDrawer(player)) return;
        // Guessed right!
        if (guessedRight.contains(player.getUniqueId())) return;
        guessedRight.add(player.getUniqueId());
        plugin.getLogger().info("Guessed right: " + player.getName());
        int guessPoints = 0;
        for (int i = 0; i < publicPhrase.length(); i += 1) {
            if (publicPhrase.charAt(i) == '_') guessPoints += 1;
        }
        addScore(player.getUniqueId(), Math.max(1, guessPoints));
        Player drawer = getDrawer();
        addScore(drawer.getUniqueId(), 1);
        computeHighscore();
        for (Player target : getWorld().getPlayers()) {
            target.sendMessage(join(noSeparators(),
                                    player.displayName(),
                                    text(" guessed the phrase!", GREEN),
                                    text(" Points: ", GRAY),
                                    text("" + guessPoints, WHITE)));
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 2.0f);
        }
        if (event) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
        }
    }

    private List<Player> getEligiblePlayers() {
        List<Player> result = new ArrayList<>();
        for (Player player : getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) continue;
            if (!player.hasPermission("cavepaint.player")) continue;
            result.add(player);
        }
        return result;
    }

    protected void computeHighscore() {
        highscore = Highscore.of(scores);
        highscoreLines = Highscore.sidebar(highscore);
    }
}
