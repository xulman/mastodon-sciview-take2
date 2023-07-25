package org.mastodon.mamut;

import mpicbg.spim.data.SpimDataException;
import org.mastodon.mamut.project.MamutProjectIO;
import org.scijava.Context;
import sc.iview.SciView;
import javax.swing.*;
import java.io.IOException;

public class StartSciviewBridgeDirectly {
	static WindowManager giveMeSomeMastodon(final Context scijavaCtx)
			throws IOException, SpimDataException {
		String projectPath = "/home/ulman/Mette/e1/E1_reduced.mastodon";

		//ImageJ ij = new ImageJ();
		//ij.ui().showUI();

		//the central hub, a container to hold all
		final WindowManager windowManager = new WindowManager( scijavaCtx );
		windowManager.getProjectManager().open( new MamutProjectIO().load( projectPath ) );

		//a GUI element wrapping around the hub
		final MainWindow win = new MainWindow(windowManager);

		//this makes the true Mastodon window visible
		//note: you can open project that restores/reopen e.g. TrackScheme window,
		//      yet the main Mastodon window is not shown... but this is runs non-stop
		win.setVisible( true );

		//this makes the whole thing (incl. the central hub) go down when the GUI is closed
		win.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );

		return windowManager;
	}

	static SciView createSciview()
			throws Exception {
		return SciView.create();
	}

	public static void main(String[] args) {
		try {
			SciView sv = createSciview();
			sv.toggleSidebar();
			WindowManager mastodon = giveMeSomeMastodon(sv.getScijavaContext());

			final SciviewBridge bridge = new SciviewBridge(mastodon, sv);
			bridge.openSyncedBDV();

		} catch (Exception e) {
			System.out.println("Got this exception: "+e.getMessage());
		}
	}
}
