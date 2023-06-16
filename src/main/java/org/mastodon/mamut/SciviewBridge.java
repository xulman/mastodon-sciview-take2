//TODO add license text
package org.mastodon.mamut;

import graphics.scenery.Sphere;
import graphics.scenery.controls.InputHandler;
import mpicbg.spim.data.SpimDataException;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.mamut.util.SphereNodes;
import org.scijava.Context;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import sc.iview.SciView;

import java.io.IOException;

public class SciviewBridge {

	//data source stuff
	final WindowManager mastodonWin;

	//data sink stuff
	final SciView sciviewWin;
	final SphereNodes sphereNodes;

	public SciviewBridge(final WindowManager mastodonMainWindow,
	                     final SciView targetSciviewWindow)
	{
		this.mastodonWin = mastodonMainWindow;
		this.sciviewWin = targetSciviewWindow;

		//add the "root" node for this Mastodon session
		Sphere parentNode = sciviewWin.addSphere();
		//todo: make the parent node (sphere) invisible
		parentNode.setName( mastodonMainWindow.projectManager.getProject().getProjectRoot().toString() );

		//add the sciview-side displaying handler for the spots
		this.sphereNodes = new SphereNodes(this.sciviewWin, parentNode);

		//todo: add similar handler for the volume
	}

	public MamutViewBdv openSyncedBDV() {
		final MamutViewBdv bdvWin = mastodonWin.createBigDataViewer();
		bdvWin.getFrame().setTitle("BDV linked to Sciview");

		//bdvWin.getViewerPanelMamut().renderTransformListeners().
		//drawSpotsFromThisTimepoint(10);
		//sciviewWin.getCamera().setfo

		//temporary drawing
		sphereNodes.showTheseSpots(mastodonWin.getAppModel(), 10);
		keyHandlersForTestingForNow(bdvWin);

		//todo: setup a callback that
		// -- get's triggered when the BDV window is closed
		// -- takes DE-register handlers created above
		return bdvWin;
	}

	private void keyHandlersForTestingForNow(final MamutViewBdv forThisBdv) {
		//handlers
		final Behaviour clk_DEC_SPH = (ClickBehaviour) (x, y) -> sphereNodes.decreaseSphereScale();
		final Behaviour clk_INC_SPH = (ClickBehaviour) (x, y) -> sphereNodes.increaseSphereScale();

		//register them
		final InputHandler handler = sciviewWin.getSceneryInputHandler();
		handler.addKeyBinding("decrease_initial_spheres_size", "O");
		handler.addBehaviour("decrease_initial_spheres_size", clk_DEC_SPH);
		handler.addKeyBinding("increase_initial_spheres_size", "shift O");
		handler.addBehaviour("increase_initial_spheres_size", clk_INC_SPH);

		//deregister them when they are due
		forThisBdv.onClose(() -> {
			handler.removeKeyBinding("decrease_initial_spheres_size");
			handler.removeBehaviour("decrease_initial_spheres_size");
			handler.removeKeyBinding("increase_initial_spheres_size");
			handler.removeBehaviour("increase_initial_spheres_size");
		});
	}

	// --------------------------------------------------------------------------
	static WindowManager giveMeSomeMastodon(final Context scijavaCtx)
	throws IOException, SpimDataException {
		String projectPath = "/home/ulman/Mette/e1/E1_reduced.mastodon";

		//not sure what this is good for but see it everywhere...
		//(seems to give no effect on Linux)
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

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
		win.setDefaultCloseOperation( /* WindowConstants.EXIT_ON_CLOSE == */ 3 );

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
			System.out.println("got exception: "+e.getMessage());
		}
	}
}
