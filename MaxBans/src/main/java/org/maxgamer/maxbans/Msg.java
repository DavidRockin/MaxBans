package org.maxgamer.maxbans;

import org.bukkit.ChatColor;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

public class Msg
{
    private static YamlConfiguration cfg;
    
    @SuppressWarnings("deprecation")
	public static void reload() {
        final File f = new File(MaxBans.instance.getDataFolder() + "/messages.yml");
        if (!f.exists()) MaxBans.instance.saveResource("messages.yml", false);
        Msg.cfg = YamlConfiguration.loadConfiguration(f);
    }
    
    public static String get(final String loc, final String[] keys, final String[] values) {
        String msg = Msg.cfg.getString(loc);
        if (msg == null || msg.isEmpty()) {
            return "Unknown message in config: " + loc;
        }
        if (keys != null && values != null) {
            if (keys.length != values.length) {
                try {
                    throw new IllegalArgumentException("Invalid message request. keys.length should equal values.length!");
                }
                catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
            for (int i = 0; i < keys.length; ++i) {
                msg = msg.replace("{" + keys[i] + "}", values[i]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
    
    public static String get(final String loc) {
        return get(loc, null, (String[])null);
    }
    
    public static String get(final String loc, final String key, final String value) {
        return get(loc, new String[] { key }, new String[] { value });
    }
}
