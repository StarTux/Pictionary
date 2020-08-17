package com.cavetale.pictionary.command;

/**
 * Can be thrown from inside CommandCall::call.
 */
public class CommandWarn extends RuntimeException {
    public CommandWarn(final String msg) {
        super(msg);
    }
}
