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
import java.util.function.BiConsumer;

public class SciviewBridge {

	//data source stuff
	final WindowManager mastodonWin;
	int SOURCE_ID = 0;
	int SOURCE_USED_RES_LEVEL = 0;
	static final float INTENSITY_CONTRAST = 2;      //raw data multiplied with this value and...
	static final float INTENSITY_NOT_ABOVE = 700;   //...then clamped not to be above this value and...
	static final float INTENSITY_GAMMA = 1.0f;      //...then gamma-corrected;

	static boolean INTENSITY_OF_COLORS_APPLY = true;//flag to enable/disable imprinting, with details just below:
	static final float SPOT_RADIUS_SCALE = 3.0f;    //the spreadColor() imprints spot this much larger than what it is in Mastodon
	static final float INTENSITY_OF_COLORS = 2100;  //and this max allowed value is used for the imprinting...

	static final float INTENSITY_RANGE_MAX = 2110;  //...because it plays nicely with this scaling range
	static final float INTENSITY_RANGE_MIN = 0;
	static boolean UPDATE_VOLUME_AUTOMATICALLY = true;
	static boolean UPDATE_VOLUME_VERBOSE_REPORTS = false;

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

	final Vector3f mastodonToImgCoordsTransfer;

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
		final Source<?> spimSource = mastodonWin.getAppModel().getSharedBdvData().getSources().get(SOURCE_ID).getSpimSource();
		final long[] volumeDims = spimSource.getSource(0,0).dimensionsAsLongArray();
		SOURCE_USED_RES_LEVEL = spimSource.getNumMipmapLevels() > 1 ? 1 : 0;
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

		//setup intensity display listeners that keep the ranges of the three volumes in sync
		// (but the change of one triggers listeners of the others (making each volume its ranges
		//  adjusted 3x times... luckily it doesn't start cycling/looping; perhaps switch to cascade?)
		redVolChannelNode.getConverterSetups().get(SOURCE_ID).setupChangeListeners().add( t -> {
			//System.out.println("RED informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
			greenVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			greenVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
			blueVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			blueVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
		});
		greenVolChannelNode.getConverterSetups().get(SOURCE_ID).setupChangeListeners().add( t -> {
			//System.out.println("GREEN informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
			redVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			redVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
			blueVolChannelNode.setMinDisplayRange((float)t.getDisplayRangeMin());
			blueVolChannelNode.setMaxDisplayRange((float)t.getDisplayRangeMax());
		});
		blueVolChannelNode.getConverterSetups().get(SOURCE_ID).setupChangeListeners().add( t -> {
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

	public static <T extends IntegerType<T>>
	void freshNewWhiteContent(final RandomAccessibleInterval<T> redCh,
	                          final RandomAccessibleInterval<T> greenCh,
	                          final RandomAccessibleInterval<T> blueCh,
	                          final RandomAccessibleInterval<T> srcImg) {
		final BiConsumer<T,T> intensityProcessor =
				(src, tgt) -> tgt.setReal( INTENSITY_NOT_ABOVE * Math.pow( //TODO, replace pow() with LUT for several gammas
						Math.min(INTENSITY_CONTRAST * src.getRealFloat(), INTENSITY_NOT_ABOVE) / INTENSITY_NOT_ABOVE,
						INTENSITY_GAMMA) );
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
		final float intensityScale = INTENSITY_OF_COLORS / INTENSITY_NOT_ABOVE;

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
				final float val = si.get().getInteger() * intensityScale;
				rc.get().setReal( val * rgbValue[0] );
				gc.get().setReal( val * rgbValue[1] );
				bc.get().setReal( val * rgbValue[2] );
				++cnt;
			}
		}

		if (UPDATE_VOLUME_VERBOSE_REPORTS)
			System.out.println("  colored "+cnt+" pixels in the interval ["
				+min[0]+","+min[1]+","+min[2]+"] -> ["
				+max[0]+","+max[1]+","+max[2]+"] @ ["
				+pxCentre[0]+","+pxCentre[1]+","+pxCentre[2]+"]");
	}

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

	private void updateSciviewContent(final MamutViewBdv forThisBdv) {
		final int tp = forThisBdv.getViewerPanelMamut().state().getCurrentTimepoint();
		updateSciviewColoring(forThisBdv);
		sphereNodes.showTheseSpots(mastodonWin.getAppModel(), tp,
				getCurrentColorizer(forThisBdv));
	}

