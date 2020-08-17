package com.cavetale.pictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class State {
    String worldName = "";
    Cuboid canvas = Cuboid.ZERO;
    Map<UUID, User> users = new HashMap<>();
    Phase phase = Phase.IDLE;
    int playTicks = 0;
    String secretPhrase = "";
    String publicPhrase = "";
    UUID drawerUuid = null;

    public enum Phase {
        IDLE,
        PLAY;
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
        switch (phase) {
        case IDLE: return;
        case PLAY: tickPlay(); break;
        default: break;
        }
    }

    void tickPlay() {
        playTicks += 1;
        if (playTicks % 800 == 0) {
            solveOneLetter();
        }
        if (playTicks % 10 == 0) {
            Player drawer = getDrawer();
            if (drawer != null) {
                drawer.sendActionBar(ChatColor.GREEN + "Secret: " + ChatColor.WHITE + secretPhrase);
            }
        }
        Player drawer = getDrawer();
        if (drawer != null) {
            if (!drawer.isFlying() && drawer.isSneaking()) {
                draw(drawer, false);
            }
        }
    }

    void solveOneLetter() {
        Random random = ThreadLocalRandom.current();
        int index = -1;
        int found = 0;
        for (int i = 0; i < publicPhrase.length(); i += 1) {
            char c = publicPhrase.charAt(i);
            if (c != '_') continue;
            if (found > 0 && random.nextInt(found) != 0) continue;
            index = i;
            found += 1;
        }
        if (index >= 0) {
            char[] chars = publicPhrase.toCharArray();
            chars[index] = secretPhrase.charAt(index);
            publicPhrase = new String(chars);
        }
    }

    public boolean isDrawer(Player player) {
        return player.getUniqueId().equals(drawerUuid);
    }

    void startGame(Player drawer, String phrase) {
        userOf(drawer); // create
        drawerUuid = drawer.getUniqueId();
        phase = Phase.PLAY;
        playTicks = 0;
        secretPhrase = phrase;
        publicPhrase = phrase.replaceAll("[^ ]", "_");
        for (Player target : getWorld().getPlayers()) {
            target.sendTitle(ChatColor.GREEN + "Start",
                             ChatColor.GREEN + drawer.getName() + " is on");
            target.sendMessage(ChatColor.GREEN + "Start! " + drawer.getName() + " is on");
            target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.2f, 2.0f);
        }
        clearCanvas();
    }

    public Player getDrawer() {
        if (drawerUuid == null) return null;
        return Bukkit.getPlayer(drawerUuid);
    }

    public void endGame() {
        phase = Phase.IDLE;
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
        block.setType(to);
        if (thick) {
            for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.UP, BlockFace.DOWN)) {
                Block b = block.getRelative(face);
                if (canvas.contains(b)) {
                    b.setType(to);
                }
            }
        }
    }
}
