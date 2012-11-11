package org.maxgamer.maxbans.banmanager;

import java.net.InetAddress;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.maxgamer.maxbans.MaxBans;
import org.maxgamer.maxbans.database.Database;

public class BanManager{
	private MaxBans plugin;
	private HashMap<String, Ban> bans = new HashMap<String, Ban>();
	private HashMap<String, TempBan> tempbans = new HashMap<String, TempBan>();
	private HashMap<String, IPBan> ipbans = new HashMap<String, IPBan>();
	private HashMap<String, TempIPBan> tempipbans = new HashMap<String, TempIPBan>();
	private HashMap<String, Mute> mutes = new HashMap<String, Mute>();
	private HashMap<String, TempMute> tempmutes = new HashMap<String, TempMute>();
	private HashMap<String, List<Warn>> warnings = new HashMap<String, List<Warn>>();
	private TrieMap<String> recentips = new TrieMap<String>();
	
	private HashSet<String> chatCommands = new HashSet<String>();
	
	public boolean lockdown = false;
	public String lockdownReason = "None";
	
	private Database db;
	
	public BanManager(MaxBans plugin){
		this.plugin = plugin;
		this.db = plugin.getDB();
		
		this.reload();
	}
	
	
	/**
	 * Reloads from the database.
	 * Don't use this except when starting up.
	 */
	public void reload(){
		plugin.getDB().getDatabaseWatcher().run(); //Clears it. Does not restart it.
		
		//Check the database is the same instance
		this.db = plugin.getDB();
		
		//Clear the memory cache
		this.bans.clear();
		this.tempbans.clear();
		this.ipbans.clear();
		this.tempipbans.clear();
		this.mutes.clear();
		this.tempmutes.clear();
		this.recentips.clear();
		
		plugin.reloadConfig();
		
		this.lockdown = plugin.getConfig().getBoolean("lockdown");
		this.lockdownReason = plugin.getConfig().getString("lockdown-reason");
		
		//Reload the cache from the database.
		String query = "none";
		plugin.getLogger().info("Loading from DB...");
		try{
			//Phase 1: Load bans
			plugin.getLogger().info("Loading bans");
			query = "SELECT * FROM bans";
			PreparedStatement ps = db.getConnection().prepareStatement(query);
			ResultSet rs = ps.executeQuery();
			
			while(rs.next()){
				String name = rs.getString("name");
				String reason = rs.getString("reason");
				String banner = rs.getString("banner");
				
				long expires = rs.getLong("expires");
				long time = rs.getLong("time");
				
				if(expires != 0){
					TempBan tb = new TempBan(reason, banner, time, expires);
					this.tempbans.put(name, tb);
				}
				else{
					Ban ban = new Ban(reason, banner, time);
					this.bans.put(name, ban);
				}
			}
			
			//Phase 2: Load IP Bans
			plugin.getLogger().info("Loading ipbans");
			query = "SELECT * FROM ipbans";
			ps = db.getConnection().prepareStatement(query);
			rs = ps.executeQuery();
			
			while(rs.next()){
				String ip = rs.getString("ip");
				String reason = rs.getString("reason");
				String banner = rs.getString("banner");
				
				long expires = rs.getLong("expires");
				long time = rs.getLong("time");
				
				if(expires != 0){
					if(expires < System.currentTimeMillis()){
						db.getBuffer().addString("DELETE FROM ipbans WHERE ip = '"+ip+"' AND time <> 0");
					}
					else{
						TempIPBan tib = new TempIPBan(reason, banner, time, expires);
						this.tempipbans.put(ip, tib);
					}
				}
				else{
					IPBan ipban = new IPBan(reason, banner, time);
					this.ipbans.put(ip, ipban);
				}
			}
			
			//Phase 3: Load Mutes
			plugin.getLogger().info("Loading mutes");
			query = "SELECT * FROM mutes";
			ps = db.getConnection().prepareStatement(query);
			rs = ps.executeQuery();
			
			while(rs.next()){
				String name = rs.getString("name");
				String banner = rs.getString("muter");
				
				long expires = rs.getLong("expires");
				long time = rs.getLong("time");
				
				if(expires != 0){
					if(expires < System.currentTimeMillis()){
						db.getBuffer().addString("DELETE FROM mutes WHERE name = '"+name+"' AND time <> 0");
					}
					else{
						TempMute tmute = new TempMute(banner, time, expires);
						this.tempmutes.put(name, tmute);
					}
				}
				else{
					Mute mute = new Mute(banner, time);
					this.mutes.put(name, mute);
				}
			}
			
			//Phase 4 loading: Load IP history
			plugin.getLogger().info("Loading IP History");
			query = "SELECT * FROM iphistory";
			ps = db.getConnection().prepareStatement(query);
			rs = ps.executeQuery();
			
			while(rs.next()){
				String name = rs.getString("name");
				String ip = rs.getString("ip");
				
				this.recentips.put(name, ip);
			}
			
			//Phase 5 loading: Load Warn history
			
			plugin.getLogger().info("Loading warn history...");
			query = "SELECT * FROM warnings";
			ps = db.getConnection().prepareStatement(query);
			rs = ps.executeQuery();
			
			while(rs.next()){
				String name = rs.getString("name");
				String reason = rs.getString("reason");
				String banner = rs.getString("banner");
				
				Warn warn = new Warn(reason,banner);
				
				List<Warn> warns = this.warnings.get(name);
				if(warns == null){
					warns = new ArrayList<Warn>();
					this.warnings.put(name, warns);
				}
				warns.add(warn);
			}
			
			//Phase 6 loading: Load Chat Commands
			plugin.getLogger().info("Loading chat commands...");
			List<String> cmds = plugin.getConfig().getStringList("chat-commands");
			for(String s : cmds){
				this.addChatCommand(s);
			}
		}
		catch(SQLException e){
			plugin.getLogger().severe(plugin.color_secondary + "Could not load database history using: " + query);
			e.printStackTrace();
		}

		db.scheduleWatcher(); //Actually starts it.
	}
    
