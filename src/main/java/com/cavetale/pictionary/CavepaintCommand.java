package com.cavetale.pictionary;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import org.bukkit.entity.Player;

public final class CavepaintCommand extends AbstractCommand<PictionaryPlugin> {
    protected CavepaintCommand(final PictionaryPlugin plugin) {
        super(plugin, "cavepaint");
        rootNode.addChild("word").arguments("<index>")
            .hidden(true)
            .description("Pick word")
            .playerCaller(this::word);
        rootNode.addChild("open").denyTabCompletion()
            .hidden(true)
            .description("Open word choice dialogue")
            .playerCaller(this::open);
    }

    @Override
    protected void onEnable() {
    }

    private boolean word(Player player, String[] args) {
        if (args.length != 1) return false;
        State state = plugin.state;
        if (!state.isDrawer(player)) {
            throw new CommandWarn("You are not the artist");
        }
        if (state.phase != Phase.PICK) {
            throw new CommandWarn("Phrase has already been chosen");
        }
        final int index = CommandArgCompleter.requireInt(args[0], i -> i >= 0 && i < state.wordChoices.size());
        state.pickWord(player, index);
        return true;
    }

    private void open(Player player) {
        State state = plugin.state;
        if (!state.isDrawer(player)) {
            throw new CommandWarn("You are not the artist");
        }
        if (state.phase != Phase.PICK) {
            throw new CommandWarn("Phrase has already been chosen");
        }
        state.openPickBook(player);
    }
}
