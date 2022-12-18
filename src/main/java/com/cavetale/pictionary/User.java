package com.cavetale.pictionary;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class User {
    protected final UUID uuid;
    protected long lastDrawTime;

    public User(final UUID uuid) {
        this.uuid = uuid;
    }

    public User(final Player player) {
        this(player.getUniqueId());
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }
}
