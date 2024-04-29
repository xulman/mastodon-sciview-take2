package org.mastodon.mamut

import bdv.viewer.Source
import graphics.scenery.*
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.RandomAccessibleInterval
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
        sb.append("   INTENSITY_RANGE_MAX = ${intensity.rangeMax}\n")
        sb.append("   INTENSITY_RANGE_MIN = ${intensity.rangeMin}\n")
        return sb.toString()
    }

    //data sink stuff
    val sciviewWin: SciView
    val sphereLinkNodes: SphereLinkNodes
    //sink scene graph structuring nodes
    val axesParent: Node?
    val sphereParent: Group
    var volChannelNode: Volume
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
        // absolute number of pixels for each dimension of the volume
        val volumeNumPixels = spimSource.getSource(0, this.sourceResLevel).dimensionsAsLongArray()
        val volumeDownscale = floatArrayOf(
            volumeDims[0].toFloat() / volumeNumPixels[0].toFloat(),
            volumeDims[1].toFloat() / volumeNumPixels[1].toFloat(),
            volumeDims[2].toFloat() / volumeNumPixels[2].toFloat()
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

        val commonNodeName = ": " + mastodon.projectName
        volChannelNode = sciviewWin.addVolume(spimSource.getSource(0, this.sourceResLevel) as RandomAccessibleInterval<UnsignedShortType>, "Volume$commonNodeName", floatArrayOf(1f, 1f, 1f))
        addVolumeToScene(
            volChannelNode,
            "Grays.lut",
            volumeScale,
            intensity.rangeMin,
            intensity.rangeMax
        )

        //spots stuff:
        sphereParent = Group()
        sphereParent.name = "SPOTS$commonNodeName"
        sciviewWin.addNode(sphereParent)
        val MAGIC_ONE_TENTH = 0.1f //probably something inside scenery...
        spotsScale.mul(MAGIC_ONE_TENTH * volChannelNode.pixelToWorldRatio)
        mastodonToImgCoordsTransfer = Vector3f(
            voxelRes[0] * volumeDownscale[0],
            voxelRes[1] * volumeDownscale[1],
            voxelRes[2] * volumeDownscale[2]
        )
        sphereParent.spatial().scale = spotsScale
        sphereParent.spatial().position = Vector3f(
            volumeNumPixels[0].toFloat(),
            volumeNumPixels[1].toFloat(),
            volumeNumPixels[2].toFloat()
        )
            .mul(-0.5f, 0.5f, 0.5f) //NB: y,z axes are flipped, see SphereNodes::setSphereNode()
            .mul(mastodonToImgCoordsTransfer) //raw img coords to Mastodon internal coords
            .mul(spotsScale) //apply the same scaling as if "going through the SphereNodes"

        //add the sciview-side displaying handler for the spots
        sphereLinkNodes = SphereLinkNodes(sciviewWin, sphereParent)
//        sphereLinkNodes.showTheseSpots(mastodon, 0, noTSColorizer)
        sphereLinkNodes.initializeSpots(mastodon, 0, noTSColorizer)
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
            sciviewWin.deleteNode(volChannelNode, true)
            logger.debug("Mastodon-sciview Bridge closing procedure: red volume removed")
            Thread.sleep(updateGraceTime)
            sciviewWin.deleteNode(sphereParent, true)
            logger.debug("Mastodon-sciview Bridge closing procedure: spots were removed")
        } catch (e: InterruptedException) { /* do nothing */
        }
        sciviewWin.deleteNode(axesParent, true)
    }

    /** Adds a volume to the sciview scene, adjusts the transfer function to a ramp from [0, 0] to [1, 1]
     * and sets the node children visibility to false. */
    private fun addVolumeToScene(
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
            val tf = TransferFunction()
            tf.addControlPoint(0f, 0f)
            tf.addControlPoint(1f, 1f)
            it.transferFunction = tf
            //make Bounding Box Grid invisible
            it.children.forEach { n: Node -> n.visible = false }
        }

        //FAILED to hook the volume nodes under the this.volumeParent node... so commented out for now
        //(one could construct Volume w/o sciview.addVolume(), but I find that way too difficult)
        //sciviewWin.deleteNode(v, true);
        //this.volumeParent.addChild(v);
    }

    private var intensityBackup = intensity.copy()

    /** Makes an educated guess about the value range of the volume and adjusts the min/max range values accordingly. */
    fun autoAdjustIntensity() {
        // toggle boolean state
        isVolumeAutoAdjust = !isVolumeAutoAdjust

        if (isVolumeAutoAdjust) {
            var maxVal = 0.0f
            val srcImg = spimSource.getSource(0, sourceResLevel) as RandomAccessibleInterval<UnsignedShortType>
            Views.iterable(srcImg).forEach { px -> maxVal = maxVal.coerceAtLeast(px.realFloat) }
            intensity.clampTop = 0.9f * maxVal //very fake 90% percentile...
            intensity.rangeMin = maxVal * 0.15f
            intensity.rangeMax = maxVal * 0.75f
            //TODO: change MIN and MAX to proper values
            logger.debug("Clamp at ${intensity.clampTop}," +
                    " range min to ${intensity.rangeMin} and range max to ${intensity.rangeMax}")
            updateVolume(force = true)
            updateUI()
        } else {
            intensity = intensityBackup.copy()
            updateVolume(force = true)
            updateUI()
        }
    }

    /** Change voxel values based on the intensity values like contrast, shift, gamma, etc. */
    fun <T : IntegerType<T>?> volumeIntensityProcessing(
        srcImg: RandomAccessibleInterval<T>?
    ) {
        val gammaEnabledIntensityProcessor: (T) -> Unit =
            { src: T -> src?.setReal(
                    intensity.clampTop * ( //TODO, replace pow() with LUT for several gammas
                            min(
                                intensity.contrast * src.realFloat + intensity.shift,
                                intensity.clampTop
                            ) / intensity.clampTop
                        ).pow(intensity.gamma)
                    )
            }
        val noGammaIntensityProcessor: (T) -> Unit =
            { src: T -> src?.setReal(
                        min(
                            // TODO This needs to incorporate INTENSITY_RANGE_MIN and MAX
                            intensity.contrast * src.realFloat + intensity.shift,
                            intensity.clampTop
                        )
                    )
            }
        //choose one processor for the downstream job;
        //it is seemingly a long code but it does the if-decision only once now
        val intensityProcessor = if (intensity.gamma != 1.0f)
            gammaEnabledIntensityProcessor else noGammaIntensityProcessor

        if (srcImg == null) logger.warn("volumeIntensityProcessing: srcImg is null !!!")

        //massage input data into the red channel (LB guarantees that counterparting pixels are accessed)
        LoopBuilder.setImages(srcImg)
            .multiThreaded()
            .forEachPixel(intensityProcessor)

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
    /** Create a BDV window and launch a [BdvNotifier] instance to synchronize time point and viewing direction. */
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
    /** Calls [updateVolume] and [SphereNodes.showTheseSpots] to update the current volume and corresponding spots. */
    fun updateSciviewContent(forThisBdv: DisplayParamsProvider) {
        updateVolume(forThisBdv)
//        sphereLinkNodes.showTheseSpots(
//            mastodon,
//            forThisBdv.timepoint, forThisBdv.colorizer
//        )
//        sphereLinkNodes.initializeSpots(mastodon, forThisBdv.timepoint, forThisBdv.colorizer)
    }

    private var lastTpWhenVolumeWasUpdated = 0
    val detachedDPP_showsLastTimepoint: DisplayParamsProvider = DPP_Detached()

    /** Fetch the volume state at the current time point,
     * then call [volumeIntensityProcessing] to adjust the intensity values*/
    @JvmOverloads
    fun updateVolume(
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
                volumeIntensityProcessing(srcRAI as RandomAccessibleInterval<UnsignedShortType>
                )
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
        volChannelNode.visible = state
        if (state) {
            volChannelNode.children.stream()
                .filter { c: Node -> c.name.startsWith("Bounding") }
                .forEach { c: Node -> c.visible = false }
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

        val handler = sciviewWin.sceneryInputHandler
        handler?.addKeyBinding(desc_DEC_SPH, key_DEC_SPH)
        handler?.addBehaviour(desc_DEC_SPH, ClickBehaviour { _, _ ->
            sphereLinkNodes.decreaseSphereScale()
            updateUI()
        })

        handler?.addKeyBinding(desc_INC_SPH, key_INC_SPH)
        handler?.addBehaviour(desc_INC_SPH, ClickBehaviour { _, _ ->
            sphereLinkNodes.increaseSphereScale()
            updateUI()
        })

        handler?.addKeyBinding(desc_CTRL_WIN, key_CTRL_WIN)
        handler?.addBehaviour(desc_CTRL_WIN, ClickBehaviour { _, _ -> createAndShowControllingUI() })

        handler?.addKeyBinding(desc_CTRL_INFO, key_CTRL_INFO)
        handler?.addBehaviour(desc_CTRL_INFO, ClickBehaviour { _, _ -> logger.info(this.toString()) })

        handler?.addKeyBinding(desc_PREV_TP, key_PREV_TP)
        handler?.addBehaviour(desc_PREV_TP, ClickBehaviour { _, _ ->
            detachedDPP_withOwnTime.prevTimepoint()
            updateSciviewContent(detachedDPP_withOwnTime)
        })

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
            val AXES_LINE_WIDTHS = 0.04f
            val AXES_LINE_LENGTHS = 0.7f
            //
            val axesParent = Group()
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
        const val key_CTRL_WIN = "ctrl I"
        const val key_CTRL_INFO = "shift I"
        const val key_PREV_TP = "T"
        const val key_NEXT_TP = "shift T"
        const val desc_DEC_SPH = "decrease_initial_spheres_size"
        const val desc_INC_SPH = "increase_initial_spheres_size"
        const val desc_CTRL_WIN = "controlling_window"
        const val desc_CTRL_INFO = "controlling_info"
        const val desc_PREV_TP = "show_previous_timepoint"
        const val desc_NEXT_TP = "show_next_timepoint"
    }
}
