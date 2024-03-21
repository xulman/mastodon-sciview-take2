package org.mastodon.mamut

import bdv.tools.brightness.ConverterSetup
import bdv.viewer.Source
import graphics.scenery.*
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.lazyLogger
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
import org.mastodon.mamut.views.bdv.MamutViewBdv
import org.mastodon.model.tag.TagSetStructure
import org.mastodon.ui.coloring.DefaultGraphColorGenerator
import org.mastodon.ui.coloring.GraphColorGenerator
import org.mastodon.ui.coloring.TagSetGraphColorGenerator
import org.scijava.event.EventService
import org.scijava.ui.behaviour.ClickBehaviour
import sc.iview.SciView
import util.SphereLinkNodes
import javax.swing.JFrame
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class SciviewBridge {
    private val logger by lazyLogger()
    //data source stuff
    val mastodon: ProjectModel
    var sourceID = 0
    var sourceResLevel = 0
    /** default intensity parameters */
    var intensity = Intensity()

    /** Collection of parameters for value and color intensity mapping */
    data class Intensity(
        var contrast: Float = 1.0f,         // raw data multiplier
        var shift: Float = 0.0f,            // raw data shift
        var clampTop: Float = 65535.0f,    // upper clamp value
        var gamma: Float = 1.0f,            // gamma correction with exp()
        var applyToColors: Boolean = false,   // flag to enable/disable imprinting
        var colorBoost: Boolean = true,       // flag to enable/disable boosting of rgb colors to the brightest possible
        var colorIntensity: Float = 2100f,  // max allowed value used for the imprinting
        var spotRadiusScale: Float = 3f,    // the spreadColor() imprints spot this much larger than what it is in Mastodon
        var rangeMin: Float = 0f,
        var rangeMax: Float = 5000f,
    )

    var updateVolAutomatically = true
    override fun toString(): String {
        val sb = StringBuilder("Mastodon-sciview bridge internal settings:\n")
        sb.append("   SOURCE_ID = $sourceID\n")
        sb.append("   SOURCE_USED_RES_LEVEL = $sourceResLevel\n")
        sb.append("   INTENSITY_CONTRAST = ${intensity.contrast}\n")
        sb.append("   INTENSITY_SHIFT = ${intensity.shift}\n")
        sb.append("   INTENSITY_CLAMP_AT_TOP = ${intensity.clampTop}\n")
        sb.append("   INTENSITY_GAMMA = ${intensity.gamma}\n")
        sb.append("   INTENSITY_OF_COLORS_APPLY = ${intensity.applyToColors}\n")
        sb.append("   SPOT_RADIUS_SCALE = ${intensity.spotRadiusScale}\n")
        sb.append("   INTENSITY_OF_COLORS = ${intensity.colorIntensity}\n")
        sb.append("   INTENSITY_RANGE_MAX = ${intensity.rangeMax}\n")
        sb.append("   INTENSITY_RANGE_MIN = ${intensity.rangeMin}\n")
        sb.append("   UPDATE_VOLUME_AUTOMATICALLY = $updateVolAutomatically\n")
        return sb.toString()
    }

    //data sink stuff
    val sciviewWin: SciView
    val sphereLinkNodes: SphereLinkNodes
    //sink scene graph structuring nodes
    val axesParent: Node?
    val sphereParent: Group
    val volumeParent: Sphere?
    val redVolChannelNode: Volume
    val greenVolChannelNode: Volume
    val blueVolChannelNode: Volume
    val volNodes //shortcut for ops that operate on the three channels
            : List<Node?>?
    val redVolChannelImg: RandomAccessibleInterval<UnsignedShortType>?
    val greenVolChannelImg: RandomAccessibleInterval<UnsignedShortType>?
    val blueVolChannelImg: RandomAccessibleInterval<UnsignedShortType>?
    var spimSource: Source<out Any>
    var isVolumeAutoAdjust = false
    val mastodonToImgCoordsTransfer: Vector3f
    var associatedUI: SciviewBridgeUI? = null
    var uiFrame: JFrame? = null

    constructor(
        mastodonMainWindow: ProjectModel,
        targetSciviewWindow: SciView
    ) : this(mastodonMainWindow, 0, 0, targetSciviewWindow)

    constructor(
        mastodonMainWindow: ProjectModel,
        sourceID: Int, sourceResLevel: Int,
        targetSciviewWindow: SciView
    ) {
        mastodon = mastodonMainWindow
        sciviewWin = targetSciviewWindow
        sciviewWin.setPushMode(true)
        detachedDPP_withOwnTime = DPP_DetachedOwnTime(
            mastodon.minTimepoint,
            mastodon.maxTimepoint
        )

        //adjust the default scene's settings
        sciviewWin.applicationName = ("sciview for Mastodon: " + mastodon.projectName)
        sciviewWin.toggleSidebar()
        sciviewWin.floor?.visible = false
        sciviewWin.lights?.forEach { l: PointLight ->
            if (l.name.startsWith("headli")) adjustHeadLight(l) else l.visible = false
        }
        sciviewWin.camera?.children?.forEach { l: Node ->
            if (l.name.startsWith("headli") && l is PointLight) adjustHeadLight(l)
        }
        sciviewWin.addNode(AmbientLight(0.05f, Vector3f(1f, 1f, 1f)))
        sciviewWin.camera?.spatial()?.move(30f, 2)

        //add "root" with data axes
        axesParent = addDataAxes()
        sciviewWin.addNode<Node?>(axesParent)

        //get necessary metadata - from image data
        this.sourceID = sourceID
        this.sourceResLevel = sourceResLevel
        spimSource = mastodon.sharedBdvData.sources[this.sourceID].spimSource
        val volumeDims = spimSource.getSource(0, 0).dimensionsAsLongArray()
        //SOURCE_USED_RES_LEVEL = spimSource.getNumMipmapLevels() > 1 ? 1 : 0;
        val volumeDimsUsedResLevel = spimSource.getSource(0, this.sourceResLevel).dimensionsAsLongArray()
        val volumeDownscale = floatArrayOf(
            volumeDims[0].toFloat() / volumeDimsUsedResLevel[0].toFloat(),
            volumeDims[1].toFloat() / volumeDimsUsedResLevel[1].toFloat(),
            volumeDims[2].toFloat() / volumeDimsUsedResLevel[2].toFloat()
        )
        logger.info("downscale factors: ${volumeDownscale[0]} x, ${volumeDownscale[1]} x, ${volumeDownscale[2]} x")
        //
        val voxelRes = getDisplayVoxelRatio(spimSource)
        logger.info("pixel ratios: ${voxelRes[0]} x, ${voxelRes[1]} x, ${voxelRes[2]} x")
        //
        val volumeScale = Vector3f(
            voxelRes[0] * voxelRes[0] * volumeDownscale[0] * volumeDownscale[0],
            voxelRes[1] * voxelRes[1] * volumeDownscale[1] * volumeDownscale[1],
            voxelRes[2] * voxelRes[2] * volumeDownscale[2] * volumeDownscale[2] * -1.0f
        )
        val spotsScale = Vector3f(
            volumeDims[0] * voxelRes[0],
            volumeDims[1] * voxelRes[1],
            volumeDims[2] * voxelRes[2]
        )

        //volume stuff:
        redVolChannelImg = PlanarImgs.unsignedShorts(*volumeDimsUsedResLevel)
        greenVolChannelImg = PlanarImgs.unsignedShorts(*volumeDimsUsedResLevel)
        blueVolChannelImg = PlanarImgs.unsignedShorts(*volumeDimsUsedResLevel)
        //
        freshNewGrayscaleContent(
            redVolChannelImg, greenVolChannelImg, blueVolChannelImg,
            spimSource.getSource(0, this.sourceResLevel) as RandomAccessibleInterval<UnsignedShortType>
        )
        volumeParent = null //sciviewWin.addSphere();
        //volumeParent.setName( "VOLUME: "+mastodonMainWindow.projectManager.getProject().getProjectRoot().toString() );
        //
        val commonNodeName = ": " + mastodon.projectName
        redVolChannelNode = sciviewWin.addVolume(redVolChannelImg, "RED VOL$commonNodeName", floatArrayOf(1f, 1f, 1f))
        adjustAndPlaceVolumeIntoTheScene(
            redVolChannelNode,
            "Red.lut",
            volumeScale,
            intensity.rangeMin,
            intensity.rangeMax
        )
        //TODO display range can one learn from the coloring process
        //
        greenVolChannelNode =
            sciviewWin.addVolume(greenVolChannelImg, "GREEN VOL$commonNodeName", floatArrayOf(1f, 1f, 1f))
        adjustAndPlaceVolumeIntoTheScene(
            greenVolChannelNode,
            "Green.lut",
            volumeScale,
            intensity.rangeMin,
            intensity.rangeMax
        )
        //
        blueVolChannelNode =
            sciviewWin.addVolume(blueVolChannelImg, "BLUE VOL$commonNodeName", floatArrayOf(1f, 1f, 1f))
        adjustAndPlaceVolumeIntoTheScene(
            blueVolChannelNode,
            "Blue.lut",
            volumeScale,
            intensity.rangeMin,
            intensity.rangeMax
        )
        //
        volNodes = listOf<Node?>(redVolChannelNode, greenVolChannelNode, blueVolChannelNode)
        val converterSetupID = if (this.sourceID < redVolChannelNode.converterSetups.size) this.sourceID else 0
        //setup intensity display listeners that keep the ranges of the three volumes in sync
        // (but the change of one triggers listeners of the others (making each volume its ranges
        //  adjusted 3x times... luckily it doesn't start cycling/looping; perhaps switch to cascade?)
        fun setupChangeListenerForNode(node: Volume, other1: Volume, other2: Volume): (ConverterSetup) -> Unit {
            return { t: ConverterSetup ->
                node.minDisplayRange = t.displayRangeMin.toFloat()
                node.maxDisplayRange = t.displayRangeMax.toFloat()
                other1.minDisplayRange = t.displayRangeMin.toFloat()
                other1.maxDisplayRange = t.displayRangeMax.toFloat()
                other2.minDisplayRange = t.displayRangeMin.toFloat()
                other2.maxDisplayRange = t.displayRangeMax.toFloat()
            }
        }

        val setupChangeListener = setupChangeListenerForNode(greenVolChannelNode, redVolChannelNode, blueVolChannelNode)

        redVolChannelNode.converterSetups[converterSetupID].setupChangeListeners().add(setupChangeListener)
        greenVolChannelNode.converterSetups[converterSetupID].setupChangeListeners().add(setupChangeListener)
        blueVolChannelNode.converterSetups[converterSetupID].setupChangeListeners().add(setupChangeListener)


        //spots stuff:
        sphereParent = Group()
        sphereParent.name = "SPOTS$commonNodeName"
        sciviewWin.addNode(sphereParent)
        val MAGIC_ONE_TENTH = 0.1f //probably something inside scenery...
        spotsScale.mul(MAGIC_ONE_TENTH * redVolChannelNode.pixelToWorldRatio)
        mastodonToImgCoordsTransfer = Vector3f(
            voxelRes[0] * volumeDownscale[0],
            voxelRes[1] * volumeDownscale[1],
            voxelRes[2] * volumeDownscale[2]
        )
        sphereParent.spatial().scale = spotsScale
        sphereParent.spatial().position = Vector3f(
            volumeDimsUsedResLevel[0].toFloat(),
            volumeDimsUsedResLevel[1].toFloat(),
            volumeDimsUsedResLevel[2].toFloat()
        )
            .mul(-0.5f, 0.5f, 0.5f) //NB: y,z axes are flipped, see SphereNodes::setSphereNode()
            .mul(mastodonToImgCoordsTransfer) //raw img coords to Mastodon internal coords
            .mul(spotsScale) //apply the same scaling as if "going through the SphereNodes"

        //add the sciview-side displaying handler for the spots
        sphereLinkNodes = SphereLinkNodes(sciviewWin, sphereParent)
        sphereLinkNodes.showTheseSpots(mastodon, 0, noTSColorizer)


        //temporary handlers, originally for testing....
        registerKeyboardHandlers()
    }

    val eventService: EventService?
        get() = sciviewWin.scijavaContext?.getService(EventService::class.java)

    fun close() {
        detachControllingUI()
        deregisterKeyboardHandlers()
        logger.info("Mastodon-sciview Bridge closing procedure: UI and keyboard handlers are removed now")
        sciviewWin.setActiveNode(axesParent)
        logger.info("Mastodon-sciview Bridge closing procedure: focus shifted away from our nodes")

        //first make invisible, then remove...
        setVisibilityOfVolume(false)
        setVisibilityOfSpots(false)
        logger.debug("Mastodon-sciview Bridge closing procedure: our nodes made hidden")
        val updateGraceTime = 100L // in ms
        try {
            sciviewWin.deleteNode(redVolChannelNode, true)
            logger.debug("Mastodon-sciview Bridge closing procedure: red volume removed")
            Thread.sleep(updateGraceTime)
            sciviewWin.deleteNode(greenVolChannelNode, true)
            logger.debug("Mastodon-sciview Bridge closing procedure: green volume removed")
            Thread.sleep(updateGraceTime)
            sciviewWin.deleteNode(blueVolChannelNode, true)
            logger.debug("Mastodon-sciview Bridge closing procedure: blue volume removed")
            Thread.sleep(updateGraceTime)
            sciviewWin.deleteNode(sphereParent, true)
            logger.debug("Mastodon-sciview Bridge closing procedure: spots were removed")
        } catch (e: InterruptedException) { /* do nothing */
        }
        sciviewWin.deleteNode(axesParent, true)
    }

    private fun adjustAndPlaceVolumeIntoTheScene(
        v: Volume?,
        colorMapName: String,
        scale: Vector3f,
        displayRangeMin: Float,
        displayRangeMax: Float
    ) {
        v?.let {
            sciviewWin.setColormap(it, colorMapName)
            it.spatial().scale = scale
            it.minDisplayRange = displayRangeMin
            it.maxDisplayRange = displayRangeMax
            //make Bounding Box Grid invisible
            it.children.forEach { n: Node -> n.visible = false }
        }

        //FAILED to hook the volume nodes under the this.volumeParent node... so commented out for now
        //(one could construct Volume w/o sciview.addVolume(), but I find that way too difficult)
        //sciviewWin.deleteNode(v, true);
        //this.volumeParent.addChild(v);
    }

    private var intensityBackup = intensity.copy()

    fun autoAdjustIntensity() {
        // toggle boolean state
        isVolumeAutoAdjust = !isVolumeAutoAdjust

        if (isVolumeAutoAdjust) {
            var maxVal = 0.0f
            val srcImg = spimSource.getSource(0, sourceResLevel) as RandomAccessibleInterval<UnsignedShortType>
            Views.iterable(srcImg).forEach { px -> maxVal = maxVal.coerceAtLeast(px.realFloat) }
            intensity.clampTop = 0.9f * maxVal //very fake 90% percentile...
            intensity.colorIntensity = 2.0f * maxVal
            intensity.rangeMin = maxVal * 0.15f
            intensity.rangeMax = maxVal * 0.75f
            //TODO: change MIN and MAX to proper values
            logger.debug("Clamp at ${intensity.clampTop}, Color intensity to ${intensity.colorIntensity}," +
                    " range min to ${intensity.rangeMin} and range max to ${intensity.rangeMax}")
            updateSVColoring(force = true)
            updateUI()
        } else {
            intensity = intensityBackup.copy()
            updateSVColoring(force = true)
            updateUI()
        }
    }

    fun <T : IntegerType<T>?> freshNewGrayscaleContent(
        redCh: RandomAccessibleInterval<T>?,
        greenCh: RandomAccessibleInterval<T>?,
        blueCh: RandomAccessibleInterval<T>?,
        srcImg: RandomAccessibleInterval<T>?
    ) {

        //TODO would be great if the following two functions would be outside this function, and would therefore
        //     be created only once (not created again with every new call of this function like it is now)
        val gammaEnabledIntensityProcessor: (T,T) -> Unit =
            { src: T, tgt: T -> tgt?.setReal(
                    intensity.clampTop * ( //TODO, replace pow() with LUT for several gammas
                            min(
                                intensity.contrast * src!!.realFloat + intensity.shift,
                                intensity.clampTop
                            ) / intensity.clampTop
                        ).pow(intensity.gamma)
                    )
            }
        val noGammaIntensityProcessor: (T,T) -> Unit =
            { src: T, tgt: T -> tgt?.setReal(
                        min(
                            // TODO This needs to incorporate INTENSITY_RANGE_MIN and MAX
                            intensity.contrast * src!!.realFloat + intensity.shift,
                            intensity.clampTop
                        )
                    )
            }
        //choose one processor for the downstream job;
        //it is seemingly a long code but it does the if-decision only once now
        val intensityProcessor = if (intensity.gamma != 1.0f)
            gammaEnabledIntensityProcessor else noGammaIntensityProcessor

        if (srcImg == null) logger.warn("freshNewWhiteContent(): srcImg is null !!!")
        if (redCh == null) logger.warn("freshNewWhiteContent(): redCh is null !!!")

        //massage input data into the red channel (LB guarantees that counterparting pixels are accessed)
        LoopBuilder.setImages(srcImg, redCh)
            .multiThreaded()
            .forEachPixel(intensityProcessor)
        //clone the red channel into the remaining two
        LoopBuilder.setImages(redCh, greenCh, blueCh)
            .multiThreaded()
            .forEachPixel(LoopBuilder.TriConsumer { r: T, g: T, b: T ->
                g?.set(r)
                b?.set(r)
            })
    }

    val posAuxArray = FloatArray(3)
    val coloringROIMin = LongArray(3)
    val coloringROIMax = LongArray(3) // NB: only works for single threaded coloring!
    fun <T : IntegerType<T>?> spreadColor(
        redCh: RandomAccessibleInterval<T>?,
        greenCh: RandomAccessibleInterval<T>?,
        blueCh: RandomAccessibleInterval<T>?,
        srcImg: RandomAccessibleInterval<T>,
        pxCentre: Vector3f,
        maxSpatialDist: Double,
        rgbValue: Vector3f
    ) {

        val maxDist = longArrayOf(
            (maxSpatialDist / mastodonToImgCoordsTransfer.x).toLong(),
            (maxSpatialDist / mastodonToImgCoordsTransfer.y).toLong(),
            (maxSpatialDist / mastodonToImgCoordsTransfer.z).toLong()
        )
        for (d in 0..2) {
            coloringROIMin[d] = max((pxCentre[d] - maxDist[d]).toDouble(), 0.0).toLong()
            coloringROIMax[d] = min((pxCentre[d] + maxDist[d]).toDouble(), (srcImg.dimension(d) - 1).toDouble())
                .toLong()
        }
        val roi: Interval = FinalInterval(coloringROIMin, coloringROIMax)
        val rc = Views.interval(redCh, roi).cursor()
        val gc = Views.interval(greenCh, roi).cursor()
        val bc = Views.interval(blueCh, roi).cursor()
        val si = Views.interval(srcImg, roi).localizingCursor()
        val pos = Vector3f()
        val maxDistSq = (maxSpatialDist * maxSpatialDist).toFloat()

        //to preserve a color, the r,g,b ratio must be kept (only mul()s, not add()s);
        //since data values are clamped to INTENSITY_NOT_ABOVE, we can stretch all
        //the way to INTENSITY_OF_COLORS (the brightest color displayed)
        val intensityScale = intensity.colorIntensity / intensity.clampTop

        val usedColor = if (intensity.colorBoost) {
            //NB: normalizes all color components so that the maximum is at 1
            rgbValue.div(rgbValue[rgbValue.maxComponent()])
        } else {
            rgbValue
        }
        var count = 0
        var distSq: Float
        var colorVal: Double
        while (si.hasNext()) {
            rc.next()
            gc.next()
            bc.next()
            si.next()
            si.localize(posAuxArray)
            pos.set(posAuxArray)
            //(raw) image coords -> Mastodon coords
            distSq = pos.sub(pxCentre).mul(mastodonToImgCoordsTransfer).lengthSquared()
            if (distSq <= maxDistSq) {
                //we're within the ROI (spot)
                colorVal = si.get().realDouble * intensityScale
                rc.get().setReal(colorVal * usedColor[0])
                gc.get().setReal(colorVal * usedColor[1])
                bc.get().setReal(colorVal * usedColor[2])
                ++count
            }
        }

        logger.debug(
            "colored $count pixels in the interval" +
                    "[${coloringROIMin[0]}, ${coloringROIMin[1]}, ${coloringROIMin[2]}]" +
                    " -> [${coloringROIMax[0]}, ${coloringROIMax[1]}, ${coloringROIMax[2]}]" +
                    " @ [${pxCentre[0]}, ${pxCentre[1]}, ${pxCentre[2]}]"
        )
        logger.debug(
            "boosted [${rgbValue[0]}, ${rgbValue[1]}, ${rgbValue[2]}]" +
                    " to [${usedColor[0]}, ${usedColor[1]}, ${usedColor[2]}]"
        )
    }

    fun mastodonToImgCoord(inputMastodonCoord: FloatArray, destVec: Vector3f): Vector3f {
        //yes, ugly... but avoids new allocations, yet can be still used "inplace" or "chaining"
        destVec.set(
            inputMastodonCoord[0] / mastodonToImgCoordsTransfer.x,
            inputMastodonCoord[1] / mastodonToImgCoordsTransfer.y,
            inputMastodonCoord[2] / mastodonToImgCoordsTransfer.z
        )
        return destVec
    }

    // --------------------------------------------------------------------------
    fun openSyncedBDV(): MamutViewBdv {
        val bdvWin = mastodon.windowManager.createView(MamutViewBdv::class.java)
        bdvWin.frame.setTitle("BDV linked to ${sciviewWin.getName()}")

        //initial spots content:
        val bdvWinParamsProvider = DPP_BdvAdapter(bdvWin)
        updateSciviewContent(bdvWinParamsProvider)
        BdvNotifier(
            { updateSciviewContent(bdvWinParamsProvider) },
            { updateSciviewCamera(bdvWin) },
            mastodon,
            bdvWin
        )
        return bdvWin
    }

    // --------------------------------------------------------------------------
    private var recentTagSet: TagSetStructure.TagSet? = null
    private var recentColorizer: GraphColorGenerator<Spot, Link>? = null
    private val noTSColorizer = DefaultGraphColorGenerator<Spot, Link>()
    private fun getCurrentColorizer(forThisBdv: MamutViewBdv): GraphColorGenerator<Spot, Link> {
        //NB: trying to avoid re-creating of new TagSetGraphColorGenerator objs with every new content rending
        val colorizer: GraphColorGenerator<Spot, Link>
        val ts = forThisBdv.coloringModel.tagSet
        if (ts != null) {
            if (ts !== recentTagSet) {
                recentColorizer = TagSetGraphColorGenerator(mastodon.model.tagSetModel, ts)
            }
            colorizer = recentColorizer!!
        } else {
            colorizer = noTSColorizer
        }
        recentTagSet = ts
        return colorizer
    }

    //------------------------------
    interface DisplayParamsProvider {
        val timepoint: Int
        val colorizer: GraphColorGenerator<Spot, Link>
    }

    internal inner class DPP_BdvAdapter(val ofThisBdv: MamutViewBdv) : DisplayParamsProvider {
        override val timepoint: Int
            get() = ofThisBdv.viewerPanelMamut.state().currentTimepoint
        override val colorizer: GraphColorGenerator<Spot, Link>
            get() = getCurrentColorizer(ofThisBdv)
    }

    internal inner class DPP_Detached : DisplayParamsProvider {
        override val timepoint: Int
            get() = lastTpWhenVolumeWasUpdated
        override val colorizer: GraphColorGenerator<Spot, Link>
            get() = recentColorizer ?: noTSColorizer
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

        override val colorizer: GraphColorGenerator<Spot, Link>
            get() = recentColorizer ?: noTSColorizer
    }

    //------------------------------
    fun updateSciviewContent(forThisBdv: DisplayParamsProvider) {
        updateSVColoring(forThisBdv)
        sphereLinkNodes.showTheseSpots(
            mastodon,
            forThisBdv.timepoint, forThisBdv.colorizer
        )
    }

    private var lastTpWhenVolumeWasUpdated = 0
    val detachedDPP_showsLastTimepoint: DisplayParamsProvider = DPP_Detached()

    @JvmOverloads
    fun updateSVColoring(
        forThisBdv: DisplayParamsProvider = detachedDPP_showsLastTimepoint,
        force: Boolean = false
    ) {

        if (updateVolAutomatically || force) {
            val currTP = forThisBdv.timepoint

            if (currTP != lastTpWhenVolumeWasUpdated) {
                lastTpWhenVolumeWasUpdated = currTP

                val spotCoord = Vector3f()
                val color = Vector3f()
                logger.debug("COLORING: started")
                val tp = forThisBdv.timepoint
                val srcRAI = mastodon
                    .sharedBdvData.sources[sourceID]
                    .spimSource.getSource(tp, sourceResLevel)
                logger.debug("COLORING: resets with new white content")
                freshNewGrayscaleContent(
                    redVolChannelImg, greenVolChannelImg, blueVolChannelImg,
                    srcRAI as RandomAccessibleInterval<UnsignedShortType>
                )
                if (intensity.applyToColors) {
                    val colorizer = forThisBdv.colorizer
                    for (s in mastodon.model.spatioTemporalIndex.getSpatialIndex(tp)) {
                        val col = colorizer.color(s)
                        if (col == 0) continue  //don't imprint black spots into the volume
                        color.x = (col and 0x00FF0000 shr 16) / 255f
                        color.y = (col and 0x0000FF00 shr 8) / 255f
                        color.z = (col and 0x000000FF) / 255f
                        logger.debug(
                            "COLORING: colors spot ${s.label} with color [" +
                                    "${color[0]}, ${color[1]}, ${color[2]}]($col)"
                            )
                        s.localize(posAuxArray)
                        spreadColor(
                            redVolChannelImg, greenVolChannelImg, blueVolChannelImg,
                            srcRAI,
                            mastodonToImgCoord(posAuxArray, spotCoord),
                            //NB: spot drawing is driven by image intensity, and thus
                            //dark BG doesn't get colorized too much ('cause it is dark),
                            //and thus it doesn't hurt if the spot is considered reasonably larger
                            intensity.spotRadiusScale * sqrt(s.boundingSphereRadiusSquared),
                            color
                        )
                    }
                }
                try {
                    val graceTimeForVolumeUpdatingInMS: Long = 50
                    logger.debug("COLORING: notified to update red volume")
                    redVolChannelNode.volumeManager.notifyUpdate(redVolChannelNode)
                    Thread.sleep(graceTimeForVolumeUpdatingInMS)
                    logger.debug("COLORING: notified to update green volume")
                    greenVolChannelNode.volumeManager.notifyUpdate(greenVolChannelNode)
                    Thread.sleep(graceTimeForVolumeUpdatingInMS)
                    logger.debug("COLORING: notified to update blue volume")
                    blueVolChannelNode.volumeManager.notifyUpdate(blueVolChannelNode)
                } catch (e: InterruptedException) { /* do nothing */ }
                logger.debug("COLORING: finished")
            }
        }
    }

    private fun updateSciviewCamera(forThisBdv: MamutViewBdv) {
        forThisBdv.viewerPanelMamut.state().getViewerTransform(auxTransform)
        for (r in 0..2) for (c in 0..3) viewMatrix[c, r] = auxTransform[r, c].toFloat()
        viewMatrix.getUnnormalizedRotation(viewRotation)
        val camSpatial = sciviewWin.camera?.spatial() ?: return
        viewRotation.y *= -1f
        viewRotation.z *= -1f
        camSpatial.rotation = viewRotation
        val dist = camSpatial.position.length()
        camSpatial.position = sciviewWin.camera?.forward!!.normalize().mul(-1f * dist)
    }

    private val auxTransform = AffineTransform3D()
    private val viewMatrix = Matrix4f()
    private val viewRotation = Quaternionf()

    // --------------------------------------------------------------------------
    fun setVisibilityOfVolume(state: Boolean) {
        volNodes?.forEach { v: Node? ->
            v?.visible = state
            if (state) {
                v?.children?.stream()
                    ?.filter { c: Node -> c.name.startsWith("Bounding") }
                    ?.forEach { c: Node -> c.visible = false }
            }
        }
    }

    fun setVisibilityOfSpots(state: Boolean) {
        sphereParent.visible = state
        if (state) {
            sphereParent
                .getChildrenByName(SphereLinkNodes.NAME_OF_NOT_USED_SPHERES)
                .forEach { s: Node -> s.visible = false }
        }
    }

    fun focusSpot(name: String) {
        val nodes = sphereParent.getChildrenByName(name)
        if (nodes.isNotEmpty()) {
            sciviewWin.setActiveCenteredNode(nodes[0])
        }
    }

    val detachedDPP_withOwnTime: DPP_DetachedOwnTime
    fun showTimepoint(timepoint: Int) {
        detachedDPP_withOwnTime.timepoint = timepoint
        updateSciviewContent(detachedDPP_withOwnTime)
    }

    private fun registerKeyboardHandlers() {
        //handlers

        //register them
        val handler = sciviewWin.sceneryInputHandler
        handler?.addKeyBinding(desc_DEC_SPH, key_DEC_SPH)
        handler?.addBehaviour(desc_DEC_SPH, ClickBehaviour { _, _ ->
            sphereLinkNodes.decreaseSphereScale()
            updateUI()
        })
        //
        handler?.addKeyBinding(desc_INC_SPH, key_INC_SPH)
        handler?.addBehaviour(desc_INC_SPH, ClickBehaviour { _, _ ->
            sphereLinkNodes.increaseSphereScale()
            updateUI()
        })
        //
        handler?.addKeyBinding(desc_COLORING, key_COLORING)
        handler?.addBehaviour(desc_COLORING, ClickBehaviour { _, _ ->
            updateSVColoring(force = true)
            updateUI()
        })
        //
        handler?.addKeyBinding(desc_CLRNG_AUTO, key_CLRNG_AUTO)
        handler?.addBehaviour(desc_CLRNG_AUTO, ClickBehaviour { _, _ ->
            updateVolAutomatically = !updateVolAutomatically
            logger.info("Volume updating auto mode: $updateVolAutomatically")
            updateUI()
        })
        //
        handler?.addKeyBinding(key_CLRNG_ONOFF, key_CLRNG_ONOFF)
        handler?.addBehaviour(key_CLRNG_ONOFF, ClickBehaviour { _, _ ->
            intensity.applyToColors = !intensity.applyToColors
            logger.info("Volume spots imprinting enabled: ${intensity.applyToColors}")
            updateUI()
        })
        //
        handler?.addKeyBinding(desc_CTRL_WIN, key_CTRL_WIN)
        handler?.addBehaviour(desc_CTRL_WIN, ClickBehaviour { _, _ -> createAndShowControllingUI() })
        //
        handler?.addKeyBinding(desc_CTRL_INFO, key_CTRL_INFO)
        handler?.addBehaviour(desc_CTRL_INFO, ClickBehaviour { _, _ -> logger.info(this.toString()) })
        //
        handler?.addKeyBinding(desc_PREV_TP, key_PREV_TP)
        handler?.addBehaviour(desc_PREV_TP, ClickBehaviour { _, _ ->
            detachedDPP_withOwnTime.prevTimepoint()
            updateSciviewContent(detachedDPP_withOwnTime)
        })
        //
        handler?.addKeyBinding(desc_NEXT_TP, key_NEXT_TP)
        handler?.addBehaviour(desc_NEXT_TP, ClickBehaviour { _, _ ->
            detachedDPP_withOwnTime.nextTimepoint()
            updateSciviewContent(detachedDPP_withOwnTime)
        })
    }

    private fun deregisterKeyboardHandlers() {
        val handler = sciviewWin.sceneryInputHandler
        if (handler != null) {
            listOf(desc_DEC_SPH,
                desc_INC_SPH,
                desc_COLORING,
                desc_CLRNG_AUTO,
                key_CLRNG_ONOFF,
                desc_CTRL_WIN,
                desc_CTRL_INFO,
                desc_PREV_TP,
                desc_NEXT_TP)
                .forEach {
                    handler.removeKeyBinding(it)
                    handler.removeBehaviour(it)
                }
        }
    }

    @JvmOverloads
    fun createAndShowControllingUI(windowTitle: String? = "Controls for " + sciviewWin.getName()): JFrame {
        return JFrame(windowTitle).apply {
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
            associatedUI = SciviewBridgeUI(this@SciviewBridge, contentPane)
            pack()
            isVisible = true
        }
    }

    fun detachControllingUI() {
        if (associatedUI != null) {
            associatedUI?.deactivateAndForget()
            associatedUI = null
        }
        if (uiFrame != null) {
            uiFrame?.isVisible = false
            uiFrame?.dispose()
        }
    }

    fun updateUI() {
        if (associatedUI == null) return
        associatedUI?.updatePaneValues()
    }

    companion object {
        fun getDisplayVoxelRatio(forThisSource: Source<*>): FloatArray {
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