	/**
	 * Fetches a mute on a player with a specific name
	 * @param name The name of the player.  Case doesn't matter.
	 * @return The mute object or null if they aren't muted.
	 * This will never return an expired mute.
	 */
    public Mute getMute(String name) {
    	name = name.toLowerCase();
    	
        Mute mute = mutes.get(name);
        if (mute != null) {
            return mute;
        }
        TempMute tempm = tempmutes.get(name);
        if (tempm !=null) {
            if (System.currentTimeMillis() < tempm.getExpires()) {
                return tempm;
            }
            else{
            	tempmutes.remove(name);
            	String query = "DELETE FROM mutes WHERE name = '"+name+"' AND expires <> 0";
            	db.getBuffer().addString(query);
            }
        }
        return null;
    }
    
    /**
     * Gets a ban by a players name
     * @param name The name of the player (any case)
     * @return The ban object or null if they are not banned.
     * Does not return expired bans, ever.
     */
    public Ban getBan(String name){
    	name = name.toLowerCase();
    	
    	Ban ban = bans.get(name);
    	if(ban != null){
    		return ban;
    	}
    	
    	TempBan tempBan = tempbans.get(name);
    	if(tempBan != null){
    		if(System.currentTimeMillis() < tempBan.getExpires()){
    			return tempBan;
    		}
    		else{
    			tempbans.remove(name);
    			String query = "DELETE FROM bans WHERE name = '"+name+"' AND expires <> 0";
            	db.getBuffer().addString(query);
    		}
    	}
    	
    	return null;
    }
    
    /**
     * Fetches an IP ban from the database
     * @param ip The IP address to search for
     * @return The IPBan object, or null if there is no ban
     * Will never return an expired ban
     */
    public IPBan getIPBan(String ip){
    	IPBan ipBan = ipbans.get(ip);
    	if(ipBan != null){
    		return ipBan;
    	}
    	
    	TempIPBan tempIPBan = tempipbans.get(ip);
    	if(tempIPBan != null){
    		if(System.currentTimeMillis() < tempIPBan.getExpires()){
            	return tempIPBan;
    		}
    		else{
    			ipbans.remove(ip);
    			String query = "DELETE FROM ipbans WHERE ip = '"+ip+"' AND expires <> 0";
            	db.getBuffer().addString(query);
    		}
    	}
    	return null;
    }
    
