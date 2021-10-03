package com.cavetale.pictionary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

public final class PictionaryPlugin extends JavaPlugin {
    protected static PictionaryPlugin instance;
    private PictionaryCommand pictionaryCommand = new PictionaryCommand(this);
    private EventListener eventListener = new EventListener(this);
    State state; // loaded onEnable
    private File saveFile; // created onEnable

    @Override
    public void onEnable() {
        instance = this;
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

    public List<String> getWordList() {
        File dir = new File(getDataFolder(), "words");
        Map<String, String> map = new HashMap<>();
        for (File file : dir.listFiles()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                while (true) {
                    String line = br.readLine();
                    if (line == null) break;
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.length() < 5) continue;
                    map.put(line.toLowerCase(), line);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return new ArrayList<>(map.values());
    }
}
