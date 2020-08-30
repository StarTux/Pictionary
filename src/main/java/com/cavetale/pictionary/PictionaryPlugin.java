package com.cavetale.pictionary;

import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public final class PictionaryPlugin extends JavaPlugin {
    private PictionaryCommand pictionaryCommand = new PictionaryCommand(this);
    private EventListener eventListener = new EventListener(this);
    State state; // loaded onEnable
    private File saveFile; // created onEnable

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveFile = new File(getDataFolder(), "state.json");
        pictionaryCommand.enable();
        eventListener.enable();
        load();
        getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
    }

    @Override
    public void onDisable() {
        state.cleanUp();
        save();
    }

    void load() {
        state = Json.load(saveFile, State.class, State::new);
    }

    void save() {
        Json.save(saveFile, state, true);
    }

    void tick() {
        state.tick();
    }
}
