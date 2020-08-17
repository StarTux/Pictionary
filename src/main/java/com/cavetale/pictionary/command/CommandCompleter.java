package com.cavetale.pictionary.command;

import java.util.List;

public interface CommandCompleter {
    /**
     * Produce a list of possible completions for the command line input.
     * @param context the context
     * @param the originating node
     * @param arg the remaining command line arguments
     */
    List<String> complete(CommandContext context, CommandNode node, String[] args);
}
