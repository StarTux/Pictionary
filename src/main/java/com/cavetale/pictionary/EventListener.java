package com.cavetale.pictionary;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.winthier.chat.event.ChatPlayerTalkEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
                list.add(text("Your turn! In order to draw,", GREEN));
                list.add(text("hold a dye in your hand and", GREEN));
                list.add(text("face the canvas", GREEN));
                list.add(Component.join(JoinConfiguration.noSeparators(),
                                        text("Left-click", WHITE),
                                        text(" for broad strokes", WHITE)));
                list.add(Component.join(JoinConfiguration.noSeparators(),
                                        text("Right-click", WHITE),
                                        text(" for fine strokes", WHITE)));
                list.add(Component.empty());
            }
            list.add(text()
                     .append(text("Artist ", GRAY))
                     .append(drawer.displayName())
                     .color(WHITE)
                     .build());
        }
        boolean guessedRight = plugin.state.guessedRight.contains(player.getUniqueId());
        list.add(Component.join(JoinConfiguration.noSeparators(),
                                text("Your Score ", GRAY),
                                (guessedRight
                                 ? text("" + plugin.state.userOf(player).score + "\u2714", GREEN)
                                 : text("" + plugin.state.userOf(player).score, WHITE))));
        List<User> users = plugin.state.rankScore();
        int i = 0;
        for (User user : users) {
            final Player userPlayer = user.getPlayer();
            final Component userName = userPlayer != null
                ? userPlayer.displayName()
                : text(user.name);
            int rank = ++i;
            if (i > 13) break;
            if (user.score == 0) break;
            TextComponent.Builder userLine = text()
                .append(text(user.score + " ", GRAY))
                .append(userName);
            if (user.uuid.equals(plugin.state.drawerUuid)) {
                userLine.color(BLUE);
            } else if (plugin.state.guessedRight.contains(user.uuid)) {
                userLine.color(GOLD);
            } else if (userPlayer != null && plugin.state.isEligible(userPlayer)) {
                userLine.color(WHITE);
            } else {
                userLine.color(DARK_GRAY);
            }
            list.add(userLine.build());
        }
        event.add(plugin, (plugin.state.event ? Priority.HIGHEST : Priority.LOW), list);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onChatPlayerTalk(ChatPlayerTalkEvent event) {
        String msg = event.getMessage();
        Player player = event.getPlayer();
        if (plugin.state.onGuess(plugin, player, msg)) {
            event.setMessage(plugin.state.publicPhrase.replace("_", "*"));
        }
    }
}
