package org.mastodon.mamut;

import net.imagej.ImageJ;
import org.mastodon.mamut.launcher.MastodonLauncher;
import sc.iview.SciView;

public class StartMastodon {
	public static void main(String[] args) {
		try {
			//gives sciview/scenery a chance to run at all...
			SciView.xinitThreads();

			//without this, the GUI harvesting of plugins will not happen
			ImageJ ij = new ImageJ();
			ij.ui().showUI();

			//cannot use this one as it opens its own context...
			//new MastodonLauncherCommand().run();
			//
			//...thus copy&paste the run() above.. this time with the shared context
			final MastodonLauncher launcher = new MastodonLauncher( ij.getContext() );
			launcher.setLocationByPlatform( true );
			launcher.setLocationRelativeTo( null );
			launcher.setVisible( true );
		} catch (Exception e) {
			System.out.println("Got this exception: "+e.getMessage());
		}
	}
}
