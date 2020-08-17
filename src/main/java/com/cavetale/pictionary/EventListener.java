package com.cavetale.pictionary;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final PictionaryPlugin plugin;

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        boolean left = false;
        switch (event.getAction()) {
        case LEFT_CLICK_BLOCK:
        case LEFT_CLICK_AIR:
            left = true;
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            break;
        default: return;
        }
        Player player = event.getPlayer();
        if (!plugin.state.isIn(player.getWorld())) return;
        if (!plugin.state.isDrawer(player)) return;
        plugin.state.draw(player, left);
    }

    @EventHandler
    public void onPlayerSidebar(PlayerSidebarEvent event) {
        Player player = event.getPlayer();
        if (!plugin.state.isIn(player.getWorld())) return;
        List<String> list = new ArrayList<>();
        if (plugin.state.phase == State.Phase.PLAY) {
            list.add("" + ChatColor.RED + ChatColor.BOLD + "vvvvvvvvvvvv");
            list.add(ChatColor.WHITE + " " + plugin.state.publicPhrase);
            list.add("" + ChatColor.RED + ChatColor.BOLD + "^^^^^^^^^^^^");
        } else {
            list.add("" + ChatColor.DARK_GRAY + ChatColor.BOLD + "vvvvvvvvvvvv");
            list.add(ChatColor.WHITE + " " + plugin.state.secretPhrase);
            list.add("" + ChatColor.DARK_GRAY + ChatColor.BOLD + "^^^^^^^^^^^^");
        }
        Player drawer = plugin.state.getDrawer();
        if (drawer != null) {
            list.add(ChatColor.GRAY + "Artist " + ChatColor.WHITE + drawer.getName());
        }
        list.add(ChatColor.GRAY + "Your Score " + ChatColor.WHITE + plugin.state.userOf(player).score);
        List<User> users = plugin.state.rankScore();
        int i = 0;
        for (User user : users) {
            int rank = ++i;
            if (i > 5) break;
            if (user.score == 0) break;
            list.add(ChatColor.RED + "#" + rank + ChatColor.WHITE + " " + user.score + " " + ChatColor.GRAY + user.name);
        }
        event.addLines(plugin, Priority.HIGHEST, list);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!msg.contains(" ")) return;
        msg = msg.split(" ", 2)[1];
        Player player = event.getPlayer();
        onGuess(player, msg);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerChat(PlayerChatEvent event) {
        String msg = event.getMessage();
        Player player = event.getPlayer();
        onGuess(player, msg);
    }

    void onGuess(Player player, String msg) {
        if (!plugin.state.isIn(player.getWorld())) return;
        if (plugin.state.phase != State.Phase.PLAY) return;
        if (plugin.state.isDrawer(player)) return;
        if (!msg.equalsIgnoreCase(plugin.state.secretPhrase)) return;
        plugin.getLogger().info("Winner: " + player.getName());
        plugin.state.userOf(player).score += 1;
        plugin.state.userOf(plugin.state.drawerUuid).score += 1;
        for (Player target : plugin.state.getWorld().getPlayers()) {
            target.sendMessage(ChatColor.GREEN + player.getName() + " guessed the phrase: " + plugin.state.secretPhrase);
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 2.0f);
        }
        plugin.state.endGame();
        plugin.save();
    }
}
