package org.mastodon.mamut

import bdv.tools.brightness.ConverterSetup
import bdv.tools.brightness.ConverterSetup.SetupChangeListener
import bdv.viewer.Source
import graphics.scenery.*
import graphics.scenery.primitives.Cylinder
import graphics.scenery.volumes.Volume
import net.imglib2.FinalInterval
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.planar.PlanarImgs
import net.imglib2.loops.LoopBuilder
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.type.numeric.IntegerType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.view.Views
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.mastodon.mamut.model.Link
import org.mastodon.mamut.model.Spot
import org.mastodon.model.tag.TagSetStructure
import org.mastodon.ui.coloring.DefaultGraphColorGenerator
import org.mastodon.ui.coloring.GraphColorGenerator
import org.mastodon.ui.coloring.TagSetGraphColorGenerator
import org.scijava.event.EventService
import org.scijava.ui.behaviour.ClickBehaviour
import sc.iview.SciView
import util.SphereNodes
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import javax.swing.JFrame
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class SciviewBridge {
    //data source stuff
    val mastodonWin: WindowManager?
    var SOURCE_ID = 0
    var SOURCE_USED_RES_LEVEL = 0

    //NB: defaults do not alter the raw data values
    var INTENSITY_CONTRAST = 1f //raw data multiplied with this value and...
    var INTENSITY_SHIFT = 0f //...then added with this value and...
    var INTENSITY_CLAMP_AT_TOP = 65535f //...then assured/clamped not to be above this value and...
    var INTENSITY_GAMMA = 1.0f //...then, finally, gamma-corrected (squeezed through exp());
    var INTENSITY_OF_COLORS_APPLY = false //flag to enable/disable imprinting, with details just below:
    var INTENSITY_OF_COLORS_BOOST =
        true //flag to enable/disable boosting of rgb colors to the brightest possible, yet same hue
    var SPOT_RADIUS_SCALE = 3.0f //the spreadColor() imprints spot this much larger than what it is in Mastodon
    var INTENSITY_OF_COLORS = 2100f //and this max allowed value is used for the imprinting...
    var INTENSITY_RANGE_MAX = 5000f //...because it plays nicely with this scaling range
    var INTENSITY_RANGE_MIN = 0f
    var UPDATE_VOLUME_AUTOMATICALLY = true
    var UPDATE_VOLUME_VERBOSE_REPORTS = false
    override fun toString(): String {
        val sb = StringBuilder("Mastodon-sciview bridge internal settings:\n")
        sb.append("   SOURCE_ID = $SOURCE_ID\n")
        sb.append("   SOURCE_USED_RES_LEVEL = $SOURCE_USED_RES_LEVEL\n")
        sb.append("   INTENSITY_CONTRAST = $INTENSITY_CONTRAST\n")
        sb.append("   INTENSITY_SHIFT = $INTENSITY_SHIFT\n")
        sb.append("   INTENSITY_CLAMP_AT_TOP = $INTENSITY_CLAMP_AT_TOP\n")
        sb.append("   INTENSITY_GAMMA = $INTENSITY_GAMMA\n")
        sb.append("   INTENSITY_OF_COLORS_APPLY = $INTENSITY_OF_COLORS_APPLY\n")
        sb.append("   SPOT_RADIUS_SCALE = $SPOT_RADIUS_SCALE\n")
        sb.append("   INTENSITY_OF_COLORS = $INTENSITY_OF_COLORS\n")
        sb.append("   INTENSITY_RANGE_MAX = $INTENSITY_RANGE_MAX\n")
        sb.append("   INTENSITY_RANGE_MIN = $INTENSITY_RANGE_MIN\n")
        sb.append("   UPDATE_VOLUME_AUTOMATICALLY = $UPDATE_VOLUME_AUTOMATICALLY\n")
        sb.append("   UPDATE_VOLUME_VERBOSE_REPORTS = $UPDATE_VOLUME_VERBOSE_REPORTS")
        return sb.toString()
    }

    //data sink stuff
    val sciviewWin: SciView?
    val sphereNodes: SphereNodes?

    //sink scene graph structuring nodes
    val axesParent: Node?
    val sphereParent: Sphere?
    val volumeParent: Sphere?
    val redVolChannelNode: Volume?
    val greenVolChannelNode: Volume?
    val blueVolChannelNode: Volume?
    val volNodes //shortcut for ops that operate on the three channels
            : List<Node?>?
    val redVolChannelImg: RandomAccessibleInterval<UnsignedShortType>?
    val greenVolChannelImg: RandomAccessibleInterval<UnsignedShortType>?
    val blueVolChannelImg: RandomAccessibleInterval<UnsignedShortType>?
    val mastodonToImgCoordsTransfer: Vector3f?
    var associatedUI: SciviewBridgeUI? = null
    var uiFrame: JFrame? = null

    /** exists here only for the demo (in tests folder), don't ever use in normal scenarios  */
    internal constructor() {
        mastodonWin = null
        sciviewWin = null
        sphereNodes = null
        axesParent = null
        sphereParent = null
        volumeParent = null
        greenVolChannelNode = null
        blueVolChannelNode = null
        redVolChannelNode = null
        volNodes = null
        greenVolChannelImg = null
        blueVolChannelImg = null
        redVolChannelImg = null
        mastodonToImgCoordsTransfer = null
        detachedDPP_withOwnTime = DPP_DetachedOwnTime(0, 0)
    }

    constructor(
        mastodonMainWindow: WindowManager,
        targetSciviewWindow: SciView?
    ) : this(mastodonMainWindow, 0, 0, targetSciviewWindow)

    constructor(
        mastodonMainWindow: WindowManager,
        sourceID: Int, sourceResLevel: Int,
        targetSciviewWindow: SciView?
    ) {
        mastodonWin = mastodonMainWindow
        sciviewWin = targetSciviewWindow
        detachedDPP_withOwnTime = DPP_DetachedOwnTime(
            mastodonWin.appModel.minTimepoint,
            mastodonWin.appModel.maxTimepoint
        )

        //adjust the default scene's settings
        sciviewWin!!.applicationName = ("sciview for Mastodon: "
                + mastodonMainWindow.projectManager.project.projectRoot.toString())
        sciviewWin.toggleSidebar()
        sciviewWin.floor!!.visible = false
        sciviewWin.lights!!.forEach(Consumer { l: PointLight ->
            if (l.name.startsWith("headli")) adjustHeadLight(l) else l.visible = false
        })
        sciviewWin.camera!!.children.forEach(Consumer { l: Node ->
            if (l.name.startsWith("headli") && l is PointLight) adjustHeadLight(
                l
            )
        })
        sciviewWin.addNode(AmbientLight(0.05f, Vector3f(1f, 1f, 1f)))
        sciviewWin.camera!!.spatial().move(30f, 2)

        //add "root" with data axes
        axesParent = addDataAxes()
        sciviewWin.addNode<Node?>(axesParent)

        //get necessary metadata - from image data
        SOURCE_ID = sourceID
        SOURCE_USED_RES_LEVEL = sourceResLevel
        val spimSource = mastodonWin.appModel.sharedBdvData.sources[SOURCE_ID].spimSource
        val volumeDims = spimSource.getSource(0, 0).dimensionsAsLongArray()
        //SOURCE_USED_RES_LEVEL = spimSource.getNumMipmapLevels() > 1 ? 1 : 0;
        val volumedimsUsedreslevel = spimSource.getSource(0, SOURCE_USED_RES_LEVEL).dimensionsAsLongArray()
        val volumeDownscale = floatArrayOf(
            volumeDims[0].toFloat() / volumedimsUsedreslevel[0].toFloat(),
            volumeDims[1].toFloat() / volumedimsUsedreslevel[1].toFloat(),
            volumeDims[2].toFloat() / volumedimsUsedreslevel[2].toFloat()
        )
        println("downscale factors: " + volumeDownscale[0] + "x, " + volumeDownscale[1] + "x, " + volumeDownscale[2] + "x")
        //
        val volumePxRess = calculateDisplayVoxelRatioAlaBDV(spimSource)
        println("pixel ratios: " + volumePxRess[0] + "x, " + volumePxRess[1] + "x, " + volumePxRess[2] + "x")
        //
        val volumeScale = Vector3f(
            volumePxRess[0] * volumePxRess[0] * volumeDownscale[0] * volumeDownscale[0],
            volumePxRess[1] * volumePxRess[1] * volumeDownscale[1] * volumeDownscale[1],
            -1.0f * volumePxRess[2] * volumePxRess[2] * volumeDownscale[2] * volumeDownscale[2]
        )
        val spotsScale = Vector3f(
            volumeDims[0] * volumePxRess[0],
            volumeDims[1] * volumePxRess[1],
            volumeDims[2] * volumePxRess[2]
        )

        //volume stuff:
        redVolChannelImg = PlanarImgs.unsignedShorts(*volumedimsUsedreslevel)
        greenVolChannelImg = PlanarImgs.unsignedShorts(*volumedimsUsedreslevel)
        blueVolChannelImg = PlanarImgs.unsignedShorts(*volumedimsUsedreslevel)
        //
        freshNewWhiteContent(
            redVolChannelImg, greenVolChannelImg, blueVolChannelImg,
            spimSource.getSource(0, SOURCE_USED_RES_LEVEL) as RandomAccessibleInterval<UnsignedShortType>
        )
        volumeParent = null //sciviewWin.addSphere();
        //volumeParent.setName( "VOLUME: "+mastodonMainWindow.projectManager.getProject().getProjectRoot().toString() );
        //
        val commonNodeName = ": " + mastodonMainWindow.projectManager.project.projectRoot.toString()
        redVolChannelNode = sciviewWin.addVolume(redVolChannelImg, "RED VOL$commonNodeName", floatArrayOf(1f, 1f, 1f))
        adjustAndPlaceVolumeIntoTheScene(
            redVolChannelNode,
            "Red.lut",
            volumeScale,
            INTENSITY_RANGE_MIN.toDouble(),
            INTENSITY_RANGE_MAX.toDouble()
        )
        //TODO display range can one learn from the coloring process
        //
        greenVolChannelNode =
            sciviewWin.addVolume(greenVolChannelImg, "GREEN VOL$commonNodeName", floatArrayOf(1f, 1f, 1f))
        adjustAndPlaceVolumeIntoTheScene(
            greenVolChannelNode,
            "Green.lut",
            volumeScale,
            INTENSITY_RANGE_MIN.toDouble(),
            INTENSITY_RANGE_MAX.toDouble()
        )
        //
        blueVolChannelNode =
            sciviewWin.addVolume(blueVolChannelImg, "BLUE VOL$commonNodeName", floatArrayOf(1f, 1f, 1f))
        adjustAndPlaceVolumeIntoTheScene(
            blueVolChannelNode,
            "Blue.lut",
            volumeScale,
            INTENSITY_RANGE_MIN.toDouble(),
            INTENSITY_RANGE_MAX.toDouble()
        )
        //
        volNodes = Arrays.asList<Node?>(redVolChannelNode, greenVolChannelNode, blueVolChannelNode)
        val converterSetupID = if (SOURCE_ID < redVolChannelNode.converterSetups.size) SOURCE_ID else 0
        //setup intensity display listeners that keep the ranges of the three volumes in sync
        // (but the change of one triggers listeners of the others (making each volume its ranges
        //  adjusted 3x times... luckily it doesn't start cycling/looping; perhaps switch to cascade?)
        redVolChannelNode.converterSetups.get(converterSetupID).setupChangeListeners()
            .add(SetupChangeListener { t: ConverterSetup ->
                //System.out.println("RED informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
                greenVolChannelNode.minDisplayRange = t.displayRangeMin.toFloat()
                greenVolChannelNode.maxDisplayRange = t.displayRangeMax.toFloat()
                blueVolChannelNode.minDisplayRange = t.displayRangeMin.toFloat()
                blueVolChannelNode.maxDisplayRange = t.displayRangeMax.toFloat()
            })
        greenVolChannelNode.converterSetups.get(converterSetupID).setupChangeListeners()
            .add(SetupChangeListener { t: ConverterSetup ->
                //System.out.println("GREEN informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
                redVolChannelNode.minDisplayRange = t.displayRangeMin.toFloat()
                redVolChannelNode.maxDisplayRange = t.displayRangeMax.toFloat()
                blueVolChannelNode.minDisplayRange = t.displayRangeMin.toFloat()
                blueVolChannelNode.maxDisplayRange = t.displayRangeMax.toFloat()
            })
        blueVolChannelNode.converterSetups.get(converterSetupID).setupChangeListeners()
            .add(SetupChangeListener { t: ConverterSetup ->
                //System.out.println("BLUE informer: "+t.getDisplayRangeMin()+" to "+t.getDisplayRangeMax());
                redVolChannelNode.minDisplayRange = t.displayRangeMin.toFloat()
                redVolChannelNode.maxDisplayRange = t.displayRangeMax.toFloat()
                greenVolChannelNode.minDisplayRange = t.displayRangeMin.toFloat()
                greenVolChannelNode.maxDisplayRange = t.displayRangeMax.toFloat()
            })

        //spots stuff:
        sphereParent = sciviewWin.addSphere()
        //todo: make the parent node (sphere) invisible
        sphereParent.name = "SPOTS$commonNodeName"
        //
        val MAGIC_ONE_TENTH = 0.1f //probably something inside scenery...
        spotsScale.mul(MAGIC_ONE_TENTH * redVolChannelNode.pixelToWorldRatio)
        mastodonToImgCoordsTransfer = Vector3f(
            volumePxRess[0] * volumeDownscale[0],
            volumePxRess[1] * volumeDownscale[1],
            volumePxRess[2] * volumeDownscale[2]
        )
        sphereParent.spatial().scale = spotsScale
        sphereParent.spatial().position = Vector3f(
            volumedimsUsedreslevel[0].toFloat(),
            volumedimsUsedreslevel[1].toFloat(),
            volumedimsUsedreslevel[2].toFloat()
        )
            .mul(-0.5f, 0.5f, 0.5f) //NB: y,z axes are flipped, see SphereNodes::setSphereNode()
            .mul(mastodonToImgCoordsTransfer) //raw img coords to Mastodon internal coords
            .mul(spotsScale) //apply the same scaling as if "going through the SphereNodes"

        //add the sciview-side displaying handler for the spots
        sphereNodes = SphereNodes(sciviewWin, sphereParent)
        sphereNodes.showTheseSpots(mastodonWin.appModel, 0, noTScolorizer)

        //temporary handlers, originally for testing....
        registerKeyboardHandlers()
    }

    val eventService: EventService
        get() = sciviewWin!!.scijavaContext!!.getService(EventService::class.java)

    fun close() {
        detachControllingUI()
        deregisterKeyboardHandlers()
        println("Mastodon-sciview Bridge closing procedure: UI and keyboards handlers are removed now")
        sciviewWin!!.setActiveNode(axesParent)
        println("Mastodon-sciview Bridge closing procedure: focus shifted away from our nodes")

        //first make invisible, then remove...
        setVisibilityOfVolume(false)
        setVisibilityOfSpots(false)
        println("Mastodon-sciview Bridge closing procedure: our nodes made hidden")
        val graceTimeForVolumeUpdatingInMS: Long = 100
        try {
            sciviewWin.deleteNode(redVolChannelNode, true)
            println("Mastodon-sciview Bridge closing procedure: red volume removed")
            Thread.sleep(graceTimeForVolumeUpdatingInMS)
            sciviewWin.deleteNode(greenVolChannelNode, true)
            println("Mastodon-sciview Bridge closing procedure: green volume removed")
            Thread.sleep(graceTimeForVolumeUpdatingInMS)
            sciviewWin.deleteNode(blueVolChannelNode, true)
            println("Mastodon-sciview Bridge closing procedure: blue volume removed")
            Thread.sleep(graceTimeForVolumeUpdatingInMS)
            sciviewWin.deleteNode(sphereParent, true)
            println("Mastodon-sciview Bridge closing procedure: spots were removed")
        } catch (e: InterruptedException) { /* do nothing */
        }
        sciviewWin.deleteNode(axesParent, true)
    }

    private fun adjustAndPlaceVolumeIntoTheScene(
        v: Volume?,
        colorMapName: String,
        scale: Vector3f,
        displayRangeMin: Double,
        displayRangeMax: Double
    ) {
        sciviewWin!!.setColormap(v!!, colorMapName)
        v.spatial().scale = scale
        v.minDisplayRange = displayRangeMin.toFloat()
        v.maxDisplayRange = displayRangeMax.toFloat()

        //make Bounding Box Grid invisible
        v.children.forEach(Consumer { n: Node -> n.visible = false })

        //FAILED to hook the volume nodes under the this.volumeParent node... so commented out for now
        //(one could construct Volume w/o sciview.addVolume(), but I find that way too difficult)
        //sciviewWin.deleteNode(v, true);
        //this.volumeParent.addChild(v);
    }

    fun <T : IntegerType<T>?> freshNewWhiteContent(
        redCh: RandomAccessibleInterval<T>?,
        greenCh: RandomAccessibleInterval<T>?,
        blueCh: RandomAccessibleInterval<T>?,
        srcImg: RandomAccessibleInterval<T>?
    ) {
        val intensityProcessor = if (INTENSITY_GAMMA.toDouble() != 1.0) BiConsumer<T, T> { src: T, tgt: T ->
            tgt!!.setReal(
                INTENSITY_CLAMP_AT_TOP * ( //TODO, replace pow() with LUT for several gammas
                        min(
                            (INTENSITY_CONTRAST * src!!.realFloat + INTENSITY_SHIFT).toDouble(),
                            INTENSITY_CLAMP_AT_TOP.toDouble()
                        ) / INTENSITY_CLAMP_AT_TOP
                        ).pow(INTENSITY_GAMMA.toDouble())
            )
        } else BiConsumer<T, T> { src: T, tgt: T ->
            tgt!!.setReal(
                min(
                    (INTENSITY_CONTRAST * src!!.realFloat + INTENSITY_SHIFT).toDouble(),
                    INTENSITY_CLAMP_AT_TOP.toDouble()
                )
            )
        }
        if (srcImg == null) println("freshNewWhiteContent(): srcImg is null !!!")
        if (redCh == null) println("freshNewWhiteContent(): redCh is null !!!")
        //
        //massage input data into the red channel
        LoopBuilder.setImages(srcImg, redCh)
            .flatIterationOrder()
            .multiThreaded()
            .forEachPixel(intensityProcessor)
        //clone the red channel into the remaining two, which for sure
        //were created the same way as the red, not needing flatIterationOrder()
        LoopBuilder.setImages(redCh, greenCh, blueCh)
            .multiThreaded()
            .forEachPixel(LoopBuilder.TriConsumer { r: T, g: T, b: T ->
                g!!.set(r)
                b!!.set(r)
            })
    }

    fun <T : IntegerType<T>?> spreadColor(
        redCh: RandomAccessibleInterval<T>?,
        greenCh: RandomAccessibleInterval<T>?,
        blueCh: RandomAccessibleInterval<T>?,
        srcImg: RandomAccessibleInterval<T>,
        pxCentre: LongArray,
        maxSpatialDist: Double,
        rgbValue: FloatArray
    ) {
        val min = LongArray(3)
        val max = LongArray(3)
        val maxDist = longArrayOf(
            (maxSpatialDist / mastodonToImgCoordsTransfer!!.x).toLong(),
            (maxSpatialDist / mastodonToImgCoordsTransfer.y).toLong(),
            (maxSpatialDist / mastodonToImgCoordsTransfer.z).toLong()
        )
        for (d in 0..2) {
            min[d] = max((pxCentre[d] - maxDist[d]).toDouble(), 0.0).toLong()
            max[d] = min((pxCentre[d] + maxDist[d]).toDouble(), (srcImg.dimension(d) - 1).toDouble())
                .toLong()
        }
        val roi: Interval = FinalInterval(min, max)
        val rc = Views.interval(redCh, roi).cursor()
        val gc = Views.interval(greenCh, roi).cursor()
        val bc = Views.interval(blueCh, roi).cursor()
        val si = Views.interval(srcImg, roi).localizingCursor()
        val pos = FloatArray(3)
        val maxDistSq = (maxSpatialDist * maxSpatialDist).toFloat()

        //to preserve a color, the r,g,b ratio must be kept (only mul()s, not add()s);
        //since data values are clamped to INTENSITY_NOT_ABOVE, we can stretch all
        //the way to INTENSITY_OF_COLORS (the brightest color displayed)
        val intensityScale = INTENSITY_OF_COLORS / INTENSITY_CLAMP_AT_TOP
        val usedColor: FloatArray
        if (INTENSITY_OF_COLORS_BOOST) {
            var boostMul = max(rgbValue[0].toDouble(), max(rgbValue[1].toDouble(), rgbValue[2].toDouble()))
                .toFloat()
            boostMul = 1f / boostMul
            usedColor = rgbAux
            usedColor[0] = boostMul * rgbValue[0]
            usedColor[1] = boostMul * rgbValue[1]
            usedColor[2] = boostMul * rgbValue[2]
            //NB: this operations is very very close to what
            //"rgb -> hsv -> set v=1.0 (highest) -> back to rgb"
            //would do, it non-decreases all rgb components
        } else {
            usedColor = rgbValue
        }
        var cnt = 0
        while (si.hasNext()) {
            rc.next()
            gc.next()
            bc.next()
            si.next()
            si.localize(pos)
            //(raw) image coords -> Mastodon coords
            pos[0] = (pos[0] - pxCentre[0]) * mastodonToImgCoordsTransfer.x
            pos[1] = (pos[1] - pxCentre[1]) * mastodonToImgCoordsTransfer.y
            pos[2] = (pos[2] - pxCentre[2]) * mastodonToImgCoordsTransfer.z
            val distSq = (pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]).toDouble()
            if (distSq <= maxDistSq) {
                //we're within the ROI (spot)
                val `val` = si.get()!!.realFloat * intensityScale
                rc.get()!!.setReal(`val` * usedColor[0])
                gc.get()!!.setReal(`val` * usedColor[1])
                bc.get()!!.setReal(`val` * usedColor[2])
                ++cnt
            }
        }
        if (UPDATE_VOLUME_VERBOSE_REPORTS) {
            println(
                "  colored " + cnt + " pixels in the interval ["
                        + min[0] + "," + min[1] + "," + min[2] + "] -> ["
                        + max[0] + "," + max[1] + "," + max[2] + "] @ ["
                        + pxCentre[0] + "," + pxCentre[1] + "," + pxCentre[2] + "]"
            )
            println(
                "  boosted [" + rgbValue[0] + "," + rgbValue[1] + "," + rgbValue[2]
                        + "] to [" + usedColor[0] + "," + usedColor[1] + "," + usedColor[2] + "]"
            )
        }
    }

    val rgbAux = FloatArray(3)
    fun mastodonToImgCoord(
        mastodonCoord: FloatArray,
        pxCoord: LongArray
    ): LongArray {
        pxCoord[0] = (mastodonCoord[0] / mastodonToImgCoordsTransfer!!.x).toLong()
        pxCoord[1] = (mastodonCoord[1] / mastodonToImgCoordsTransfer.y).toLong()
        pxCoord[2] = (mastodonCoord[2] / mastodonToImgCoordsTransfer.z).toLong()
        return pxCoord
    }

    // --------------------------------------------------------------------------
    fun openSyncedBDV(): MamutViewBdv {
        val bdvWin = mastodonWin!!.createBigDataViewer()
        bdvWin.frame.setTitle("BDV linked to " + sciviewWin!!.getName())

        //initial spots content:
        val bdvWinParamsProvider = DPP_BdvAdapter(bdvWin)
        updateSciviewContent(bdvWinParamsProvider)
        BdvNotifier(
            { updateSciviewContent(bdvWinParamsProvider) },
            { updateSciviewCamera(bdvWin) },
            mastodonWin.appModel,
            bdvWin
        )
        return bdvWin
    }

    // --------------------------------------------------------------------------
    private var recentTagSet: TagSetStructure.TagSet? = null
    private var recentColorizer: GraphColorGenerator<Spot, Link>? = null
    private val noTScolorizer = DefaultGraphColorGenerator<Spot, Link>()
    private fun getCurrentColorizer(forThisBdv: MamutViewBdv): GraphColorGenerator<Spot, Link>? {
        //NB: trying to avoid re-creating of new TagSetGraphColorGenerator objs with every new content rending
        val colorizer: GraphColorGenerator<Spot, Link>?
        val ts = forThisBdv.coloringModel.tagSet
        if (ts != null) {
            if (ts !== recentTagSet) {
                recentColorizer = TagSetGraphColorGenerator(mastodonWin!!.appModel.model.tagSetModel, ts)
            }
            colorizer = recentColorizer
        } else {
            colorizer = noTScolorizer
        }
        recentTagSet = ts
        return colorizer
    }

    //------------------------------
    interface DisplayParamsProvider {
        val timepoint: Int
        val colorizer: GraphColorGenerator<Spot, Link>?
    }

    internal inner class DPP_BdvAdapter(val ofThisBdv: MamutViewBdv) : DisplayParamsProvider {
        override val timepoint: Int
            get() = ofThisBdv.viewerPanelMamut.state().currentTimepoint
        override val colorizer: GraphColorGenerator<Spot, Link>?
            get() = getCurrentColorizer(ofThisBdv)
    }

    internal inner class DPP_Detached : DisplayParamsProvider {
        override val timepoint: Int
            get() = lastTpWhenVolumeWasUpdated
        override val colorizer: GraphColorGenerator<Spot, Link>?
            get() = if (recentColorizer != null) recentColorizer else noTScolorizer
    }

    inner class DPP_DetachedOwnTime(val min: Int, val max: Int) : DisplayParamsProvider {

        override var timepoint = 0
            set(value) {
                field = max(min.toDouble(), min(max.toDouble(), value.toDouble())).toInt()
            }

        fun prevTimepoint() {
            timepoint = max(min.toDouble(), (timepoint - 1).toDouble()).toInt()
        }

        fun nextTimepoint() {
            timepoint = min(max.toDouble(), (timepoint + 1).toDouble()).toInt()
        }

        override val colorizer: GraphColorGenerator<Spot, Link>?
            get() = if (recentColorizer != null) recentColorizer else noTScolorizer
    }

    //------------------------------
    fun updateSciviewContent(forThisBdv: DisplayParamsProvider) {
        updateSciviewColoring(forThisBdv)
        sphereNodes!!.showTheseSpots(
            mastodonWin!!.appModel,
            forThisBdv.timepoint, forThisBdv.colorizer!!
        )
    }

    private var lastTpWhenVolumeWasUpdated = 0
    fun updateSciviewColoring(forThisBdv: DisplayParamsProvider) {
        //only a wrapper that conditionally calls the workhorse method
        if (UPDATE_VOLUME_AUTOMATICALLY) {
            //HACK FOR NOW to prevent from redrawing of the same volumes
            //would be better if the BdvNotifier could tell us, instead of us detecting it here
            val currTP = forThisBdv.timepoint
            if (currTP != lastTpWhenVolumeWasUpdated) {
                lastTpWhenVolumeWasUpdated = currTP
                updateSciviewColoringNow(forThisBdv)
            }
        }
    }

    val detachedDPP_showsLastTimepoint: DisplayParamsProvider = DPP_Detached()

    @JvmOverloads
    fun updateSciviewColoringNow(forThisBdv: DisplayParamsProvider = detachedDPP_showsLastTimepoint) {
        val pxCoord = LongArray(3)
        val spotCoord = FloatArray(3)
        val color = FloatArray(3)
        if (UPDATE_VOLUME_VERBOSE_REPORTS) println("COLORING: started")
        val tp = forThisBdv.timepoint
        val srcRAI = mastodonWin!!.appModel
            .sharedBdvData.sources[SOURCE_ID]
            .spimSource.getSource(tp, SOURCE_USED_RES_LEVEL)
        if (UPDATE_VOLUME_VERBOSE_REPORTS) println("COLORING: resets with new white content")
        freshNewWhiteContent(
            redVolChannelImg, greenVolChannelImg, blueVolChannelImg,
            srcRAI as RandomAccessibleInterval<UnsignedShortType>
        )
        if (INTENSITY_OF_COLORS_APPLY) {
            val colorizer = forThisBdv.colorizer
            for (s in mastodonWin.appModel.model.spatioTemporalIndex.getSpatialIndex(tp)) {
                val col = colorizer!!.color(s)
                if (col == 0) continue  //don't imprint black spots into the volume
                color[0] = (col and 0x00FF0000 shr 16) / 255f
                color[1] = (col and 0x0000FF00 shr 8) / 255f
                color[2] = (col and 0x000000FF) / 255f
                if (UPDATE_VOLUME_VERBOSE_REPORTS) println("COLORING: colors spot " + s.label + " with color [" + color[0] + "," + color[1] + "," + color[2] + "](" + col + ")")
                s.localize(spotCoord)
                spreadColor(
                    redVolChannelImg, greenVolChannelImg, blueVolChannelImg,
                    srcRAI,
                    mastodonToImgCoord(spotCoord, pxCoord),  //NB: spot drawing is driven by image intensity, and thus
                    //dark BG doesn't get colorized too much ('cause it is dark),
                    //and thus it doesn't hurt if the spot is considered reasonably larger
                    SPOT_RADIUS_SCALE * sqrt(s.boundingSphereRadiusSquared),
                    color
                )
            }
        }
        try {
            val graceTimeForVolumeUpdatingInMS: Long = 50
            if (UPDATE_VOLUME_VERBOSE_REPORTS) println("COLORING: notified to update red volume")
            redVolChannelNode!!.volumeManager.notifyUpdate(redVolChannelNode)
            Thread.sleep(graceTimeForVolumeUpdatingInMS)
            if (UPDATE_VOLUME_VERBOSE_REPORTS) println("COLORING: notified to update green volume")
            greenVolChannelNode!!.volumeManager.notifyUpdate(greenVolChannelNode)
            Thread.sleep(graceTimeForVolumeUpdatingInMS)
            if (UPDATE_VOLUME_VERBOSE_REPORTS) println("COLORING: notified to update blue volume")
            blueVolChannelNode!!.volumeManager.notifyUpdate(blueVolChannelNode)
        } catch (e: InterruptedException) { /* do nothing */
        }
        if (UPDATE_VOLUME_VERBOSE_REPORTS) println("COLORING: finished")
    }

    private fun updateSciviewCamera(forThisBdv: MamutViewBdv) {
        forThisBdv.viewerPanelMamut.state().getViewerTransform(auxTransform)
        for (r in 0..2) for (c in 0..3) viewMatrix[c, r] = auxTransform[r, c].toFloat()
        viewMatrix.getUnnormalizedRotation(viewRotation)
        val camSpatial = sciviewWin!!.camera!!.spatial()
        viewRotation.y *= -1f
        viewRotation.z *= -1f
        camSpatial.rotation = viewRotation
        val dist: Float = camSpatial.position.length()
        camSpatial.position = sciviewWin.camera!!.forward.normalize().mul(-1f * dist)
    }

    private val auxTransform = AffineTransform3D()
    private val viewMatrix = Matrix4f(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    private val viewRotation = Quaternionf()

    // --------------------------------------------------------------------------
    fun setVisibilityOfVolume(state: Boolean) {
        volNodes!!.forEach(Consumer { v: Node? ->
            v!!.visible = state
            if (state) {
                v.children.stream()
                    .filter { c: Node -> c.name.startsWith("Bounding") }
                    .forEach { c: Node -> c.visible = false }
            }
        })
    }

    fun setVisibilityOfSpots(state: Boolean) {
        sphereParent!!.visible = state
        if (state) {
            sphereParent
                .getChildrenByName(SphereNodes.NAME_OF_NOT_USED_SPHERES)
                .forEach(Consumer { s: Node -> s.visible = false })
        }
    }

    fun focusSpot(name: String?) {
        val nodes = sphereParent!!.getChildrenByName(name!!)
        if (nodes.isNotEmpty()) sciviewWin!!.setActiveCenteredNode(nodes[0])
    }

    val detachedDPP_withOwnTime: DPP_DetachedOwnTime
    fun showTimepoint(timepoint: Int) {
        detachedDPP_withOwnTime.timepoint = timepoint
        updateSciviewContent(detachedDPP_withOwnTime)
    }

    private fun registerKeyboardHandlers() {
        //handlers

        //register them
        val handler = sciviewWin!!.sceneryInputHandler
        handler!!.addKeyBinding(desc_DEC_SPH, key_DEC_SPH)
        handler.addBehaviour(desc_DEC_SPH, ClickBehaviour { _, _ ->
            sphereNodes!!.decreaseSphereScale()
            updateUI()
        })
        //
        handler.addKeyBinding(desc_INC_SPH, key_INC_SPH)
        handler.addBehaviour(desc_INC_SPH, ClickBehaviour { _, _ ->
            sphereNodes!!.increaseSphereScale()
            updateUI()
        })
        //
        handler.addKeyBinding(desc_COLORING, key_COLORING)
        handler.addBehaviour(desc_COLORING, ClickBehaviour { _, _ ->
            updateSciviewColoringNow()
            updateUI()
        })
        //
        handler.addKeyBinding(desc_CLRNG_AUTO, key_CLRNG_AUTO)
        handler.addBehaviour(desc_CLRNG_AUTO, ClickBehaviour { _, _ ->
            UPDATE_VOLUME_AUTOMATICALLY = !UPDATE_VOLUME_AUTOMATICALLY
            println("Volume updating auto mode: $UPDATE_VOLUME_AUTOMATICALLY")
            updateUI()
        })
        //
        handler.addKeyBinding(key_CLRNG_ONOFF, key_CLRNG_ONOFF)
        handler.addBehaviour(key_CLRNG_ONOFF, ClickBehaviour { _, _ ->
            INTENSITY_OF_COLORS_APPLY = !INTENSITY_OF_COLORS_APPLY
            println("Volume spots imprinting enabled: $INTENSITY_OF_COLORS_APPLY")
            updateUI()
        })
        //
        handler.addKeyBinding(desc_CTRL_WIN, key_CTRL_WIN)
        handler.addBehaviour(desc_CTRL_WIN, ClickBehaviour { _, _ -> createAndShowControllingUI() })
        //
        handler.addKeyBinding(desc_CTRL_INFO, key_CTRL_INFO)
        handler.addBehaviour(desc_CTRL_INFO, ClickBehaviour { _, _ -> println(this) })
        //
        handler.addKeyBinding(desc_PREV_TP, key_PREV_TP)
        handler.addBehaviour(desc_PREV_TP, ClickBehaviour { _, _ ->
            detachedDPP_withOwnTime.prevTimepoint()
            updateSciviewContent(detachedDPP_withOwnTime)
        })
        //
        handler.addKeyBinding(desc_NEXT_TP, key_NEXT_TP)
        handler.addBehaviour(desc_NEXT_TP, ClickBehaviour { _, _ ->
            detachedDPP_withOwnTime.nextTimepoint()
            updateSciviewContent(detachedDPP_withOwnTime)
        })
    }

    private fun deregisterKeyboardHandlers() {
        val handler = sciviewWin!!.sceneryInputHandler
        handler!!.removeKeyBinding(desc_DEC_SPH)
        handler.removeBehaviour(desc_DEC_SPH)
        //
        handler.removeKeyBinding(desc_INC_SPH)
        handler.removeBehaviour(desc_INC_SPH)
        //
        handler.removeKeyBinding(desc_COLORING)
        handler.removeBehaviour(desc_COLORING)
        //
        handler.removeKeyBinding(desc_CLRNG_AUTO)
        handler.removeBehaviour(desc_CLRNG_AUTO)
        //
        handler.removeKeyBinding(key_CLRNG_ONOFF)
        handler.removeBehaviour(key_CLRNG_ONOFF)
        //
        handler.removeKeyBinding(desc_CTRL_WIN)
        handler.removeBehaviour(desc_CTRL_WIN)
        //
        handler.removeKeyBinding(desc_CTRL_INFO)
        handler.removeBehaviour(desc_CTRL_INFO)
        //
        handler.removeKeyBinding(desc_PREV_TP)
        handler.removeBehaviour(desc_PREV_TP)
        //
        handler.removeKeyBinding(desc_NEXT_TP)
        handler.removeBehaviour(desc_NEXT_TP)
    }

    @JvmOverloads
    fun createAndShowControllingUI(windowTitle: String? = "Controls for " + sciviewWin!!.getName()): JFrame {
        return JFrame(windowTitle).apply {
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
            associatedUI = SciviewBridgeUI(this@SciviewBridge, contentPane)
            pack()
            isVisible = true
        }
    }

    fun detachControllingUI() {
        if (associatedUI != null) {
            associatedUI!!.deactivateAndForget()
            associatedUI = null
        }
        if (uiFrame != null) {
            uiFrame!!.isVisible = false
            uiFrame!!.dispose()
        }
    }

    fun updateUI() {
        if (associatedUI == null) return
        associatedUI!!.updatePaneValues()
    }

    companion object {
        fun calculateDisplayVoxelRatioAlaBDV(forThisSource: Source<*>): FloatArray {
            val vxAxisRatio = forThisSource.voxelDimensions.dimensionsAsDoubleArray()
            val finalRatio = FloatArray(vxAxisRatio.size)
            var minLength = vxAxisRatio[0]
            for (i in 1 until vxAxisRatio.size) minLength = min(vxAxisRatio[i], minLength)
            for (i in vxAxisRatio.indices) finalRatio[i] = (vxAxisRatio[i] / minLength).toFloat()
            return finalRatio
        }

        // --------------------------------------------------------------------------
        fun adjustHeadLight(hl: PointLight) {
            hl.intensity = 1.5f
            hl.spatial().rotation = Quaternionf().rotateY(Math.PI.toFloat())
        }

        fun addDataAxes(): Node {
            //add the data axes
            val AXES_LINE_WIDTHS = 0.25f
            val AXES_LINE_LENGTHS = 5f
            //
            val axesParent: Node = Box(Vector3f(0.1f))
            axesParent.name = "Data Axes"
            //
            var c = Cylinder(AXES_LINE_WIDTHS / 2.0f, AXES_LINE_LENGTHS, 12)
            c.name = "Data x axis"
            c.material().diffuse = Vector3f(1f, 0f, 0f)
            val halfPI = Math.PI.toFloat() / 2.0f
            c.spatial().rotation = Quaternionf().rotateLocalZ(-halfPI)
            axesParent.addChild(c)
            //
            c = Cylinder(AXES_LINE_WIDTHS / 2.0f, AXES_LINE_LENGTHS, 12)
            c.name = "Data y axis"
            c.material().diffuse = Vector3f(0f, 1f, 0f)
            c.spatial().rotation = Quaternionf().rotateLocalZ(Math.PI.toFloat())
            axesParent.addChild(c)
            //
            c = Cylinder(AXES_LINE_WIDTHS / 2.0f, AXES_LINE_LENGTHS, 12)
            c.name = "Data z axis"
            c.material().diffuse = Vector3f(0f, 0f, 1f)
            c.spatial().rotation = Quaternionf().rotateLocalX(-halfPI)
            axesParent.addChild(c)
            return axesParent
        }

        // --------------------------------------------------------------------------
        const val key_DEC_SPH = "O"
        const val key_INC_SPH = "shift O"
        const val key_COLORING = "G"
        const val key_CLRNG_AUTO = "shift G"
        const val key_CLRNG_ONOFF = "ctrl G"
        const val key_CTRL_WIN = "ctrl I"
        const val key_CTRL_INFO = "shift I"
        const val key_PREV_TP = "T"
        const val key_NEXT_TP = "shift T"
        const val desc_DEC_SPH = "decrease_initial_spheres_size"
        const val desc_INC_SPH = "increase_initial_spheres_size"
        const val desc_COLORING = "recolor_volume_now"
        const val desc_CLRNG_AUTO = "recolor_automatically"
        const val desc_CLRNG_ONOFF = "recolor_enabled"
        const val desc_CTRL_WIN = "controlling_window"
        const val desc_CTRL_INFO = "controlling_info"
        const val desc_PREV_TP = "show_previous_timepoint"
        const val desc_NEXT_TP = "show_next_timepoint"
    }
}
