package org.mastodon.mamut;

import mpicbg.spim.data.SpimDataException;
import org.mastodon.mamut.project.MamutProjectIO;
import org.scijava.Context;
import sc.iview.SciView;
import javax.swing.*;
import java.io.IOException;

public class StartSciviewBridgeDirectly {
	static WindowManager giveMeMastodon(final Context scijavaCtx) {
		//the central hub, a container to hold all
		final WindowManager windowManager = new WindowManager( scijavaCtx );

		//a GUI element wrapping around the hub
		final MainWindow win = new MainWindow(windowManager);

		//this makes the true Mastodon window visible
		//note: you can open project that restores/reopen e.g. TrackScheme window,
		//      yet the main Mastodon window is not shown... but this then runs non-stop
		win.setVisible( true );

		//this makes the whole thing (incl. the central hub) go down when the GUI is closed
		win.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE );

		return windowManager;
	}

	static WindowManager giveMeMastodonOfThisProject(final Context scijavaCtx, String projectPath)
			throws IOException, SpimDataException {
		//ImageJ ij = new ImageJ();
		//ij.ui().showUI();

		WindowManager m = giveMeMastodon(scijavaCtx);
		m.getProjectManager().open( new MamutProjectIO().load( projectPath ) );
		return m;
	}

	static SciView createSciview()
			throws Exception {
		return SciView.create();
	}

	public static void main(String[] args) {
		try {
			// --------------->>  <<---------------
			//point this to your testing project, or grab example project with:
			//git clone https://github.com/mastodon-sc/mastodon-example-data.git
			//String projectPath = "/home/ulman/Mette/e1/E1_reduced.mastodon";
			String projectPath = "/home/ulman/devel/sciview_hack2/mastodon-example-data/tgmm-mini/tgmm-mini.mastodon";
			// --------------->>  <<---------------

			SciView sv = createSciview();
			WindowManager mastodon = giveMeMastodonOfThisProject(sv.getScijavaContext(), projectPath);

			final SciviewBridge bridge = new SciviewBridge(mastodon,0,2, sv);
			//bridge.openSyncedBDV();

			mastodon.getAppModel().projectClosedListeners().add(() -> {
				System.out.println("Mastodon project was closed, cleaning up in sciview:");
				bridge.close(); //calls also bridge.detachControllingUI();
			});
		} catch (Exception e) {
			System.out.println("Got this exception: "+e.getMessage());
		}
	}
}
