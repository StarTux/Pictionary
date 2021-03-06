package com.cavetale.pictionary;

import com.cavetale.pictionary.command.CommandContext;
import com.cavetale.pictionary.command.CommandNode;
import com.cavetale.pictionary.command.CommandWarn;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class PictionaryCommand implements TabExecutor {
    private final PictionaryPlugin plugin;
    private CommandNode root = new CommandNode("pictionary");

    void enable() {
        root.description("Pictionary Admin Interface");
        root.addChild("setcanvas")
            .caller(this::setcanvas)
            .description("Set the canvas");
        root.addChild("start")
            .caller(this::start)
            .description("Start the game");
        root.addChild("stop")
            .caller(this::stop)
            .description("Stop the game");
        root.addChild("clear")
            .caller(this::clear)
            .description("Clear all scores");
        root.addChild("scores")
            .caller(this::scores)
            .description("List scores");
        root.addChild("debug")
            .caller(this::debug)
            .description("Debug");
        root.addChild("clearwordlist")
            .caller(this::clearWordList)
            .description("Clear word list");
        root.addChild("save")
            .caller(this::save)
            .description("Save to disk");
        root.addChild("reload")
            .caller(this::reload)
            .description("Reload from disk");
        root.addChild("memberlist")
            .caller(this::memberlist)
            .description("Toggle MemberList");
        plugin.getCommand("pictionary").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return root.call(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return root.complete(sender, command, label, args);
    }

    Cuboid requireWorldEditSelection(Player player) {
        Cuboid cuboid = WorldEdit.getSelection(player);
        if (cuboid == null) throw new CommandWarn("Make a WorldEdit selection first!");
        return cuboid;
    }

    boolean setcanvas(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Cuboid selection = requireWorldEditSelection(player);
        plugin.state.setCanvas(player.getWorld(), selection);
        plugin.save();
        context.message(ChatColor.YELLOW + "Canvas updated: " + selection);
        return true;
    }

    boolean start(CommandContext context, CommandNode node, String[] args) {
        if (args.length == 0) {
            plugin.state.startNewGame();
            context.message("Game started!");
            return true;
        }
        if (args.length < 2) return false;
        Player drawer = plugin.getServer().getPlayerExact(args[0]);
        if (drawer == null) throw new CommandWarn("Player not found: " + args[0]);
        String phrase = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.state.startGame(drawer, phrase);
        context.message("Game started: " + drawer.getName() + ", " + phrase);
        return true;
    }

    boolean stop(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        plugin.state.endGame();
        context.message("Game stopped.");
        return true;
    }

    boolean scores(CommandContext context, CommandNode node, String[] args) {
        World world = plugin.state.getWorld();
        for (Player target : world.getPlayers()) {
            context.message(target.getName() + ": " + plugin.state.userOf(target).score);
        }
        return true;
    }

    boolean clear(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        plugin.state.users.clear();
        context.message("All scores cleared");
        return true;
    }

    boolean debug(CommandContext context, CommandNode node, String[] args) {
        context.message(Json.serialize(plugin.state));
        return true;
    }

    boolean clearWordList(CommandContext context, CommandNode node, String[] args) {
        plugin.state.wordList.clear();
        context.message("Word list cleared!");
        return true;
    }

    boolean save(CommandContext context, CommandNode node, String[] args) {
        plugin.save();
        context.message("Saved to disk");
        return true;
    }

    boolean reload(CommandContext context, CommandNode node, String[] args) {
        plugin.load();
        context.message("Reloaded from disk");
        return true;
    }

    boolean memberlist(CommandContext context, CommandNode node, String[] args) {
        plugin.state.memberList = !plugin.state.memberList;
        plugin.save();
        context.message("Member list: " + plugin.state.memberList);
        return true;
    }
}
