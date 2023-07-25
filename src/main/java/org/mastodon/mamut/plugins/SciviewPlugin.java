package org.mastodon.mamut.plugins;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static sc.iview.commands.MenuWeights.HELP;
import static sc.iview.commands.MenuWeights.HELP_HELP;

@Plugin(type = Command.class, menuRoot = "SciView", //
		menu = { @Menu(label = "Help", weight = HELP), //
				@Menu(label = "Mastodon Bridge", weight = HELP_HELP) })
public class SciviewPlugin implements Command
{
	@Parameter
	LogService logService;

	@Override
	public void run()
	{
		logService.warn("Mastodon-sciview Bridge Help:");
		System.out.println("IEWRORUTEWIOURTEIWO");
	}
}