    /**
     * Fetches an IP ban from the database
     * @param ip The IP address to search for
     * @return The IPBan object, or null if there is no ban
     * Will never return an expired ban
     */
    public IPBan getIPBan(InetAddress addr){
    	return this.getIPBan(addr.getHostAddress());
    }
    
    /**
     * Fetches the IP history of everyone ever
     * @return the IP history of everyone ever. Format: HashMap<Username, IP Address>.
     */
    public TrieMap<String> getIPHistory(){
    	return this.recentips;
    }
    
    /**
     * Fetches a list of all warnings the player currently has to their name.
     * @param name The name of the player to fetch. Case insensitive.
     * @return a list of all warnings the player currently has to their name.
     */
    public List<Warn> getWarnings(String name){
    	name = name.toLowerCase();
    	return this.warnings.get(name);
    }
    
    /**
     * Creates a new ban and stores it in the database
     * @param name The name of the player who is banned
     * @param reason The reason they were banned
     * @param banner The admin who banned them
     */
    public void ban(String name, String reason, String banner){
    	name = name.toLowerCase();
    	banner = banner.toLowerCase();
    	
    	Ban ban = new Ban(reason, banner, System.currentTimeMillis());
    	this.bans.put(name, ban);
    	
    	//Now we can escape them
    	name = escape(name);
    	banner = escape(banner);
    	reason = escape(reason);
    	
    	plugin.getDB().getBuffer().addString("INSERT INTO bans (name, reason, banner, time) VALUES ('"+name+"','" + reason+"','" + banner+"','" + System.currentTimeMillis()+"');");
    }
    
    /**
     * Removes a ban and removes it from the database
     * @param name The name of the player who is banned
     */
    public void unban(String name){
    	name = name.toLowerCase();
    	Ban ban = this.bans.get(name);
    	TempBan tBan = this.tempbans.get(name);
    	String ip = this.getIP(name);
    	
    	//Now we can escape it
    	name = escape(name);
    	String query = "";
    	if(ban != null){
    		this.bans.remove(name);
    		query += "DELETE FROM bans WHERE name = '"+name+"'; ";
    	}
    	if(tBan != null){
    		this.tempbans.remove(name);
    		query += "DELETE FROM bans WHERE name = '"+name+"'; ";
    	}
    	if(!query.isEmpty()){
	    	plugin.getDB().getBuffer().addString(query);
    	}
    	unbanip(ip);
    }
    
    /**
     * Removes a ban and removes it from the database
     * @param ip The ip of the player who is banned
     */
    public void unbanip(String ip){
    	IPBan ipBan = this.ipbans.get(ip);
    	TempIPBan tipBan = this.tempipbans.get(ip);
    	
    	String query = "";
    	if(ipBan != null){
    		this.ipbans.remove(ip);
    		query += "DELETE FROM ipbans WHERE ip = '"+ip+"'; ";
    	}
    	if(tipBan != null){
    		this.tempipbans.remove(ip);
    		query += "DELETE FROM ipbans WHERE ip = '"+ip+"'; ";
    	}
    	if(!query.isEmpty()){
	    	plugin.getDB().getBuffer().addString(query);
    	}
    }
    
    /**
     * Unmutes the given player.
     * @param name The name of the player. Case insensitive.
     */
    public void unmute(String name){
    	name = name.toLowerCase();
    	
    	Mute mute = this.mutes.get(name);
    	TempMute tMute = this.tempmutes.get(name);
    	
    	//Escape it
    	name = escape(name);
    	String query = "";
    	if(mute != null){
    		this.mutes.remove(name);
    		query = "DELETE FROM mutes WHERE name = '"+name+"';";
    	}
    	if(tMute != null){
    		this.tempmutes.remove(name);
    		query = "DELETE FROM mutes WHERE name = '"+name+"';";
    	}
    	
    	plugin.getDB().getBuffer().addString(query);
    }
    
    /**
     * @param name The name of the player.
     * @param reason Reason for the ban
     * @param banner The admin who banned them
     * @param expires Epoch time (Milliseconds) that they're unbanned.
     * Expirey time is NOT time from creating ban, it is milliseconds from 1970 (System.currentMillis())
     */
    public void tempban(String name, String reason, String banner, long expires){
    	name = name.toLowerCase();
    	banner = banner.toLowerCase();
    	
    	TempBan ban = new TempBan(reason, banner, System.currentTimeMillis(), expires);
    	this.tempbans.put(name, ban);
    	
    	//Safe to escape now
    	name = escape(name);
    	banner = escape(banner);
    	reason = escape(reason);
    	
    	plugin.getDB().getBuffer().addString("INSERT INTO bans (name, reason, banner, time, expires) VALUES ('"+name+"','" + reason+"','" + banner+"','" + System.currentTimeMillis()+"','" + expires+"');");
    }
    
