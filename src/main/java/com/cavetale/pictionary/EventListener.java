package com.cavetale.pictionary;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.winthier.chat.event.ChatPlayerTalkEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
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
    public void onPlayerHud(PlayerHudEvent event) {
        if (plugin.state.phase == State.Phase.IDLE) return;
        Player player = event.getPlayer();
        if (!plugin.state.isIn(player.getWorld())) return;
        List<Component> lines = new ArrayList<>();
        Player drawer = plugin.state.getDrawer();
        if (drawer != null) {
            if (player.equals(drawer)) {
                lines.add(text("Your turn! In order to draw,", GREEN));
                lines.add(text("hold a dye in your hand and", GREEN));
                lines.add(text("face the canvas", GREEN));
                lines.add(Component.join(JoinConfiguration.noSeparators(),
                                        text("Left-click", WHITE),
                                        text(" for broad strokes", WHITE)));
                lines.add(Component.join(JoinConfiguration.noSeparators(),
                                        text("Right-click", WHITE),
                                        text(" for fine strokes", WHITE)));
                lines.add(Component.empty());
            }
            lines.add(text()
                     .append(text("Artist ", GRAY))
                     .append(drawer.displayName())
                     .color(WHITE)
                     .build());
        }
        boolean guessedRight = plugin.state.guessedRight.contains(player.getUniqueId());
        lines.add(Component.join(JoinConfiguration.noSeparators(),
                                text("Your Score ", GRAY),
                                (guessedRight
                                 ? text("" + plugin.state.getScore(player.getUniqueId()) + "\u2714", GREEN)
                                 : text("" + plugin.state.getScore(player.getUniqueId()), WHITE))));
        lines.addAll(plugin.state.highscoreLines);
        event.sidebar(PlayerHudPriority.HIGHEST, lines);
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
