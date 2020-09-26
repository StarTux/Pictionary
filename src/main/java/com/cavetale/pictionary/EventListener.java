package com.cavetale.pictionary;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
        Player drawer = plugin.state.getDrawer();
        if (drawer != null) {
            list.add(ChatColor.GRAY + "Artist " + ChatColor.WHITE + drawer.getName());
        }
        list.add(ChatColor.GRAY + "Your Score " + ChatColor.WHITE + plugin.state.userOf(player).score);
        List<User> users = plugin.state.rankScore();
        int i = 0;
        for (User user : users) {
            Player other = user.getPlayer();
            int rank = ++i;
            if (i > 13) break;
            if (user.score == 0) break;
            if (user.uuid.equals(plugin.state.drawerUuid)) {
                list.add(ChatColor.RED + "#" + rank + ChatColor.WHITE + " " + user.score + " " + ChatColor.BLUE + user.name);
            } else if (plugin.state.guessedRight.contains(user.uuid)) {
                list.add(ChatColor.RED + "#" + rank + ChatColor.WHITE + " " + user.score + " " + ChatColor.GOLD + user.name);
            } else if (other != null && plugin.state.isEligible(other)) {
                list.add(ChatColor.RED + "#" + rank + ChatColor.WHITE + " " + user.score + " " + ChatColor.GRAY + user.name);
            } else {
                list.add(ChatColor.RED + "#" + rank + ChatColor.DARK_GRAY + " " + user.score + " " + user.name);
            }
        }
        event.addLines(plugin, Priority.HIGHEST, list);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!msg.contains(" ")) return;
        String[] toks = msg.split(" ", 2);
        msg = toks[1];
        Player player = event.getPlayer();
        if (plugin.state.onGuess(plugin, player, msg)) {
            event.setMessage(toks[0] + " " + plugin.state.publicPhrase.replace("_", "*"));
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        String msg = event.getMessage();
        Player player = event.getPlayer();
        if (plugin.state.onGuess(plugin, player, msg)) {
            event.setMessage(plugin.state.publicPhrase.replace("_", "*"));
        }
    }
}