    /**
     * IP Bans an IP address so they can't join.
     * @param ip The IP to ban (e.g. 127.0.0.1)
     * @param reason The reason ("Misconduct!")
     * @param banner The admin who banned them
     */
    public void ipban(String ip, String reason, String banner){
    	banner = banner.toLowerCase();
    	
    	IPBan ipban = new IPBan(reason, banner, System.currentTimeMillis());
    	
    	this.ipbans.put(ip, ipban);
    	
    	//Safe to escape now
    	banner = escape(banner);
    	reason = escape(reason);
    	
    	plugin.getDB().getBuffer().addString("INSERT INTO ipbans (ip, reason, banner, time) VALUES ('"+ip+"','" + reason+"','" + banner+"','" + System.currentTimeMillis()+"');");
    }
    
    /**
     * IP Bans an IP address so they can't join.
     * @param ip The IP to ban (e.g. 127.0.0.1)
     * @param reason The reason ("Misconduct!")
     * @param banner The admin who banned them
     * @param expires The time the ban expires
     */
    public void tempipban(String ip, String reason, String banner, long expires){
    	banner = banner.toLowerCase();
    	
    	TempIPBan tib = new TempIPBan(reason, banner, System.currentTimeMillis(), expires);
    	
    	this.tempipbans.put(ip, tib);
    	
    	//Safe to escape now
    	banner = escape(banner);
    	reason = escape(reason);
    	
    	plugin.getDB().getBuffer().addString("INSERT INTO ipbans (ip, reason, banner, time, expires) VALUES ('"+ip+"','" + reason+"','" + banner+"','" + System.currentTimeMillis()+"','" + expires+"');");
    }
    
    /**
     * Mutes a player so they can't chat.
     * @param name The name of the player to mute
     * @param banner The admin who muted them
     */
    public void mute(String name, String banner){
    	name = name.toLowerCase();
    	
    	Mute mute = new Mute(banner, System.currentTimeMillis());
    	
    	this.mutes.put(name, mute);
    	
    	name = escape(name);
    	banner = escape(banner);
    	
    	plugin.getDB().getBuffer().addString("INSERT INTO mutes (name, muter, time) VALUES ('"+name+"','" + banner+"','"+System.currentTimeMillis()+"');");
    }
    
    /**
     * Mutes a player so they can't chat.
     * @param name The name of the player to mute
     * @param banner The admin who muted them
     * @param expires The time the mute expires
     */
    public void tempmute(String name, String banner, long expires){
    	name = name.toLowerCase();
    	banner = banner.toLowerCase();
    	
    	TempMute tmute = new TempMute(banner, System.currentTimeMillis(), expires);
    	
    	this.tempmutes.put(name, tmute);
    	
    	name = escape(name);
    	banner = escape(banner);
    	
    	plugin.getDB().getBuffer().addString("INSERT INTO mutes (name, muter, time, expires) VALUES ('"+name+"','" + banner+"','"+System.currentTimeMillis()+"','"+expires+"');");
    }
    
    /**
     * Gives a player a warning
     * @param name The name of the player
     * @param reason The reason for the warning
     */
    public void warn(String name, String reason, String banner){
    	name = name.toLowerCase();
    	banner = banner.toLowerCase();
    	
    	List<Warn> warns = this.warnings.get(name);
    	
    	if(warns == null){
    		warns = new ArrayList<Warn>();
    		this.warnings.put(name, warns);
    	}
    	
    	//Adds it to warnings
    	warns.add(new Warn(reason, banner));
    	
    	name = escape(name);
    	banner = escape(banner);
    	reason = escape(reason);
    	
    	db.getBuffer().addString("INSERT INTO warnings (name, reason, banner) VALUES ('"+name+"','"+reason+"','"+banner+"')");
    	
    	if(warns.size() >= plugin.getConfig().getInt("max-warnings")){
    		this.tempban(name, "Reached Max Warnings:\n" + reason, banner, System.currentTimeMillis() + 3600000); //1 hour
    		Player p = Bukkit.getPlayerExact(name);
    		if(p != null){
    			p.kickPlayer("Reached Max Warnings:\n" + reason);
    		}
    		announce(plugin.color_secondary + name + plugin.color_primary + " has reached max warnings.  One hour ban.");
    		
    		clearWarnings(name);
    	}
    }
    
