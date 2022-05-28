package com.cavetale.pictionary;

import com.cavetale.core.font.VanillaItems;
import com.cavetale.mytems.Mytems;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class PictionaryPlugin extends JavaPlugin {
    protected static PictionaryPlugin instance;
    private PictionaryCommand pictionaryCommand = new PictionaryCommand(this);
    private EventListener eventListener = new EventListener(this);
    State state; // loaded onEnable
    private File saveFile; // created onEnable
    public static final Component TITLE = join(noSeparators(),
                                               Mytems.RED_PAINTBRUSH.component,
                                               text("C", RED),
                                               text("a", GOLD),
                                               text("v", YELLOW),
                                               text("e", GREEN),
                                               text("p", AQUA),
                                               text("a", DARK_AQUA),
                                               text("i", BLUE),
                                               text("n", LIGHT_PURPLE),
                                               text("t", RED),
                                               VanillaItems.WATER_BUCKET.component);

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
        state.enable();
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