	private int lastTpWhenVolumeWasUpdated = -1;
	private void updateSciviewColoring(final MamutViewBdv forThisBdv) {
		//only a wrapper that conditionally calls the workhorse method
		if (UPDATE_VOLUME_AUTOMATICALLY) {
			//HACK FOR NOW to prevent from redrawing of the same volumes
			//would be better if the BdvNotifier could tell us, instead of us detecting it here
			int currTP = forThisBdv.getViewerPanelMamut().state().getCurrentTimepoint();
			if (currTP != lastTpWhenVolumeWasUpdated) {
				lastTpWhenVolumeWasUpdated = currTP;
				updateSciviewColoringNow(forThisBdv);
			}
		}
	}

	private void updateSciviewColoringNow(final MamutViewBdv forThisBdv) {
		long[] pxCoord = new long[3];
		float[] spotCoord = new float[3];
		float[] color = new float[3];

		if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: started");
		final int tp = forThisBdv.getViewerPanelMamut().state().getCurrentTimepoint();
		RandomAccessibleInterval<?> srcRAI = mastodonWin.getAppModel()
				.getSharedBdvData().getSources().get(SOURCE_ID)
				.getSpimSource().getSource(tp, SOURCE_USED_RES_LEVEL);

		if (UPDATE_VOLUME_VERBOSE_REPORTS) System.out.println("COLORING: resets with new white content");
		freshNewWhiteContent(redVolChannelImg,greenVolChannelImg,blueVolChannelImg,
				(RandomAccessibleInterval)srcRAI);

		if (INTENSITY_OF_COLORS_APPLY) {
			GraphColorGenerator<Spot, Link> colorizer = getCurrentColorizer(forThisBdv);
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
	private void keyboardHandlersForTestingForNow(final MamutViewBdv forThisBdv) {
		//handlers
		final Behaviour clk_DEC_SPH = (ClickBehaviour) (x, y) -> sphereNodes.decreaseSphereScale();
		final Behaviour clk_INC_SPH = (ClickBehaviour) (x, y) -> sphereNodes.increaseSphereScale();
		final Behaviour clk_COLORING = (ClickBehaviour) (x, y) -> updateSciviewColoringNow(forThisBdv);
		final Behaviour clk_CLRNG_AUTO = (ClickBehaviour) (x, y) -> {
			UPDATE_VOLUME_AUTOMATICALLY = !UPDATE_VOLUME_AUTOMATICALLY;
			System.out.println("Volume updating auto mode: "+UPDATE_VOLUME_AUTOMATICALLY);
		};
		final Behaviour clk_CLRNG_ONOFF = (ClickBehaviour) (x, y) -> {
			INTENSITY_OF_COLORS_APPLY = !INTENSITY_OF_COLORS_APPLY;
			System.out.println("Volume spots imprinting enabled: "+INTENSITY_OF_COLORS_APPLY);
		};

		//register them
		final InputHandler handler = sciviewWin.getSceneryInputHandler();
		handler.addKeyBinding("decrease_initial_spheres_size", "O");
		handler.addBehaviour("decrease_initial_spheres_size", clk_DEC_SPH);
		handler.addKeyBinding("increase_initial_spheres_size", "shift O");
		handler.addBehaviour("increase_initial_spheres_size", clk_INC_SPH);
		handler.addKeyBinding("recolor_volume_now", "G");
		handler.addBehaviour("recolor_volume_now", clk_COLORING);
		handler.addKeyBinding("recolor_automatically", "shift G");
		handler.addBehaviour("recolor_automatically", clk_CLRNG_AUTO);
		handler.addKeyBinding("recolor_enabled", "ctrl G");
		handler.addBehaviour("recolor_enabled", clk_CLRNG_ONOFF);

		//deregister them when they are due
		forThisBdv.onClose(() -> {
			handler.removeKeyBinding("decrease_initial_spheres_size");
			handler.removeBehaviour("decrease_initial_spheres_size");
			handler.removeKeyBinding("increase_initial_spheres_size");
			handler.removeBehaviour("increase_initial_spheres_size");
			handler.removeKeyBinding("recolor_volume_now");
			handler.removeBehaviour("recolor_volume_now");
			handler.removeKeyBinding("recolor_automatically");
			handler.removeBehaviour("recolor_automatically");
			handler.removeKeyBinding("recolor_enabled");
			handler.removeBehaviour("recolor_enabled");
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
