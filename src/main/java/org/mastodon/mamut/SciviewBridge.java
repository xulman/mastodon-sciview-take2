//TODO add license text
package org.mastodon.mamut;

import bdv.viewer.Source;
import graphics.scenery.AmbientLight;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.Node;
import graphics.scenery.PointLight;
import graphics.scenery.Sphere;
import graphics.scenery.controls.InputHandler;
import graphics.scenery.primitives.Cylinder;
import graphics.scenery.volumes.Volume;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.planar.PlanarImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.project.MamutProjectIO;
import org.mastodon.mamut.util.SphereNodes;
import org.mastodon.model.tag.TagSetStructure;
import org.mastodon.ui.coloring.TagSetGraphColorGenerator;
import org.mastodon.ui.coloring.DefaultGraphColorGenerator;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.scijava.Context;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import sc.iview.SciView;
import javax.swing.WindowConstants;
import java.io.IOException;

public class SciviewBridge {

	//data source stuff
	final WindowManager mastodonWin;
	final int SOURCE_ID = 0;

	//data sink stuff
	final SciView sciviewWin;
	final SphereNodes sphereNodes;

	//sink scene graph structuring nodes
	final Node axesParent;
	final Sphere sphereParent;
	final Sphere volumeParent;

	final Volume redVolChannelNode;
	final Volume greenVolChannelNode;
	final Volume blueVolChannelNode;
	final RandomAccessibleInterval<UnsignedShortType> redVolChannelImg;
	final RandomAccessibleInterval<UnsignedShortType> greenVolChannelImg;
	final RandomAccessibleInterval<UnsignedShortType> blueVolChannelImg;

	public SciviewBridge(final WindowManager mastodonMainWindow,
	                     final SciView targetSciviewWindow)
	{
		this.mastodonWin = mastodonMainWindow;
		this.sciviewWin = targetSciviewWindow;

		//adjust the default scene's settings
		sciviewWin.getFloor().setVisible(false);
		sciviewWin.getLights().forEach(l -> {
			if (l.getName().startsWith("headli"))
				adjustHeadLight(l);
			else
				l.setVisible(false);
		});
		sciviewWin.getCamera().getChildren().forEach(l -> {
			if (l.getName().startsWith("headli") && l instanceof PointLight)
				adjustHeadLight((PointLight)l);
		});
		sciviewWin.addNode( new AmbientLight(0.05f, new Vector3f(1,1,1)) );
		sciviewWin.getCamera().spatial().move(30f,2);

		//add "root" with data axes
		this.axesParent = addDataAxes();
		sciviewWin.addChild( axesParent );

		//get necessary metadata - from image data
		final long[] volumeDims = mastodonWin.getAppModel()
				.getSharedBdvData().getSources().get(SOURCE_ID)
				.getSpimSource().getSource(0,0).dimensionsAsLongArray();
		final float[] volumePxRess = calculateDisplayVoxelRatioAlaBDV(mastodonWin.getAppModel()
				.getSharedBdvData().getSources().get(SOURCE_ID).getSpimSource());
		//
		final Vector3f volumeScale = new Vector3f(
				volumePxRess[0] * volumePxRess[0],
				volumePxRess[1] * volumePxRess[1],
				-1.0f * volumePxRess[2] * volumePxRess[2] );
		final Vector3f spotsScale = new Vector3f(
				volumeDims[0] * volumePxRess[0],
				volumeDims[1] * volumePxRess[1],
				volumeDims[2] * volumePxRess[2] );

		//volume stuff:
		redVolChannelImg = PlanarImgs.unsignedShorts(volumeDims);
		greenVolChannelImg = PlanarImgs.unsignedShorts(volumeDims);
		blueVolChannelImg = PlanarImgs.unsignedShorts(volumeDims);
		//
		spreadColor(redVolChannelImg,greenVolChannelImg,blueVolChannelImg,
				(RandomAccessibleInterval)mastodonWin.getAppModel()
						.getSharedBdvData().getSources().get(SOURCE_ID)
						.getSpimSource().getSource(0,0),
				new long[] {350,350,50},
				400,
				99999,
				new float[] {1,1,0} );

		volumeParent = null; //sciviewWin.addSphere();
		//volumeParent.setName( "VOLUME: "+mastodonMainWindow.projectManager.getProject().getProjectRoot().toString() );
		//
		final String commonNodeName = ": "+mastodonMainWindow.projectManager.getProject().getProjectRoot().toString();
		redVolChannelNode = sciviewWin.addVolume(redVolChannelImg, "RED VOL"+commonNodeName, new float[] {1,1,1});
		adjustAndPlaceVolumeIntoTheScene(redVolChannelNode, "Red.lut", volumeScale, 0, 50);
		//TODO display range can one learn from the coloring process
		//
		greenVolChannelNode = sciviewWin.addVolume(greenVolChannelImg, "GREEN VOL"+commonNodeName, new float[] {1,1,1});
		adjustAndPlaceVolumeIntoTheScene(greenVolChannelNode, "Green.lut", volumeScale, 0, 50);
		//
		blueVolChannelNode = sciviewWin.addVolume(blueVolChannelImg, "BLUE VOL"+commonNodeName, new float[] {1,1,1});
		adjustAndPlaceVolumeIntoTheScene(blueVolChannelNode, "Blue.lut", volumeScale, 0, 50);

		//spots stuff:
		sphereParent = sciviewWin.addSphere();
		//todo: make the parent node (sphere) invisible
		sphereParent.setName("SPOTS"+commonNodeName);
		//
		final float MAGIC_ONE_TENTH = 0.1f; //probably something inside scenery...
		spotsScale.mul( MAGIC_ONE_TENTH * redVolChannelNode.getPixelToWorldRatio() );

		sphereParent.spatial().setScale(spotsScale);
		sphereParent.spatial().setPosition(
				new Vector3f( volumeDims[0],volumeDims[1],volumeDims[2])
						.mul(-0.5f, 0.5f, 0.5f) //NB: y,z axes are flipped, see SphereNodes::setSphereNode()
						.mul( volumePxRess[0],volumePxRess[1],volumePxRess[2] ) //raw img coords to Mastodon internal coords
						.mul(spotsScale) ); //apply the same scaling as if "going through the SphereNodes"

		//add the sciview-side displaying handler for the spots
		this.sphereNodes = new SphereNodes(this.sciviewWin, sphereParent);
	}

