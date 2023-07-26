package org.mastodon.mamut.plugins;

import org.mastodon.mamut.SciviewBridge;
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
		StringBuilder msg = new StringBuilder("Mastodon-sciview Bridge Help:\n");
		msg.append(SciviewBridge.key_DEC_SPH).append(" does ").append(SciviewBridge.desc_DEC_SPH).append("\n");
		msg.append(SciviewBridge.key_INC_SPH).append(" does ").append(SciviewBridge.desc_INC_SPH).append("\n");
		msg.append(SciviewBridge.key_COLORING).append(" does ").append(SciviewBridge.desc_COLORING).append("\n");
		msg.append(SciviewBridge.key_CLRNG_AUTO).append(" does ").append(SciviewBridge.desc_CLRNG_AUTO).append("\n");
		msg.append(SciviewBridge.key_CLRNG_ONOFF).append(" does ").append(SciviewBridge.desc_CLRNG_ONOFF).append("\n");
		msg.append(SciviewBridge.key_CTRL_WIN).append(" does ").append(SciviewBridge.desc_CTRL_WIN).append("\n");
		msg.append(SciviewBridge.key_CTRL_INFO).append(" does ").append(SciviewBridge.desc_CTRL_INFO).append("\n");

		//abusing the warn level to make sure the log window pops up
		//and the help message is not left unnoticed
		logService.warn(msg.toString());
	}
}
