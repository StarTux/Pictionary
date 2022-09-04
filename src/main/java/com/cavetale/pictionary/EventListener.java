package com.cavetale.pictionary;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.mytems.Mytems;
import com.winthier.chat.event.ChatPlayerTalkEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
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
                lines.add(join(noSeparators(), text("Your turn!", YELLOW), text(" To draw,", GRAY)));
                lines.add(text("hold a dye and face", GRAY));
                lines.add(text("the canvas", GRAY));
                lines.add(join(noSeparators(),
                               Mytems.MOUSE_LEFT,
                               text(" Broad strokes", GRAY)));
                lines.add(join(noSeparators(),
                               Mytems.MOUSE_RIGHT,
                               text(" Fine strokes", GRAY)));
                lines.add(empty());
                lines.add(join(noSeparators(), text(tiny("secret "), GRAY),
                               text(plugin.state.secretPhrase, YELLOW)));
                lines.add(empty());
            }
            lines.add(text()
                     .append(text("Artist ", GRAY))
                     .append(drawer.displayName())
                     .color(WHITE)
                     .build());
        }
        boolean guessedRight = plugin.state.guessedRight.contains(player.getUniqueId());
        lines.add(Component.join(noSeparators(),
                                text("Your Score ", GRAY),
                                (guessedRight
                                 ? text("" + plugin.state.getScore(player.getUniqueId()) + "\u2714", GREEN)
                                 : text("" + plugin.state.getScore(player.getUniqueId()), WHITE))));
        lines.addAll(plugin.state.highscoreLines);
        event.sidebar(PlayerHudPriority.HIGHEST, lines);
        if (player.equals(drawer)) {
            if (plugin.state.bossBar != null) {
                event.bossbar(PlayerHudPriority.HIGHEST, plugin.state.drawerBossBar);
            }
        } else {
            if (plugin.state.bossBar != null) {
                event.bossbar(PlayerHudPriority.HIGHEST, plugin.state.bossBar);
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onChatPlayerTalk(ChatPlayerTalkEvent event) {
        String msg = event.getMessage();
        Player player = event.getPlayer();
        if (!plugin.state.onGuess(plugin, player, msg)) return;
        final int idx = msg.toLowerCase().indexOf(plugin.state.secretPhrase.toLowerCase());
        if (idx < 0) {
            event.setMessage(plugin.state.publicPhrase.replace("_", "*"));
        } else {
            String newMessage = msg.substring(0, idx)
                + plugin.state.publicPhrase.replace("_", "*")
                + msg.substring(idx + plugin.state.publicPhrase.length());
            event.setMessage(newMessage);
        }
    }
}
