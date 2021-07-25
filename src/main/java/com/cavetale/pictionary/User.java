package com.cavetale.pictionary;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class User {
    final UUID uuid;
    String name;
    int score;
    long lastDrawTime;

    public User(final UUID uuid, final String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public User(final Player player) {
        this(player.getUniqueId(), player.getName());
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }
}
