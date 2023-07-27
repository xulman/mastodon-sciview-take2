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
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.planar.PlanarImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.util.SphereNodes;
import org.mastodon.model.tag.TagSetStructure;
import org.mastodon.ui.coloring.TagSetGraphColorGenerator;
import org.mastodon.ui.coloring.DefaultGraphColorGenerator;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.scijava.event.EventService;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import sc.iview.SciView;
import javax.swing.JFrame;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public class SciviewBridge {

	//data source stuff
	final WindowManager mastodonWin;
	int SOURCE_ID = 0;
	int SOURCE_USED_RES_LEVEL = 0;

	//NB: defaults do not alter the raw data values
	float INTENSITY_CONTRAST = 1;      //raw data multiplied with this value and...
	float INTENSITY_SHIFT = 0;         //...then added with this value and...
	float INTENSITY_CLAMP_AT_TOP = 65535;//...then assured/clamped not to be above this value and...
	float INTENSITY_GAMMA = 1.0f;      //...then, finally, gamma-corrected (squeezed through exp());

	boolean INTENSITY_OF_COLORS_APPLY = false;//flag to enable/disable imprinting, with details just below:
	boolean INTENSITY_OF_COLORS_BOOST = true;//flag to enable/disable boosting of rgb colors to the brightest possible, yet same hue
	float SPOT_RADIUS_SCALE = 3.0f;    //the spreadColor() imprints spot this much larger than what it is in Mastodon
	float INTENSITY_OF_COLORS = 2100;  //and this max allowed value is used for the imprinting...

	float INTENSITY_RANGE_MAX = 5000;  //...because it plays nicely with this scaling range
	float INTENSITY_RANGE_MIN = 0;
	boolean UPDATE_VOLUME_AUTOMATICALLY = true;
	boolean UPDATE_VOLUME_VERBOSE_REPORTS = false;

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("Mastodon-sciview bridge internal settings:\n");
		sb.append("   SOURCE_ID = "+ SOURCE_ID +"\n");
		sb.append("   SOURCE_USED_RES_LEVEL = "+ SOURCE_USED_RES_LEVEL +"\n");
		sb.append("   INTENSITY_CONTRAST = "+ INTENSITY_CONTRAST +"\n");
		sb.append("   INTENSITY_SHIFT = "+ INTENSITY_SHIFT +"\n");
		sb.append("   INTENSITY_CLAMP_AT_TOP = "+ INTENSITY_CLAMP_AT_TOP +"\n");
		sb.append("   INTENSITY_GAMMA = "+ INTENSITY_GAMMA +"\n");
		sb.append("   INTENSITY_OF_COLORS_APPLY = "+ INTENSITY_OF_COLORS_APPLY +"\n");
		sb.append("   SPOT_RADIUS_SCALE = "+ SPOT_RADIUS_SCALE +"\n");
		sb.append("   INTENSITY_OF_COLORS = "+ INTENSITY_OF_COLORS +"\n");
		sb.append("   INTENSITY_RANGE_MAX = "+ INTENSITY_RANGE_MAX +"\n");
		sb.append("   INTENSITY_RANGE_MIN = "+ INTENSITY_RANGE_MIN +"\n");
		sb.append("   UPDATE_VOLUME_AUTOMATICALLY = "+ UPDATE_VOLUME_AUTOMATICALLY +"\n");
		sb.append("   UPDATE_VOLUME_VERBOSE_REPORTS = "+ UPDATE_VOLUME_VERBOSE_REPORTS);
		return sb.toString();
	}

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
	final List<Node> volNodes; //shortcut for ops that operate on the three channels
	final RandomAccessibleInterval<UnsignedShortType> redVolChannelImg;
	final RandomAccessibleInterval<UnsignedShortType> greenVolChannelImg;
	final RandomAccessibleInterval<UnsignedShortType> blueVolChannelImg;

	final Vector3f mastodonToImgCoordsTransfer;

	public SciviewBridgeUI associatedUI = null;
	public JFrame uiFrame = null;

	/** exists here only for the demo (in tests folder), don't ever use in normal scenarios */
	SciviewBridge() {
		this.mastodonWin = null;
		this.sciviewWin = null;
		this.sphereNodes = null;
		this.axesParent = null;
		this.sphereParent = null;
		this.volumeParent = null;
		this.greenVolChannelNode = null;
		this.blueVolChannelNode = null;
		this.redVolChannelNode = null;
		this.volNodes = null;
		this.greenVolChannelImg = null;
		this.blueVolChannelImg = null;
		this.redVolChannelImg = null;
		this.mastodonToImgCoordsTransfer = null;
		this.detachedDPP_withOwnTime = new DPP_DetachedOwnTime(0,0);
	}

	public SciviewBridge(final WindowManager mastodonMainWindow,
	                     final SciView targetSciviewWindow)
	{
		this(mastodonMainWindow,0,0,targetSciviewWindow);
	}

	public SciviewBridge(final WindowManager mastodonMainWindow,
	                     final int sourceID, final int sourceResLevel,
	                     final SciView targetSciviewWindow)
	{
		this.mastodonWin = mastodonMainWindow;
		this.sciviewWin = targetSciviewWindow;
		detachedDPP_withOwnTime = new DPP_DetachedOwnTime(
				mastodonWin.getAppModel().getMinTimepoint(),
				mastodonWin.getAppModel().getMaxTimepoint() );

		//adjust the default scene's settings
		sciviewWin.setApplicationName("sciview for Mastodon: "
				+ mastodonMainWindow.projectManager.getProject().getProjectRoot().toString() );
		sciviewWin.toggleSidebar();
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
		sciviewWin.addNode( axesParent );

		//get necessary metadata - from image data
		SOURCE_ID = sourceID;
		SOURCE_USED_RES_LEVEL = sourceResLevel;
		final Source<?> spimSource = mastodonWin.getAppModel().getSharedBdvData().getSources().get(SOURCE_ID).getSpimSource();
		final long[] volumeDims = spimSource.getSource(0,0).dimensionsAsLongArray();
		//SOURCE_USED_RES_LEVEL = spimSource.getNumMipmapLevels() > 1 ? 1 : 0;
		final long[] volumeDims_usedResLevel = spimSource.getSource(0, SOURCE_USED_RES_LEVEL).dimensionsAsLongArray();
		final float[] volumeDownscale = new float[] {
				(float)volumeDims[0] / (float)volumeDims_usedResLevel[0],
				(float)volumeDims[1] / (float)volumeDims_usedResLevel[1],
				(float)volumeDims[2] / (float)volumeDims_usedResLevel[2] };
		System.out.println("downscale factors: "+volumeDownscale[0]+"x, "+volumeDownscale[1]+"x, "+volumeDownscale[2]+"x");
		//
		final float[] volumePxRess = calculateDisplayVoxelRatioAlaBDV(spimSource);
		System.out.println("pixel ratios: "+volumePxRess[0]+"x, "+volumePxRess[1]+"x, "+volumePxRess[2]+"x");
		//
		final Vector3f volumeScale = new Vector3f(
				volumePxRess[0] * volumePxRess[0] * volumeDownscale[0] * volumeDownscale[0],
				volumePxRess[1] * volumePxRess[1] * volumeDownscale[1] * volumeDownscale[1],
				-1.0f * volumePxRess[2] * volumePxRess[2] * volumeDownscale[2] * volumeDownscale[2] );
		final Vector3f spotsScale = new Vector3f(
				volumeDims[0] * volumePxRess[0],
				volumeDims[1] * volumePxRess[1],
				volumeDims[2] * volumePxRess[2] );

		//volume stuff:
		redVolChannelImg = PlanarImgs.unsignedShorts(volumeDims_usedResLevel);
		greenVolChannelImg = PlanarImgs.unsignedShorts(volumeDims_usedResLevel);
		blueVolChannelImg = PlanarImgs.unsignedShorts(volumeDims_usedResLevel);
		//
		freshNewWhiteContent(redVolChannelImg,greenVolChannelImg,blueVolChannelImg,
				(RandomAccessibleInterval)spimSource.getSource(0, SOURCE_USED_RES_LEVEL) );

		volumeParent = null; //sciviewWin.addSphere();
		//volumeParent.setName( "VOLUME: "+mastodonMainWindow.projectManager.getProject().getProjectRoot().toString() );
		//
		final String commonNodeName = ": "+mastodonMainWindow.projectManager.getProject().getProjectRoot().toString();
		redVolChannelNode = sciviewWin.addVolume(redVolChannelImg, "RED VOL"+commonNodeName, new float[] {1,1,1});
		adjustAndPlaceVolumeIntoTheScene(redVolChannelNode, "Red.lut", volumeScale, INTENSITY_RANGE_MIN, INTENSITY_RANGE_MAX);
		//TODO display range can one learn from the coloring process
		//
		greenVolChannelNode = sciviewWin.addVolume(greenVolChannelImg, "GREEN VOL"+commonNodeName, new float[] {1,1,1});
		adjustAndPlaceVolumeIntoTheScene(greenVolChannelNode, "Green.lut", volumeScale, INTENSITY_RANGE_MIN, INTENSITY_RANGE_MAX);
		//
		blueVolChannelNode = sciviewWin.addVolume(blueVolChannelImg, "BLUE VOL"+commonNodeName, new float[] {1,1,1});
		adjustAndPlaceVolumeIntoTheScene(blueVolChannelNode, "Blue.lut", volumeScale, INTENSITY_RANGE_MIN, INTENSITY_RANGE_MAX);
		//
		volNodes = Arrays.asList(redVolChannelNode,greenVolChannelNode,blueVolChannelNode);

		final int converterSetupID = SOURCE_ID < redVolChannelNode.getConverterSetups().size() ? SOURCE_ID : 0;
		//setup intensity display listeners that keep the ranges of the three volumes in sync
		// (but the change of one triggers listeners of the others (making each volume its ranges
		//  adjusted 3x times... luckily it doesn't start cycling/looping; perhaps switch to cascade?)
		redVolChannelNode.getConverterSetups().get(converterSetupID).setupChangeListeners().add( t -> {
			//System.out.println("RED informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
			greenVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			greenVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
			blueVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			blueVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
		});
		greenVolChannelNode.getConverterSetups().get(converterSetupID).setupChangeListeners().add( t -> {
			//System.out.println("GREEN informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
			redVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			redVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
			blueVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			blueVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
		});
		blueVolChannelNode.getConverterSetups().get(converterSetupID).setupChangeListeners().add( t -> {
			//System.out.println("BLUE informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
			redVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			redVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
			greenVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			greenVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
		});

		//spots stuff:
		sphereParent = sciviewWin.addSphere();
		//todo: make the parent node (sphere) invisible
		sphereParent.setName("SPOTS"+commonNodeName);
		//
		final float MAGIC_ONE_TENTH = 0.1f; //probably something inside scenery...
		spotsScale.mul( MAGIC_ONE_TENTH * redVolChannelNode.getPixelToWorldRatio() );
		mastodonToImgCoordsTransfer = new Vector3f(
				volumePxRess[0]*volumeDownscale[0],
				volumePxRess[1]*volumeDownscale[1],
				volumePxRess[2]*volumeDownscale[2] );

		sphereParent.spatial().setScale(spotsScale);
		sphereParent.spatial().setPosition(
				new Vector3f( volumeDims_usedResLevel[0],volumeDims_usedResLevel[1],volumeDims_usedResLevel[2])
						.mul(-0.5f, 0.5f, 0.5f) //NB: y,z axes are flipped, see SphereNodes::setSphereNode()
						.mul(mastodonToImgCoordsTransfer) //raw img coords to Mastodon internal coords
						.mul(spotsScale) ); //apply the same scaling as if "going through the SphereNodes"

		//add the sciview-side displaying handler for the spots
		this.sphereNodes = new SphereNodes(this.sciviewWin, sphereParent);
		sphereNodes.showTheseSpots(mastodonWin.getAppModel(), 0, noTScolorizer);

		//temporary handlers, originally for testing....
		registerKeyboardHandlers();
	}

	public EventService getEventService() {
		return sciviewWin.getScijavaContext().getService(EventService.class);
	}

	public void close() {
		detachControllingUI();
		deregisterKeyboardHandlers();
		System.out.println("Mastodon-sciview Bridge closing procedure: UI and keyboards handlers are removed now");

		sciviewWin.setActiveNode(axesParent);
		System.out.println("Mastodon-sciview Bridge closing procedure: focus shifted away from our nodes");

		//first make invisible, then remove...
		setVisibilityOfVolume(false);
		setVisibilityOfSpots(false);
		System.out.println("Mastodon-sciview Bridge closing procedure: our nodes made hidden");

		final long graceTimeForVolumeUpdatingInMS = 100;
		try {
			sciviewWin.deleteNode(redVolChannelNode, true);
			System.out.println("Mastodon-sciview Bridge closing procedure: red volume removed");
			Thread.sleep(graceTimeForVolumeUpdatingInMS);

			sciviewWin.deleteNode(greenVolChannelNode, true);
			System.out.println("Mastodon-sciview Bridge closing procedure: green volume removed");
			Thread.sleep(graceTimeForVolumeUpdatingInMS);

			sciviewWin.deleteNode(blueVolChannelNode, true);
			System.out.println("Mastodon-sciview Bridge closing procedure: blue volume removed");
			Thread.sleep(graceTimeForVolumeUpdatingInMS);

			sciviewWin.deleteNode(sphereParent, true);
			System.out.println("Mastodon-sciview Bridge closing procedure: spots were removed");
		} catch (InterruptedException e) { /* do nothing */ }

		sciviewWin.deleteNode(axesParent, true);
	}

	private void adjustAndPlaceVolumeIntoTheScene(final Volume v,
																 final String colorMapName,
	                                              final Vector3f scale,
	                                              final double displayRangeMin,
	                                              final double displayRangeMax) {
		sciviewWin.setColormap(v, colorMapName);
		v.spatial().setScale( scale );
		v.setMinDisplayRange((float)displayRangeMin);
		v.setMaxDisplayRange((float)displayRangeMax);

		//make Bounding Box Grid invisible
		v.getChildren().forEach(n -> n.setVisible(false));

		//FAILED to hook the volume nodes under the this.volumeParent node... so commented out for now
		//(one could construct Volume w/o sciview.addVolume(), but I find that way too difficult)
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

	<T extends IntegerType<T>>
	void freshNewWhiteContent(final RandomAccessibleInterval<T> redCh,
	                          final RandomAccessibleInterval<T> greenCh,
	                          final RandomAccessibleInterval<T> blueCh,
	                          final RandomAccessibleInterval<T> srcImg) {
		final BiConsumer<T,T> intensityProcessor
			= INTENSITY_GAMMA != 1.0 ?
				(src, tgt) -> tgt.setReal( INTENSITY_CLAMP_AT_TOP * Math.pow( //TODO, replace pow() with LUT for several gammas
						Math.min(INTENSITY_CONTRAST*src.getRealFloat() +INTENSITY_SHIFT, INTENSITY_CLAMP_AT_TOP) / INTENSITY_CLAMP_AT_TOP
						, INTENSITY_GAMMA) )
			:
				(src, tgt) -> tgt.setReal( Math.min(INTENSITY_CONTRAST*src.getRealFloat() +INTENSITY_SHIFT, INTENSITY_CLAMP_AT_TOP) );

		if (srcImg == null) System.out.println("freshNewWhiteContent(): srcImg is null !!!");
		if (redCh == null) System.out.println("freshNewWhiteContent(): redCh is null !!!");
		//
		//massage input data into the red channel
		LoopBuilder.setImages(srcImg,redCh)
				.flatIterationOrder()
				.multiThreaded()
				.forEachPixel(intensityProcessor);
		//clone the red channel into the remaining two, which for sure
		//were created the same way as the red, not needing flatIterationOrder()
		LoopBuilder.setImages(redCh,greenCh,blueCh)
				.multiThreaded()
				.forEachPixel((r,g,b) -> { g.set(r); b.set(r); });
	}

	<T extends IntegerType<T>>
	void spreadColor(final RandomAccessibleInterval<T> redCh,
	                 final RandomAccessibleInterval<T> greenCh,
	                 final RandomAccessibleInterval<T> blueCh,
	                 final RandomAccessibleInterval<T> srcImg,
	                 final long[] pxCentre,
	                 final double maxSpatialDist,
	                 final float[] rgbValue) {

		long[] min = new long[3];
		long[] max = new long[3];
		long[] maxDist = new long[] {
				//Mastodon coords -> (raw) image coords
				(long)(maxSpatialDist/mastodonToImgCoordsTransfer.x),
				(long)(maxSpatialDist/mastodonToImgCoordsTransfer.y),
				(long)(maxSpatialDist/mastodonToImgCoordsTransfer.z)
		};
		for (int d = 0; d < 3; ++d) {
			min[d] = Math.max(pxCentre[d] - maxDist[d], 0);
			max[d] = Math.min(pxCentre[d] + maxDist[d], srcImg.dimension(d)-1);
		}
		final Interval roi = new FinalInterval(min,max);

		Cursor<T> rc = Views.interval(redCh,roi).cursor();
		Cursor<T> gc = Views.interval(greenCh,roi).cursor();
		Cursor<T> bc = Views.interval(blueCh,roi).cursor();
		Cursor<T> si = Views.interval(srcImg,roi).localizingCursor();

		float[] pos = new float[3];
		final float maxDistSq = (float)(maxSpatialDist*maxSpatialDist);

		//to preserve a color, the r,g,b ratio must be kept (only mul()s, not add()s);
		//since data values are clamped to INTENSITY_NOT_ABOVE, we can stretch all
		//the way to INTENSITY_OF_COLORS (the brightest color displayed)
		final float intensityScale = INTENSITY_OF_COLORS / INTENSITY_CLAMP_AT_TOP;

		float[] usedColor;
		if (INTENSITY_OF_COLORS_BOOST) {
			float boostMul = Math.max( rgbValue[0], Math.max(rgbValue[1],rgbValue[2]) );
			boostMul = 1.f / boostMul;
			usedColor = rgbAux;
			usedColor[0] = boostMul * rgbValue[0];
			usedColor[1] = boostMul * rgbValue[1];
			usedColor[2] = boostMul * rgbValue[2];
			//NB: this operations is very very close to what
			//"rgb -> hsv -> set v=1.0 (highest) -> back to rgb"
			//would do, it non-decreases all rgb components
		} else {
			usedColor = rgbValue;
		}

		int cnt = 0;
		while (si.hasNext()) {
			rc.next(); gc.next(); bc.next();
			si.next();

			si.localize(pos);
			//(raw) image coords -> Mastodon coords
			pos[0] = (pos[0]-pxCentre[0]) * this.mastodonToImgCoordsTransfer.x;
			pos[1] = (pos[1]-pxCentre[1]) * this.mastodonToImgCoordsTransfer.y;
			pos[2] = (pos[2]-pxCentre[2]) * this.mastodonToImgCoordsTransfer.z;

			final double distSq = pos[0]*pos[0] + pos[1]*pos[1] + pos[2]*pos[2];
			if (distSq <= maxDistSq) {
				//we're within the ROI (spot)
				final float val = si.get().getRealFloat() * intensityScale;
				rc.get().setReal( val * usedColor[0] );
				gc.get().setReal( val * usedColor[1] );
				bc.get().setReal( val * usedColor[2] );
				++cnt;
			}
		}

		if (UPDATE_VOLUME_VERBOSE_REPORTS) {
			System.out.println("  colored "+cnt+" pixels in the interval ["
				+min[0]+","+min[1]+","+min[2]+"] -> ["
				+max[0]+","+max[1]+","+max[2]+"] @ ["
				+pxCentre[0]+","+pxCentre[1]+","+pxCentre[2]+"]");
			System.out.println("  boosted ["+rgbValue[0]+","+rgbValue[1]+","+rgbValue[2]
				+"] to ["+usedColor[0]+","+usedColor[1]+","+usedColor[2]+"]");
		}
	}
	final float[] rgbAux = new float[3];

	public long[] mastodonToImgCoord(final float[] mastodonCoord,
	                                 final long[] pxCoord) {
		pxCoord[0] = (long)(mastodonCoord[0] / this.mastodonToImgCoordsTransfer.x);
		pxCoord[1] = (long)(mastodonCoord[1] / this.mastodonToImgCoordsTransfer.y);
		pxCoord[2] = (long)(mastodonCoord[2] / this.mastodonToImgCoordsTransfer.z);
		return pxCoord;
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
		bdvWin.getFrame().setTitle("BDV linked to "+sciviewWin.getName());

		//initial spots content:
		final DPP_BdvAdapter bdvWinParamsProvider = new DPP_BdvAdapter(bdvWin);
		updateSciviewContent(bdvWinParamsProvider);

		new BdvNotifier(
				() -> updateSciviewContent(bdvWinParamsProvider),
				() -> updateSciviewCamera(bdvWin),
				mastodonWin.getAppModel(),
				bdvWin);

		return bdvWin;
	}

	// --------------------------------------------------------------------------
	private TagSetStructure.TagSet recentTagSet;
	private GraphColorGenerator<Spot, Link> recentColorizer;
	private DefaultGraphColorGenerator<Spot, Link> noTScolorizer = new DefaultGraphColorGenerator<>();

	private GraphColorGenerator<Spot,Link> getCurrentColorizer(final MamutViewBdv forThisBdv) {
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
		return colorizer;
	}

	//------------------------------
	interface DisplayParamsProvider {
		int getTimepoint();
		GraphColorGenerator<Spot,Link> getColorizer();
	}

	class DPP_BdvAdapter implements DisplayParamsProvider {
		final MamutViewBdv ofThisBdv;
		DPP_BdvAdapter(final MamutViewBdv forThisBdv) {
			ofThisBdv = forThisBdv;
		}
		@Override
		public int getTimepoint() {
			return ofThisBdv.getViewerPanelMamut().state().getCurrentTimepoint();
		}
		@Override
		public GraphColorGenerator<Spot, Link> getColorizer() {
			return getCurrentColorizer(ofThisBdv);
		}
	}

	class DPP_Detached implements DisplayParamsProvider {
		@Override
		public int getTimepoint() {
			return lastTpWhenVolumeWasUpdated;
		}
		@Override
		public GraphColorGenerator<Spot, Link> getColorizer() {
			return recentColorizer != null ? recentColorizer : noTScolorizer;
		}
	}
	class DPP_DetachedOwnTime implements DisplayParamsProvider {
		final int min,max;
		DPP_DetachedOwnTime(int min, int max) { this.min = min; this.max = max; }
		int timepoint = 0;
		public void setTimepoint(int tp) { timepoint = Math.max(min, Math.min(max, tp)); }
		public void prevTimepoint() { timepoint = Math.max(min, timepoint-1); }
		public void nextTimepoint() { timepoint = Math.min(max, timepoint+1); }
		@Override
		public int getTimepoint() {
			return timepoint;
		}
		@Override
		public GraphColorGenerator<Spot, Link> getColorizer() {
			return recentColorizer != null ? recentColorizer : noTScolorizer;
		}
	}
	//------------------------------

	void updateSciviewContent(final DisplayParamsProvider forThisBdv) {
		updateSciviewColoring(forThisBdv);
		sphereNodes.showTheseSpots(mastodonWin.getAppModel(),
				forThisBdv.getTimepoint(), forThisBdv.getColorizer());
	}

	private int lastTpWhenVolumeWasUpdated = 0;
	void updateSciviewColoring(final DisplayParamsProvider forThisBdv) {
		//only a wrapper that conditionally calls the workhorse method
		if (UPDATE_VOLUME_AUTOMATICALLY) {
			//HACK FOR NOW to prevent from redrawing of the same volumes
			//would be better if the BdvNotifier could tell us, instead of us detecting it here
			int currTP = forThisBdv.getTimepoint();
			if (currTP != lastTpWhenVolumeWasUpdated) {
				lastTpWhenVolumeWasUpdated = currTP;
				updateSciviewColoringNow(forThisBdv);
			}
		}
	}

	final DisplayParamsProvider detachedDPP_showsLastTimepoint = new DPP_Detached();
	void updateSciviewColoringNow() {
		updateSciviewColoringNow(detachedDPP_showsLastTimepoint);
	}

	void updateSciviewColoringNow(final DisplayParamsProvider forThisBdv) {
		long[] pxCoord = new long[3];
		float[] spotCoord = new float[3];
		float[] color = new float[3];

		if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: started");
		final int tp = forThisBdv.getTimepoint();
		RandomAccessibleInterval<?> srcRAI = mastodonWin.getAppModel()
				.getSharedBdvData().getSources().get(SOURCE_ID)
				.getSpimSource().getSource(tp, SOURCE_USED_RES_LEVEL);

		if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: resets with new white content");
		freshNewWhiteContent(redVolChannelImg,greenVolChannelImg,blueVolChannelImg,
				(RandomAccessibleInterval)srcRAI);

		if (INTENSITY_OF_COLORS_APPLY) {
			GraphColorGenerator<Spot, Link> colorizer = forThisBdv.getColorizer();
			for (Spot s : mastodonWin.getAppModel().getModel().getSpatioTemporalIndex().getSpatialIndex(tp)) {
				final int col = colorizer.color(s);
				if (col == 0) continue; //don't imprint black spots into the volume
				color[0] = ((col & 0x00FF0000) >> 16) / 255.f;
				color[1] = ((col & 0x0000FF00) >> 8 ) / 255.f;
				color[2] = ( col & 0x000000FF       ) / 255.f;
				if (UPDATE_VOLUME_VERBOSE_REPORTS)
					System.out.println("COLORING: colors spot "+s.getLabel()+" with color ["+color[0]+","+color[1]+","+color[2]+"]("+col+")");

				s.localize(spotCoord);
				spreadColor(redVolChannelImg,greenVolChannelImg,blueVolChannelImg,
					(RandomAccessibleInterval)srcRAI,
					mastodonToImgCoord(spotCoord,pxCoord),
					//NB: spot drawing is driven by image intensity, and thus
					//dark BG doesn't get colorized too much ('cause it is dark),
					//and thus it doesn't hurt if the spot is considered reasonably larger
					SPOT_RADIUS_SCALE * Math.sqrt(s.getBoundingSphereRadiusSquared()),
					color);
			}
		}

		try {
			final long graceTimeForVolumeUpdatingInMS = 50;
			if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: notified to update red volume");
			redVolChannelNode.volumeManager.notifyUpdate(redVolChannelNode);

			Thread.sleep(graceTimeForVolumeUpdatingInMS);
			if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: notified to update green volume");
			greenVolChannelNode.volumeManager.notifyUpdate(greenVolChannelNode);

			Thread.sleep(graceTimeForVolumeUpdatingInMS);
			if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: notified to update blue volume");
			blueVolChannelNode.volumeManager.notifyUpdate(blueVolChannelNode);
		} catch (InterruptedException e) { /* do nothing */ }

		if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: finished");
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
	public void setVisibilityOfVolume(final boolean state) {
		volNodes.forEach(v -> {
			v.setVisible(state);
			if (state) {
				v.getChildren().stream()
						.filter(c -> c.getName().startsWith("Bounding"))
						.forEach(c -> c.setVisible(false));
			}
		});
	}
	public void setVisibilityOfSpots(final boolean state) {
		sphereParent.setVisible(state);
		if (state) {
			sphereParent
					.getChildrenByName(SphereNodes.NAME_OF_NOT_USED_SPHERES)
					.forEach(s -> s.setVisible(false));
		}
	}

	public void focusSpot(final String name) {
		List<Node> nodes = sphereParent.getChildrenByName(name);
		if (nodes.size() > 0) sciviewWin.setActiveCenteredNode( nodes.get(0) );
	}

	final DPP_DetachedOwnTime detachedDPP_withOwnTime;
	public void showTimepoint(final int timepoint) {
		detachedDPP_withOwnTime.setTimepoint(timepoint);
		updateSciviewContent(detachedDPP_withOwnTime);
	}

	// --------------------------------------------------------------------------
	public static final String key_DEC_SPH = "O";
	public static final String key_INC_SPH = "shift O";
	public static final String key_COLORING = "G";
	public static final String key_CLRNG_AUTO = "shift G";
	public static final String key_CLRNG_ONOFF = "ctrl G";
	public static final String key_CTRL_WIN = "ctrl I";
	public static final String key_CTRL_INFO = "shift I";
	public static final String key_PREV_TP = "T";
	public static final String key_NEXT_TP = "shift T";

	public static final String desc_DEC_SPH = "decrease_initial_spheres_size";
	public static final String desc_INC_SPH = "increase_initial_spheres_size";
	public static final String desc_COLORING = "recolor_volume_now";
	public static final String desc_CLRNG_AUTO = "recolor_automatically";
	public static final String desc_CLRNG_ONOFF = "recolor_enabled";
	public static final String desc_CTRL_WIN = "controlling_window";
	public static final String desc_CTRL_INFO = "controlling_info";
	public static final String desc_PREV_TP = "show_previous_timepoint";
	public static final String desc_NEXT_TP = "show_next_timepoint";

	private void registerKeyboardHandlers() {
		//handlers
		final Behaviour clk_DEC_SPH = (ClickBehaviour) (x, y) -> {
			sphereNodes.decreaseSphereScale();
			updateUI();
		};
		final Behaviour clk_INC_SPH = (ClickBehaviour) (x, y) -> {
			sphereNodes.increaseSphereScale();
			updateUI();
		};
		final Behaviour clk_COLORING = (ClickBehaviour) (x, y) -> {
			updateSciviewColoringNow();
			updateUI();
		};
		final Behaviour clk_CLRNG_AUTO = (ClickBehaviour) (x, y) -> {
			UPDATE_VOLUME_AUTOMATICALLY = !UPDATE_VOLUME_AUTOMATICALLY;
			System.out.println("Volume updating auto mode: "+UPDATE_VOLUME_AUTOMATICALLY);
			updateUI();
		};
		final Behaviour clk_CLRNG_ONOFF = (ClickBehaviour) (x, y) -> {
			INTENSITY_OF_COLORS_APPLY = !INTENSITY_OF_COLORS_APPLY;
			System.out.println("Volume spots imprinting enabled: "+INTENSITY_OF_COLORS_APPLY);
			updateUI();
		};

		final Behaviour clk_CTRL_WIN = (ClickBehaviour) (x, y) -> this.createAndShowControllingUI();
		final Behaviour clk_CTRL_INFO = (ClickBehaviour) (x, y) -> System.out.println(this);

		final Behaviour clk_PREV_TP = (ClickBehaviour) (x, y) -> {
			detachedDPP_withOwnTime.prevTimepoint();
			updateSciviewContent(detachedDPP_withOwnTime);
		};
		final Behaviour clk_NEXT_TP = (ClickBehaviour) (x, y) -> {
			detachedDPP_withOwnTime.nextTimepoint();
			updateSciviewContent(detachedDPP_withOwnTime);
		};

		//register them
		final InputHandler handler = sciviewWin.getSceneryInputHandler();
		handler.addKeyBinding(desc_DEC_SPH, key_DEC_SPH);
		handler.addBehaviour( desc_DEC_SPH, clk_DEC_SPH);
		//
		handler.addKeyBinding(desc_INC_SPH, key_INC_SPH);
		handler.addBehaviour( desc_INC_SPH, clk_INC_SPH);
		//
		handler.addKeyBinding(desc_COLORING, key_COLORING);
		handler.addBehaviour( desc_COLORING, clk_COLORING);
		//
		handler.addKeyBinding(desc_CLRNG_AUTO, key_CLRNG_AUTO);
		handler.addBehaviour( desc_CLRNG_AUTO, clk_CLRNG_AUTO);
		//
		handler.addKeyBinding(key_CLRNG_ONOFF, key_CLRNG_ONOFF);
		handler.addBehaviour( key_CLRNG_ONOFF, clk_CLRNG_ONOFF);
		//
		handler.addKeyBinding(desc_CTRL_WIN, key_CTRL_WIN);
		handler.addBehaviour( desc_CTRL_WIN, clk_CTRL_WIN);
		//
		handler.addKeyBinding(desc_CTRL_INFO, key_CTRL_INFO);
		handler.addBehaviour( desc_CTRL_INFO, clk_CTRL_INFO);
		//
		handler.addKeyBinding(desc_PREV_TP, key_PREV_TP);
		handler.addBehaviour( desc_PREV_TP, clk_PREV_TP);
		//
		handler.addKeyBinding(desc_NEXT_TP, key_NEXT_TP);
		handler.addBehaviour( desc_NEXT_TP, clk_NEXT_TP);
	}

	private void deregisterKeyboardHandlers() {
		final InputHandler handler = sciviewWin.getSceneryInputHandler();
		handler.removeKeyBinding(desc_DEC_SPH);
		handler.removeBehaviour( desc_DEC_SPH);
		//
		handler.removeKeyBinding(desc_INC_SPH);
		handler.removeBehaviour( desc_INC_SPH);
		//
		handler.removeKeyBinding(desc_COLORING);
		handler.removeBehaviour( desc_COLORING);
		//
		handler.removeKeyBinding(desc_CLRNG_AUTO);
		handler.removeBehaviour( desc_CLRNG_AUTO);
		//
		handler.removeKeyBinding(key_CLRNG_ONOFF);
		handler.removeBehaviour( key_CLRNG_ONOFF);
		//
		handler.removeKeyBinding(desc_CTRL_WIN);
		handler.removeBehaviour( desc_CTRL_WIN);
		//
		handler.removeKeyBinding(desc_CTRL_INFO);
		handler.removeBehaviour( desc_CTRL_INFO);
		//
		handler.removeKeyBinding(desc_PREV_TP);
		handler.removeBehaviour( desc_PREV_TP);
		//
		handler.removeKeyBinding(desc_NEXT_TP);
		handler.removeBehaviour( desc_NEXT_TP);
	}

	public JFrame createAndShowControllingUI() {
		return createAndShowControllingUI("Controls for "+sciviewWin.getName());
	}

	public JFrame createAndShowControllingUI(final String windowTitle) {
		uiFrame = new JFrame(windowTitle);
		uiFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		associatedUI = new SciviewBridgeUI(this, uiFrame.getContentPane());
		uiFrame.pack();
		uiFrame.setVisible(true);
		return uiFrame;
	}

	public void detachControllingUI() {
		if (associatedUI != null) {
			associatedUI.deactivateAndForget();
			associatedUI = null;
		}
		if (uiFrame != null) {
			uiFrame.setVisible(false);
			uiFrame.dispose();
		}
	}

	void updateUI() {
		if (associatedUI == null) return;
		associatedUI.updatePaneValues();
	}
}
