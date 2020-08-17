package com.cavetale.pictionary.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class CommandNode {
    private final CommandNode parent;
    private final List<CommandNode> children = new ArrayList<>();
    private final String key;
    private List<String> aliases = new ArrayList<>();
    private CommandCall call;
    private CommandCompleter completer;
    private CommandHelp help;
    private String permission;

    public CommandNode(final CommandNode parent, final String key) {
        this.parent = parent;
        this.key = key;
    }

    public CommandNode(final String key) {
        this(null, key);
    }

    public CommandNode addChild(String childKey) {
        CommandNode child = new CommandNode(this, childKey);
        children.add(child);
        return child;
    }

    public CommandNode getChild(String childKey) {
        CommandNode child = findChildCommand(childKey);
        if (child == null) throw new IllegalArgumentException("Child not found: " + childKey);
        return child;
    }

    public CommandNode caller(CommandCall newCall) {
        this.call = newCall;
        return this;
    }

    public CommandNode completer(CommandCompleter newCompleter) {
        this.completer = newCompleter;
        return this;
    }

    public CommandNode completionList(List<String> list) {
        this.completer = (ctx, nod, args) -> {
            if (args.length == 0) return null;
            String arg = args[args.length - 1].toLowerCase();
            return list.stream()
            .filter(i -> i.toLowerCase().startsWith(arg))
            .collect(Collectors.toList());
        };
        return this;
    }

    public CommandNode completionList(Function<CommandContext, List<String>> func) {
        this.completer = (ctx, nod, args) -> {
            if (args.length == 0) return null;
            String arg = args[args.length - 1].toLowerCase();
            return func.apply(ctx).stream()
            .filter(i -> i.toLowerCase().startsWith(arg))
            .collect(Collectors.toList());
        };
        return this;
    }

    public CommandNode helper(CommandHelp newHelp) {
        this.help = newHelp;
        return this;
    }

    public CommandNode helpList(List<String> newHelp) {
        this.help = (ctx, nod) -> {
            return newHelp;
        };
        return this;
    }

    public CommandNode description(String desc) {
        this.help = (ctx, nod) -> {
            return Arrays.asList(ChatColor.YELLOW + getCommandLine()
                                 + ChatColor.DARK_GRAY + " - "
                                 + ChatColor.GRAY + desc);
        };
        return this;
    }

    public CommandNode helpLine(String newHelp) {
        return helpList(Arrays.asList(newHelp));
    }

    public CommandNode alias(String... newAliases) {
        for (String newAlias : newAliases) {
            aliases.add(newAlias);
        }
        return this;
    }

    public CommandNode terminal() {
        children.clear();
        completer = (ctx, nod, args) -> Collections.emptyList();
        return this;
    }

    public CommandNode permission(String newPermission) {
        this.permission = newPermission;
        return this;
    }

    /**
     * Call this node as if it was entered in the command line, maybe
     * with additional arguments which will be handled here.
     * This will recursively traverse the tree, resulting in potential
     * user feedback or execution at any point, which is why we don't
     * use a utility traversal function.
     * CommandExecutor::onCommand may wrap this function directly.
     * @param context the context
     * @param args the remaining command line arguments
     */
    public boolean call(CommandContext context, String[] args) {
        if (args.length > 0 && !children.isEmpty()) {
            CommandNode child = findChildCommand(args[0]);
            if (child != null && child.hasPermission(context)) {
                boolean res = child.call(context, Arrays.copyOfRange(args, 1, args.length));
                if (!res) res = sendHelp(context);
                return res;
            }
        }
        if (call != null) {
            boolean res;
            try {
                res = call.call(context, this, args);
            } catch (CommandWarn warn) {
                context.sender.sendMessage(ChatColor.RED + warn.getMessage());
                return true;
            }
            if (!res) res = sendHelp(context);
            return res;
        }
        return sendHelp(context);
    }

    public boolean call(CommandSender sender, Command command, String label, String[] args) {
        return call(new CommandContext(sender, command, label, args), args);
    }

    /**
     * Recursively find the tab completion of this node as if it was
     * entered into the command line, with the given arguments.
     * TabCompleter::onTabComplete may wrap this function directly.
     */
    public List<String> complete(CommandContext context, String[] args) {
        if (args.length == 0) return null;
        CommandNode child = findChildCommand(args[0]);
        if (child != null && child.hasPermission(context)) {
            return child.complete(context, Arrays.copyOfRange(args, 1, args.length));
        }
        List<String> listChildren = args.length == 1 ? completeChildren(context, args[0]) : Collections.emptyList();
        List<String> listCustom = completeCustom(context, args);
        if (listChildren == null && listCustom == null) return null;
        List<String> list = new ArrayList<>();
        if (listChildren != null) list.addAll(listChildren);
        if (listCustom != null) list.addAll(listCustom);
        return list;
    }

    public List<String> complete(CommandSender sender, Command command, String label, String[] args) {
        return complete(new CommandContext(sender, command, label, args), args);
    }

    /**
     * Find the immediate child command for the given command line
     * argument.
     */
    public CommandNode findChildCommand(String arg) {
        for (CommandNode child : children) {
            if (child.key.equalsIgnoreCase(arg)) {
                return child;
            }
            for (String alias : child.aliases) {
                if (alias.equalsIgnoreCase(arg)) {
                    return child;
                }
            }
        }
        return null;
    }

    public boolean labelStartsWith(String arg) {
        String argl = arg.toLowerCase();
        if (key.toLowerCase().startsWith(argl)) return true;
        for (String alias : aliases) {
            if (alias.toLowerCase().startsWith(argl)) return true;
        }
        return false;
    }

    public Stream<String> getLabelStream() {
        return Stream.concat(Stream.of(key),
                             aliases.stream());
    }

    /**
     * Fetch the tab completions of this node for the given command
     * line argument.
     */
    public List<String> completeChildren(CommandContext context, String arg) {
        if (children.isEmpty()) return null;
        return children.stream()
            .filter(child -> child.hasPermission(context))
            .filter(child -> child.labelStartsWith(arg))
            .flatMap(CommandNode::getLabelStream)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<String> completeCustom(CommandContext context, String[] args) {
        if (completer == null) return null;
        return completer.complete(context, this, args);
    }

    private int sendHelpUtil(CommandContext context, int indent) {
        if (help == null) return 0;
        if (!hasPermission(context)) return 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i += 1) sb.append(' ');
        String prefix = sb.toString();
        int count = 0;
        for (String line : help.help(context, this)) {
            if (context.isPlayer()) {
                ComponentBuilder cb = new ComponentBuilder()
                    .append(prefix + line)
                    .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, getCommandLine()))
                    //.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(getCommandLine())));
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(getCommandLine())));
                context.player.sendMessage(cb.create());
            } else {
                context.sender.sendMessage(prefix + line);
            }
            count += 1;
        }
        return count;
    }

    public boolean sendHelp(CommandContext context) {
        int lineCount = 0;
        if (help != null) {
            lineCount += sendHelpUtil(context, 0);
        }
        for (CommandNode child : children) {
            lineCount += child.sendHelpUtil(context, 2);
        }
        return lineCount > 0;
    }

    public boolean hasCommandCall() {
        return call != null;
    }

    public boolean isTopLevel() {
        return parent == null;
    }

    public CommandNode getTopLevelNode() {
        CommandNode node = this;
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }

    public boolean hasPermission(CommandContext context) {
        if (permission == null) return true;
        if (context.isConsole()) return true;
        if (context.isPlayer()) return context.player.hasPermission(permission);
        if (context.isEntity()) return true;
        return false;
    }

    public String getCommandLine() {
        if (parent == null) return "/" + key;
        return parent.getCommandLine() + " " + key;
    }
}
