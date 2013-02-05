package org.maxgamer.maxbans.commands;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maxgamer.maxbans.banmanager.Warn;
import org.maxgamer.maxbans.util.Formatter;

public class ClearWarningsCommand extends CmdSkeleton{
    public ClearWarningsCommand(){
        super("maxbans.clearwarnings");
        usage = Formatter.secondary + "Usage: /clearwarnings <player>";
    }
	public boolean run(CommandSender sender, Command cmd, String label, String[] args) {
		if(args.length > 0){
			String name = args[0];
			
			name = plugin.getBanManager().match(name);
			if(name == null){
				name = args[0]; //Use exact name then.
			}
			
			String banner;
			
			if(sender instanceof Player){
				banner = ((Player) sender).getName();
			}
			else{
				banner = "Console";
			}
			
			List<Warn> warnings = plugin.getBanManager().getWarnings(name);
			
			if(warnings == null || warnings.size() == 0){
				sender.sendMessage(Formatter.secondary + name + Formatter.primary + " has no warnings to their name.");
				return true;
			}
			
			plugin.getBanManager().clearWarnings(name);
			Player p = Bukkit.getPlayer(name);
			if(p != null){
				p.sendMessage(Formatter.primary + "Your previous warnings have been pardoned by " + Formatter.secondary + banner);
			}
			sender.sendMessage(Formatter.primary + "Pardoned " + Formatter.secondary + name + Formatter.primary + "'s warnings.");
			plugin.getBanManager().addHistory(banner + " cleared " + name + "'s warnings.");
			return true;
		}
		else{
			sender.sendMessage(usage);
			return true;
		}
	}
}