    /**
     * Removes all warnings for a player from memory and the database
     * @param name The name of the player. Case insensitive.
     */
    public void clearWarnings(String name){
    	name = name.toLowerCase();
    	
    	this.warnings.put(name, null);
    	
    	//Escape it
    	name = escape(name);

    	db.getBuffer().addString("DELETE FROM warnings WHERE name = '"+name+"'");
    }
    
    /**
     * Gets the IP address a player last used, even if offline
     * Will return null if no history for that IP address.
     * @param ip The IP to lookup
     * @return a hashset of users whose most recent IP is the given one
     */
    public String getIP(String user){
    	return this.recentips.get(user.toLowerCase());
    }
    
    /**
     * Notes that a player joined from the given IP.
     * @param name Name of player. Case insensitive.
     * @param ip The ip they're connecting from.
     */
    public void logIP(String name, String ip){
    	name = name.toLowerCase();
    	String query;
    	
    	if(ip.equals(this.recentips.get(name))) return; //That's old news.
    	
    	if(this.recentips.contains(name)){
    		query = "UPDATE iphistory SET ip = '"+ip+"' WHERE name = '"+escape(name)+"'";
    	}
    	else{
    		query = "INSERT INTO iphistory (name, ip) VALUES ('"+escape(name)+"','"+ip+"')";
    	}
    	
    	this.recentips.put(name, ip);
    	
    	this.db.getBuffer().addString(query);
    }
    
    /**
     * Notes that a player joined from the given IP.
     * @param p The player who is connecting
     */
    public void logIP(Player p){
    	this.logIP(p.getName(), p.getAddress().getAddress().getHostAddress());
    }
	
	/**
	 * Announces a message to the whole server.
	 * Also logs it to the console.
	 * @param s The string
	 */
	public void announce(String s){
		plugin.getLogger().info(s);
		for(Player p : Bukkit.getOnlinePlayers()){
			p.sendMessage(s);
		}
	}
	
	/**
	 * Finds the nearest known match to a given name.
	 * Searches online players first, then any exact matches
	 * for offline players, and then the nearest match for
	 * offline players.
	 * 
	 * @param partial The partial name
	 * @return The full name, or the same partial name if it can't find one
	 */
	public String match(String partial){
		return match(partial, false);
	}
	/**
	 * Finds the nearest known match to a given name.
	 * Searches online players first, then any exact matches
	 * for offline players, and then the nearest match for
	 * offline players.
	 * 
	 * @param partial The partial name
	 * @param excludeOnline Avoids searching online players
	 * @return The full name, or the same partial name if it can't find one
	 */
	public String match(String partial, boolean excludeOnline){
		partial = partial.toLowerCase();
		//Check the name isn't already complete
		String ip = this.recentips.get(partial);
		if(ip != null) return partial; // it's already complete.
		
		//Check the player and if they're online
		if(!excludeOnline){
			Player p = Bukkit.getPlayer(partial);
			if(p != null) return p.getName();
		}
		
		//Scan the map for the match. Iff one is found, return it.
		String nearestMap = recentips.nearestKey(partial); // Note that checking the nearest match to an exact name will return the same exact name
		
		if(nearestMap != null) return nearestMap;
		
		//We can't help you. Maybe you can not be lazy.
		return partial;
	}
	
	/**
	 * Prepares a query for the database by fixing \ and 's
	 * @param s The string to escape E.g. can't do that :\
	 * @return The escaped string. E.g. can\'t do that :\\
	 */
	public String escape(String s){
		return s.replace("'", "''");
	}
	
	public void addChatCommand(String s){
		s = s.toLowerCase();
		this.chatCommands.add(s);
	}
	public boolean isChatCommand(String s){
		s = s.toLowerCase();
		return this.chatCommands.contains(s);
	}
}
