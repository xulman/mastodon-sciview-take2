package org.mastodon.mamut.plugins.scijava;

import bdv.viewer.Source;
import org.mastodon.mamut.SciviewBridge;
import org.mastodon.mamut.WindowManager;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.iview.SciView;
import sc.iview.SciViewService;

import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, name = "Mastodon to Sciview")
public class MastodonSidePlugin extends DynamicCommand {
	@Parameter
	private WindowManager mastodon;

	@Parameter(label = "Try to reuse existing sciview window:")
	boolean tryToReuseExistingSciviewWindow = true;

	@Parameter(label = "Also open the controlling window right away:")
	boolean openBridgeUI = true;

	@Parameter(label = "Choose image data channel:",
			choices = {},
			initializer = "volumeParams")
	String useThisChannel = "default first channel";

	List<String> channelNames;

	void volumeParams() {
		final int sources = mastodon.getAppModel()
				.getSharedBdvData().getSources().size();
		channelNames = new ArrayList<>(sources);

		mastodon.getAppModel()
				.getSharedBdvData().getSources()
				.forEach(s -> channelNames.add( s.getSpimSource().getName() ));
		getInfo()
				.getMutableInput("useThisChannel",String.class)
				.setChoices(channelNames);
	}
	@Override
	public void run() {
		//figure out the selected source and show another plugin with res levels options
		int chosenChannel = 0;
		while ( chosenChannel < channelNames.size()
				&& !useThisChannel.equals(channelNames.get(chosenChannel)) ) ++chosenChannel;
		//NB: theoretically the channel should be always found...
		if (chosenChannel == channelNames.size()) return; // !?

		//query res levels for this channel
		this.getContext().getService(CommandService.class).run(
				MastodonSidePluginResLevels.class, true,
				"mastodon", mastodon,
				"channelIdx", chosenChannel,
				"tryToReuseExistingSciviewWindow", tryToReuseExistingSciviewWindow,
				"openBridgeUI", openBridgeUI );
	}
	// =============================================================================


	@Plugin(type = Command.class, name = "Mastodon to Sciview: Levels")
	public static class MastodonSidePluginResLevels extends DynamicCommand {
		@Parameter
		private LogService logService;

		@Parameter
		private SciViewService sciViewService;

		@Parameter
		private WindowManager mastodon;

		@Parameter(persist = false)
		private int channelIdx;

		@Parameter(persist = false)
		boolean tryToReuseExistingSciviewWindow = true;

		@Parameter(persist = false)
		boolean openBridgeUI = true;

		@Parameter(label = "Choose resolution level:",
				choices = {},
				initializer = "levelParams")
		String useThisResolutionDownscale = "[1,1,1]";

		List<String> levelNames;

		void levelParams() {
			Source<?> chSource = mastodon.getAppModel()
					.getSharedBdvData().getSources()
					.get(channelIdx).getSpimSource();
			final int levels = chSource.getNumMipmapLevels();
			levelNames = new ArrayList<>(levels);

			final long[] baseLevelDims = new long[3];
			final long[] curLevelDims = new long[3];

			chSource.getSource(0,0).dimensions(baseLevelDims);
			levelNames.add("[1,1,1]");

			for (int level = 1; level < levels; ++level) {
				chSource.getSource(0, level).dimensions(curLevelDims);
				levelNames.add("["
					+baseLevelDims[0]/curLevelDims[0]+","
					+baseLevelDims[1]/curLevelDims[1]+","
					+baseLevelDims[2]/curLevelDims[2]+"]");
			}
			getInfo()
					.getMutableInput("useThisResolutionDownscale",String.class)
					.setChoices(levelNames);
		}

		public void run() {
			//figure out the selected res. level
			int chosenLevel = 0;
			while ( chosenLevel < levelNames.size()
					&& !useThisResolutionDownscale.equals(levelNames.get(chosenLevel)) ) ++chosenLevel;
			//NB: theoretically the channel should be always found...
			if (chosenLevel == levelNames.size()) return; // !?

			logService.info("Selected volume from channel "+channelIdx+" of "
					+(chosenLevel+1)+"-best resolution level...");

			try {
				if (!tryToReuseExistingSciviewWindow) sciViewService.createSciView();
				SciView sv = sciViewService.getOrCreateActiveSciView();

				SciviewBridge bridge = new SciviewBridge(mastodon, channelIdx,chosenLevel, sv);
				if (openBridgeUI) bridge.createAndShowControllingUI();

			} catch (Exception e) {
				logService.error("MastodonSciview plugin error: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
}
