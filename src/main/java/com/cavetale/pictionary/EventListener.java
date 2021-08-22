package com.cavetale.pictionary;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
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
        if (plugin.state.phase == State.Phase.IDLE) return;
        Player player = event.getPlayer();
        if (!plugin.state.isIn(player.getWorld())) return;
        List<Component> list = new ArrayList<>();
        Player drawer = plugin.state.getDrawer();
        if (drawer != null) {
            if (player.equals(drawer)) {
                list.add(Component.text("Your turn! In order to draw,", NamedTextColor.GREEN));
                list.add(Component.text("hold a dye in your hand and", NamedTextColor.GREEN));
                list.add(Component.text("face the canvas", NamedTextColor.GREEN));
                list.add(TextComponent.ofChildren(Component.text("Left-click", NamedTextColor.WHITE),
                                                  Component.text(" for broad strokes", NamedTextColor.WHITE)));
                list.add(TextComponent.ofChildren(Component.text("Right-click", NamedTextColor.WHITE),
                                                  Component.text(" for fine strokes", NamedTextColor.WHITE)));
                list.add(Component.empty());
            }
            list.add(Component.text()
                     .append(Component.text("Artist ", NamedTextColor.GRAY))
                     .append(drawer.displayName())
                     .color(NamedTextColor.WHITE)
                     .build());
        }
        list.add(TextComponent.ofChildren(Component.text("Your Score ", NamedTextColor.GRAY),
                                          Component.text("" + plugin.state.userOf(player).score, NamedTextColor.WHITE)));
        List<User> users = plugin.state.rankScore();
        int i = 0;
        for (User user : users) {
            final Player userPlayer = user.getPlayer();
            final Component userName = userPlayer != null
                ? userPlayer.displayName()
                : Component.text(user.name);
            int rank = ++i;
            if (i > 13) break;
            if (user.score == 0) break;
            TextComponent.Builder userLine = Component.text()
                .append(Component.text("#" + rank + " " + user.score + " "))
                .append(userName);
            if (user.uuid.equals(plugin.state.drawerUuid)) {
                userLine.color(NamedTextColor.BLUE);
            } else if (plugin.state.guessedRight.contains(user.uuid)) {
                userLine.color(NamedTextColor.GOLD);
            } else if (userPlayer != null && plugin.state.isEligible(userPlayer)) {
                userLine.color(NamedTextColor.WHITE);
            } else {
                userLine.color(NamedTextColor.DARK_GRAY);
            }
            list.add(userLine.build());
        }
        event.add(plugin, (plugin.state.event ? Priority.HIGHEST : Priority.LOW), list);
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
