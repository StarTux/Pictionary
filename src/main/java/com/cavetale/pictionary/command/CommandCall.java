package com.cavetale.pictionary.command;

public interface CommandCall {
    boolean call(CommandContext context, CommandNode node, String[] args);
}