	private void adjustAndPlaceVolumeIntoTheScene(final Volume v,
																 final String colorMapName,
	                                              final Vector3f scale,
	                                              final double displayRangeMin,
	                                              final double displayRangeMax) {
		sciviewWin.setColormap(v, colorMapName);
		v.spatial().setScale( scale );
		v.getConverterSetups().get(SOURCE_ID)
				.setDisplayRange(displayRangeMin,displayRangeMax);

		//make Bounding Box Grid invisible
		v.getChildren().forEach(n -> n.setVisible(false));

		//sciviewWin.deleteNode(v, true);
		//this.volumeParent.addChild(v);
	}

	public static
	float[] calculateDisplayVoxelRatioAlaBDV(final Source<?> forThisSource)
	{
		double[] vxAxisRatio = forThisSource.getVoxelDimensions().dimensionsAsDoubleArray();
		float[] finalRatio = new float[ vxAxisRatio.length ];

		double minLength = vxAxisRatio[0];
		for (int i = 1; i < vxAxisRatio.length; ++i) minLength = Math.min( vxAxisRatio[i], minLength );
		for (int i = 0; i < vxAxisRatio.length; ++i) finalRatio[i] = (float)(vxAxisRatio[i] / minLength);
		return finalRatio;
	}

	private static <T extends RealType<T>>
	void flatCopy(RandomAccessibleInterval<T> input,
	              RandomAccessibleInterval<T> output) {
		Cursor<T> reader = Views.flatIterable(input).cursor();
		Cursor<T> writer = Views.flatIterable(output).cursor();
		while (writer.hasNext())
			writer.next().set( reader.next() );
	}

	public static <T extends IntegerType<T>>
	void spreadColor(final RandomAccessibleInterval<T> redCh,
	                 final RandomAccessibleInterval<T> greenCh,
	                 final RandomAccessibleInterval<T> blueCh,
	                 final RandomAccessibleInterval<T> srcImg,
	                 final long[] pxCentre,
	                 final long maxSpatialDist,
	                 final int maxIntensityDist,
	                 final float[] rgbValue) {

		long[] min = new long[3];
		long[] max = new long[3];
		for (int d = 0; d < 3; ++d) {
			min[d] = Math.max(pxCentre[d] - maxSpatialDist, 0);
			max[d] = Math.min(pxCentre[d] + maxSpatialDist, srcImg.dimension(d)-1);
		}
		final Interval roi = new FinalInterval(min,max);

		Cursor<T> rc = Views.interval(redCh,roi).cursor();
		Cursor<T> gc = Views.interval(greenCh,roi).cursor();
		Cursor<T> bc = Views.interval(blueCh,roi).cursor();
		Cursor<T> si = Views.interval(srcImg,roi).cursor();
		final int sourceVal = srcImg.randomAccess().setPositionAndGet(pxCentre).getInteger();

		while (si.hasNext()) {
			rc.next(); gc.next(); bc.next();
			final int val = si.next().getInteger();
			if (Math.abs(sourceVal-val) <= maxIntensityDist) {
				//within ROI (by definition...) && within intensity range (the test above)
				rc.get().setReal( (float)val * rgbValue[0] );
				gc.get().setReal( (float)val * rgbValue[1] );
				bc.get().setReal( (float)val * rgbValue[2] );
			}
		}
	}

	// --------------------------------------------------------------------------
	public static void adjustHeadLight(final PointLight hl) {
		hl.setIntensity(1.5f);
		hl.spatial().setRotation(new Quaternionf().rotateY((float) Math.PI));
	}

