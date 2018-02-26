package org.maxgamer.maxbans.database;

import org.bukkit.Bukkit;
import org.maxgamer.maxbans.MaxBans;

import java.io.File;
import java.sql.SQLException;

public class Conversion implements Runnable
{

	private MaxBans plugin;
	private Database target;
	private Database source;
	private File sourceFile;

	public Conversion (MaxBans plugin, final DatabaseCore target, final DatabaseCore source)
	{
		this(plugin, target, source, null);
	}

	public Conversion (MaxBans plugin, final DatabaseCore target, final DatabaseCore source, File sourceFile)
	{
		try {
			this.plugin = plugin;
			this.target = new Database(target);
			this.source = new Database(source);
			this.sourceFile = sourceFile;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void run()
	{
		if (null == plugin || null == target || null == source)
			return;

		try {
			if (new File(this.sourceFile.getPath() + "-old").exists()) {
				System.out.println("[!!!] CANNOT CONVERT SQLITE TO MYSQL, OLD DATABASE EXISTS! Did you forget to turn off conversion?");
				return;
			}

			source.copyTo(target);

			if (null != this.sourceFile) {
				this.sourceFile.renameTo(new File(this.sourceFile.getPath() + "-old"));
			}

			System.out.println("Your data has been converted -- please disable conversion and restart your server");
		} catch (SQLException ex) {
			System.out.println("FAILED TO CONVERT SQLITE TO MYSQL DATABASE");
			this.kill();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void kill()
	{
		Bukkit.getPluginManager().disablePlugin(this.plugin);
	}

}