	public static Node addDataAxes() {
		//add the data axes
		final float AXES_LINE_WIDTHS = 0.25f;
		final float AXES_LINE_LENGTHS = 5.f;
		//
		final Node axesParent = new Box( new Vector3f(0.1f) );
		axesParent.setName("Data Axes");
		//
		Cylinder c = new Cylinder(AXES_LINE_WIDTHS/2.0f, AXES_LINE_LENGTHS, 12);
		c.setName("Data x axis");
		c.material().setDiffuse( new Vector3f(1,0,0) );
		final float halfPI = (float)Math.PI / 2.0f;
		c.spatial().setRotation( new Quaternionf().rotateLocalZ(-halfPI) );
		axesParent.addChild(c);
		//
		c = new Cylinder(AXES_LINE_WIDTHS/2.0f, AXES_LINE_LENGTHS, 12);
		c.setName("Data y axis");
		c.material().setDiffuse( new Vector3f(0,1,0) );
		c.spatial().setRotation( new Quaternionf().rotateLocalZ((float)Math.PI) );
		axesParent.addChild(c);
		//
		c = new Cylinder(AXES_LINE_WIDTHS/2.0f, AXES_LINE_LENGTHS, 12);
		c.setName("Data z axis");
		c.material().setDiffuse( new Vector3f(0,0,1) );
		c.spatial().setRotation( new Quaternionf().rotateLocalX(-halfPI) );
		axesParent.addChild(c);

		return axesParent;
	}

	// --------------------------------------------------------------------------
	public MamutViewBdv openSyncedBDV() {
		final MamutViewBdv bdvWin = mastodonWin.createBigDataViewer();
		bdvWin.getFrame().setTitle("BDV linked to Sciview");

		//initial spots content:
		final int tp = bdvWin.getViewerPanelMamut().state().getCurrentTimepoint();
		//sphereNodes.setDataCentre( getSpotsAveragePos(tp) );
		updateSciviewContent(bdvWin);

		new BdvNotifier(
				() -> updateSciviewContent(bdvWin),
				() -> updateSciviewCamera(bdvWin),
				mastodonWin.getAppModel(),
				bdvWin);

		//temporary handlers mostly for testing
		keyboardHandlersForTestingForNow(bdvWin);
		return bdvWin;
	}

	private Vector3f getSpotsAveragePos(final int tp) {
		final float[] pos = new float[3];
		final float[] avg = {0,0,0};
		int cnt = 0;
		for (Spot s : mastodonWin.getAppModel().getModel().getSpatioTemporalIndex().getSpatialIndex(tp)) {
			s.localize(pos);
			avg[0] += pos[0];
			avg[1] += pos[1];
			avg[2] += pos[2];
			++cnt;
		}
		return new Vector3f(avg[0]/(float)cnt, avg[1]/(float)cnt, avg[2]/(float)cnt);
	}

	// --------------------------------------------------------------------------
	private TagSetStructure.TagSet recentTagSet;
	private GraphColorGenerator<Spot, Link> recentColorizer;
	private DefaultGraphColorGenerator<Spot, Link> noTScolorizer = new DefaultGraphColorGenerator<>();

	private void updateSciviewContent(final MamutViewBdv forThisBdv) {
		final int tp = forThisBdv.getViewerPanelMamut().state().getCurrentTimepoint();

		//NB: trying to avoid re-creating of new TagSetGraphColorGenerator objs with every new content rending
		GraphColorGenerator<Spot, Link> colorizer;
		final TagSetStructure.TagSet ts = forThisBdv.getColoringModel().getTagSet();
		if (ts != null) {
			if (ts != recentTagSet) {
				recentColorizer = new TagSetGraphColorGenerator<>(mastodonWin.getAppModel().getModel().getTagSetModel(), ts);
			}
			colorizer = recentColorizer;
		} else {
			colorizer = noTScolorizer;
		}
		recentTagSet = ts;
		sphereNodes.showTheseSpots(mastodonWin.getAppModel(), tp, colorizer);
	}

	private void updateSciviewCamera(final MamutViewBdv forThisBdv) {
		forThisBdv.getViewerPanelMamut().state().getViewerTransform(auxTransform);
		for (int r = 0; r < 3; ++r)
			for (int c = 0; c < 4; ++c)
				viewMatrix.set(c,r, (float)auxTransform.get(r,c));
		viewMatrix.getUnnormalizedRotation( viewRotation );

		final Camera.CameraSpatial camSpatial = sciviewWin.getCamera().spatial();
		viewRotation.y *= -1;
		viewRotation.z *= -1;
		camSpatial.setRotation( viewRotation );
		float dist = camSpatial.getPosition().length();
		camSpatial.setPosition( sciviewWin.getCamera().getForward().normalize().mul(-1f * dist) );
	}

	private final AffineTransform3D auxTransform = new AffineTransform3D();
	private final Matrix4f viewMatrix = new Matrix4f(1f,0,0,0, 0,1f,0,0, 0,0,1f,0, 0,0,0,1);
	private final Quaternionf viewRotation = new Quaternionf();

	// --------------------------------------------------------------------------
	private void keyboardHandlersForTestingForNow(final MamutViewBdv forThisBdv) {
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
