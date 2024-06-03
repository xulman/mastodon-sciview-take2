package util

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.extensions.*
import graphics.scenery.utils.lazyLogger
import net.imglib2.display.ColorTable
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.EigenDecomposition
import org.apache.commons.math3.linear.RealMatrix
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.mastodon.mamut.ProjectModel
import org.mastodon.mamut.model.Link
import org.mastodon.mamut.model.Spot
import org.mastodon.spatial.SpatialIndex
import org.mastodon.ui.coloring.GraphColorGenerator
import org.scijava.event.EventService
import sc.iview.SciView
import java.awt.Color
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.math.sqrt
import kotlin.time.TimeSource

class SphereLinkNodes(
    val sv: SciView,
    val mastodonData: ProjectModel,
    val sphereParentNode: Node,
    val linkParentNode: Node
) {

    private val logger by lazyLogger()
    var sphereScaleFactor = 1f
    var linkScaleFactor = 1f
    var DEFAULT_COLOR = 0x00FFFFFF
    var numTimePoints: Int
    lateinit var lut: ColorTable
    var currentColorMode: ColorMode
    val spotPool: MutableList<InstancedNode.Instance> = ArrayList()
    private var spotRef: Spot? = null
    var events: EventService? = null

    val sphere = Icosphere(1f, 2)
    val cylinder = Cylinder(0.2f, 1f, 6, true, true)
    var mainSpotInstance: InstancedNode? = null
    var mainLinkInstance: InstancedNode? = null
    lateinit var spots: SpatialIndex<Spot>
    var linkForwardRange: Int
    var linkBackwardRange: Int

    init {
        events = sv.scijavaContext?.getService(EventService::class.java)
        numTimePoints = mastodonData.maxTimepoint

        setLUT("Fire.lut")
        currentColorMode = ColorMode.LUT

        linkForwardRange = mastodonData.maxTimepoint
        linkBackwardRange = mastodonData.maxTimepoint
    }

    fun setLUT(lutName: String) {
        try {
            lut = sv.getLUT(lutName)
        } catch (e: Exception) {
            logger.error("Could not find LUT $lutName.")
        }
    }

    /** The following types are allowed for track coloring:
     * - [LUT] uses a colormap, defaults to Fire.lut
     * - [SPOT] uses the spot color from the connected spot */
    enum class ColorMode { LUT, SPOT }

    /** Shows or initializes the main spot instance, publishes it to the scene and populates it with instances from the current time-point. */
    fun showInstancedSpots(
        timepoint: Int,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        // only create and add the main instance once during initialization
        if (mainSpotInstance == null) {
            sphere.setMaterial(ShaderMaterial.fromFiles("DeferredInstancedColor.vert", "DeferredInstancedColor.frag")) {
                diffuse = Vector3f(1.0f, 1.0f, 1.0f)
                ambient = Vector3f(1.0f, 1.0f, 1.0f)
                specular = Vector3f(.0f, 1.0f, 1.0f)
                metallic = 0.0f
                roughness = 1.0f
            }

            val mainSpot = InstancedNode(sphere)
            // Instanced properties should be aligned to 4*32bit boundaries, hence the use of Vector4f instead of Vector3f here
            mainSpot.instancedProperties["Color"] = { Vector4f(1f) }
            sv.addNode(mainSpot, parent = sphereParentNode)
            mainSpotInstance = mainSpot
        }

        // ensure that mainSpotInstance is not null and properly initialized
        val mainSpot = mainSpotInstance ?: throw IllegalStateException("InstancedSpot is null, instance was not initialized.")

        if (spotRef == null) spotRef = mastodonData.model.graph.vertexRef()
        val focusedSpotRef = mastodonData.focusModel.getFocusedVertex(spotRef)
        mastodonData.selectionModel
        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(timepoint)
        sv.blockOnNewNodes = false

        val spotPosition = FloatArray(3)
        val covArray = Array(3) { DoubleArray(3) }
        var covariance: Array2DRowRealMatrix
        var inst: InstancedNode.Instance
        var axisLengths: Vector3f

        var index = 0
        for (spot in spots) {
            // reuse a spot instance from the pool if the pool is large enough
            if (index < spotPool.size) {
                inst = spotPool[index]
                inst.visible = true
            }
            // otherwise create a new instance and add it to the pool
            else {
                inst = mainSpot.addInstance()
                inst.name = "${timepoint}_${spot.internalPoolIndex}"
                inst.addAttribute(Material::class.java, sphere.material())
                inst.parent = sphereParentNode
                spotPool.add(inst)
            }

            // get spot covariance and calculate the scaling and rotation from it
            spot.localize(spotPosition)
            spot.getCovariance(covArray)
            covariance = Array2DRowRealMatrix(covArray)
            val (eigenvalues, eigenvectors) = computeEigen(covariance)
            axisLengths = computeSemiAxes(eigenvalues)
            inst.spatial {
                position = Vector3f(spotPosition)
                scale = axisLengths * sphereScaleFactor
//                rotation = matrixToQuaternion(eigenvectors)
            }
            inst.setColorFromSpot(spot, colorizer)
            // highlight the spot currently selected in BDV
            if (focusedSpotRef != null && focusedSpotRef.internalPoolIndex == spot.internalPoolIndex) {
                inst.instancedProperties["Color"] = { Vector4f(1f, 0.25f, 0.25f, 1f) }
            }

            index++
        }

        // turn all leftover spots from the pool invisible
        var i = index
        while (i < spotPool.size) {
            spotPool[i++].visible = false
        }
    }

    private fun computeEigen(covariance: Array2DRowRealMatrix): Pair<DoubleArray, RealMatrix> {
        val eigenDecomposition = EigenDecomposition(covariance)
        val eigenvalues = eigenDecomposition.realEigenvalues
        val eigenvectors = eigenDecomposition.v
        val tempRow = eigenvectors.getRow(0)
//        eigenvectors.setRow(0, eigenvectors.getRow(2))
//        eigenvectors.setRow(2, tempRow)
        return Pair(eigenvalues, eigenvectors)
    }

    private fun computeSemiAxes(eigenvalues: DoubleArray): Vector3f {
        return Vector3f(
            // flip X and Z axes to align with the sciview coordinate system (is this correct??)
            sqrt(eigenvalues[2]).toFloat(),
            sqrt(eigenvalues[1]).toFloat(),
            sqrt(eigenvalues[0]).toFloat()
        )
    }

    private fun matrixToQuaternion(eigenvectors: RealMatrix): Quaternionf {
        val matrix3f = Matrix3f()
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                matrix3f.set(j, i, eigenvectors.getEntry(j, i).toFloat())
            }
        }
        return Quaternionf().setFromUnnormalized(matrix3f)
    }

    // stretch color channels
    private fun Vector3f.stretchColor(): Vector3f {
        this.x.coerceIn(0f, 1f)
        this.y.coerceIn(0f, 1f)
        this.z.coerceIn(0f, 1f)
        val max = this.max()
        return this + Vector3f(1 - max)
    }

    /** extension function that takes a spot and colors the corresponding instance according to the [colorizer]. */
    private fun InstancedNode.Instance.setColorFromSpot(
        s: Spot,
        colorizer: GraphColorGenerator<Spot, Link>,
        randomColors: Boolean = false,
        isPartyMode: Boolean = false
    ) {
        var intColor = colorizer.color(s)
        if (intColor == 0x00000000) {
            intColor = DEFAULT_COLOR
        }
        if (!isPartyMode) {
            if (!randomColors) {
                val col = unpackRGB(intColor)
                this.instancedProperties["Color"] = { col }
            } else {
                val col = Random.random3DVectorFromRange(0f, 1f).stretchColor()
                this.instancedProperties["Color"] = { col.xyzw() }
            }
        } else {
            this.instancedProperties["Color"] = { Random.random3DVectorFromRange(0f, 1f).xyzw() }
        }
    }

    /** Takes an integer-encoded RGB value and returns it as [Vector4f] where alpha is 1.0f. */
    private fun unpackRGB(intColor: Int): Vector4f {
        val r = (intColor shr 16 and 0x000000FF) / 255f
        val g = (intColor shr 8 and 0x000000FF) / 255f
        val b = (intColor and 0x000000FF) / 255f
        return Vector4f(r, g, b, 1.0f)
    }

    fun decreaseSphereScale() {
        val oldScale = sphereScaleFactor
        sphereScaleFactor -= 0.1f
        if (sphereScaleFactor < 0.1f) sphereScaleFactor = 0.1f
        val factor = sphereScaleFactor / oldScale
        mainSpotInstance?.instances?.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Decreasing scale to $sphereScaleFactor, by factor $factor")
    }

    fun increaseSphereScale() {
        val oldScale = sphereScaleFactor
        sphereScaleFactor += 0.1f
        val factor = sphereScaleFactor / oldScale
        mainSpotInstance?.instances?.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Increasing scale to $sphereScaleFactor, by factor $factor")
    }

    fun increaseLinkScale() {
        val oldScale = linkScaleFactor
        linkScaleFactor += 0.2f
        val factor = linkScaleFactor / oldScale
        mainLinkInstance?.instances?.forEach { l -> l.spatial().scale *= Vector3f(factor, 1f, factor) }
        logger.debug("Increasing scale to $linkScaleFactor, by factor $factor")
    }

    fun decreaseLinkScale() {
        val oldScale = linkScaleFactor
        linkScaleFactor -= 0.2f
        val factor = linkScaleFactor / oldScale
        mainLinkInstance?.instances?.forEach { l -> l.spatial().scale *= Vector3f(factor, 1f, factor) }
        logger.debug("Decreasing scale to $linkScaleFactor, by factor $factor")
    }

    fun initializeInstancedLinks(
        colorMode: ColorMode,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        cylinder.setMaterial(ShaderMaterial.fromFiles("DeferredInstancedColor.vert", "DeferredInstancedColor.frag")) {
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
            ambient = Vector3f(1.0f, 1.0f, 1.0f)
            specular = Vector3f(.0f, 1.0f, 1.0f)
            metallic = 0.0f
            roughness = 1.0f
        }
        currentColorMode = colorMode
        val mainLink = InstancedNode(cylinder)
        mainLink.instancedProperties["Color"] = { Vector4f(1f) }
        sv.addNode(mainLink, parent = linkParentNode)

        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(0)
        numTimePoints = mastodonData.maxTimepoint
        mainLinkInstance = mainLink
//        spots.forEach { spot ->
//            searchAndConnectSpots(spot, numTimePoints, colorizer, true)
//        }

        val start = TimeSource.Monotonic.markNow()
        // TODO use coroutines for this
        mastodonData.model.graph.edges().forEach { edge ->
            addLink(edge.source, edge.target, mainLink, colorizer)
        }
        val end = TimeSource.Monotonic.markNow()
        logger.info("Edge traversel took ${end - start}.")
        logger.info("Found a total of ${links.size} links. Should be ${mastodonData.model.graph.edges().size}.")
        // first update the link colors without providing a colorizer, because no BDV window has been opened yet
        updateLinkColors(null)
    }

    /** Traverse and update the colors of all [links] using the provided [cm].
     * When set to [cm.SPOT], it uses the [colorizer] to get the spot colors. */
    fun updateLinkColors (
        colorizer: GraphColorGenerator<Spot, Link>?,
        cm: ColorMode =  currentColorMode
    ) {
        val start = TimeSource.Monotonic.markNow()
        when (cm) {
            ColorMode.LUT -> {
                links.forEach {link ->
                    val factor = link.value.tp / numTimePoints.toDouble()
                    val color = unpackRGB(lut.lookupARGB(0.0, 1.0, factor))
                    link.value.instance.instancedProperties["Color"] = { color }
                }
            }
            ColorMode.SPOT -> {
                if (colorizer != null) {
                    for (tp in 0 until numTimePoints) {
                        val spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(tp)
                        spots.forEach { spot ->
                            links[spot.hashCode()]?.instance?.setColorFromSpot(spot, colorizer)
                        }
                    }
                }
            }
        }
        val end = TimeSource.Monotonic.markNow()
        logger.info("Link coloring took ${end - start}.")
    }

    fun updateLinkVisibility(currentTP: Int) {
        links.forEach {link ->
            // turns the link on if it is within range, otherwise turns it off
            link.value.instance.visible = link.value.tp in currentTP - linkBackwardRange..currentTP + linkForwardRange
        }
    }

    /** This function generates a unique hash for every spot, using its time-point and internal pool index. */
    fun Spot.generateHash() : Int {
        val hash = this.timepoint * 239 + this.internalPoolIndex * 337
        return (hash % Int.MAX_VALUE)

    }

    val linkSize = 2.0

    var linksNodesHub: Node? = null // gathering node in sciview -- a links node associated to its spots node
    // list of all link segments
    var links: ConcurrentHashMap<Int, LinkNode> = ConcurrentHashMap()

    var selectionStorage: Node = RichNode()
    var refSpot: Spot? = null
    var minTP = 0
    var maxTP = 0

    fun registerNewSpot(spot: Spot) {
        if (refSpot != null) refSpot!!.modelGraph.releaseRef(refSpot)
        refSpot = spot.modelGraph.vertexRef()
        refSpot?.refTo(spot)
        minTP = spot.timepoint
        maxTP = minTP
    }

    fun hsvToArgb(hue: Int, saturation: Int, value: Int): Vector4f {
        val h = hue / 360.0f
        val s = saturation / 100.0f
        val v = value / 100.0f

        val rgbInt = Color.HSBtoRGB(h, s, v)
        return unpackRGB(rgbInt)
    }

    fun updateLinks(TPsInPast: Int, TPsAhead: Int) {
//        logger.info("updatelinks!")
//        refSpot?.let {
//            clearLinksOutsideRange(it.timepoint, it.timepoint)
//            backwardSearch(it, it.timepoint - TPsInPast)
//            forwardSearch(it, it.timepoint + TPsAhead)
//        }
//        events?.publish(NodeChangedEvent(linksNodesHub))
    }

    fun addLink(from: Spot, to: Spot, mainInstance: InstancedNode, colorizer: GraphColorGenerator<Spot, Link>) {

        // temporary container to get the position as array
        val pos = FloatArray(3)

        from.localize(pos)
        val posOrigin = Vector3f(pos)
        to.localize(pos)
        val posTarget = Vector3f(pos)

        //NB: posOrigin is base of the "vector" link, posTarget is the "vector" link itself
        posTarget.sub(posOrigin)
        val inst = mainInstance.addInstance()
        inst.addAttribute(Material::class.java, cylinder.material())
        val factor = to.timepoint / numTimePoints.toDouble()
        val color = unpackRGB(lut.lookupARGB(0.0, 1.0, factor))

        inst.instancedProperties["Color"] = { color }
        inst.spatial {
            scale.set(linkSize, posTarget.length().toDouble(), linkSize)
            rotation = Quaternionf().rotateTo(Vector3f(0f, 1f, 0f), posTarget).normalize()
            position = Vector3f(posOrigin)
        }
        inst.name = from.label + " --> " + to.label
        inst.parent = linkParentNode
        // add a new key-value pair to the hash map
        links[from.hashCode()] = LinkNode(inst, from, to, from.timepoint)

//        minTP = minTP.coerceAtMost(from.timepoint)
//        maxTP = maxTP.coerceAtLeast(to.timepoint)

//            logger.info("added link from ${from.timepoint}/${from.internalPoolIndex} to ${to.timepoint}/${to.internalPoolIndex}")
    }

    /** Recursive method that traverses the links of the provided [origin] up until the given timepoint [toTP].
     * Forward search is enabled when [forward] is true, otherwise it searches backwards. */
    private fun searchAndConnectSpots(
        spot: Spot,
        toTP: Int,
        colorizer: GraphColorGenerator<Spot, Link>,
        forward: Boolean
    ) {
        // ensure that the local state of mainInstance is not nullable
        val mainInstance = mainLinkInstance?: throw IllegalStateException("Main link instance was not initialized")

        if (forward) {
            // forward search
            if (spot.timepoint >= toTP) return

//            val originRef = spot.modelGraph.vertexRef()
            val targetRef = spot.modelGraph.vertexRef() // so we can have two different references
            if (spot.outgoingEdges().size() > 1) {
//                logger.info("got ${spot.outgoingEdges().size()} outgoing edges for TP ${spot.timepoint} and spot ${spot.internalPoolIndex}")
            }
            // TODO why even use incoming edges in forward search?
//            for (l in spot.incomingEdges()) {
//                l.getSource(targetRef)
//                if (targetRef.timepoint < spot.timepoint && targetRef.timepoint <= toTP) {
//                    addLink(spot, originRef)
//                    searchAndConnectSpots(originRef, toTP, colorizer, true)
//                }
//            }
            for (l in spot.outgoingEdges()) {
                if (l.getTarget(targetRef).timepoint > spot.timepoint && targetRef.timepoint <= toTP) {
                    addLink(spot, targetRef, mainInstance, colorizer)
                    searchAndConnectSpots(targetRef, toTP, colorizer, true)
                }
            }
//            spot.modelGraph.releaseRef(spot)
            spot.modelGraph.releaseRef(targetRef)
        }
        //        else {
//            // TODO do we even need backwards search?
//            // backwards search
//            if (spot.timepoint <= toTP) return
//            val spotRef = spot.modelGraph.vertexRef()
//            for (l in spot.incomingEdges()) {
//                if (l.getSource(spotRef).timepoint < spot.timepoint && spotRef.timepoint >= toTP) {
//                    addLink(spotRef, spot)
//                    searchAndConnectSpots(spotRef, toTP, colorizer, false)
//                }
//            }
//            for (l in spot.outgoingEdges()) {
//                if (l.getTarget(spotRef).timepoint < spot.timepoint && spotRef.timepoint >= toTP) {
//                    addLink(spotRef, spot)
//                    searchAndConnectSpots(spotRef, toTP, colorizer, false)
//                }
//            }
//        }
    }

    fun clearLinksOutsideRange(fromTP: Int, toTP: Int) {
        links.iterator().let {
            while (it.hasNext() == true) {
                val link = it.next()
                if (link.value.from.timepoint < fromTP || link.value.to.timepoint > toTP) {
                    linksNodesHub?.removeChild(link.value.instance)
                    it.remove()
                }
            }
        }
        minTP = fromTP
        maxTP = toTP
    }

    fun clearAllLinks() {
        linksNodesHub!!.children.removeIf { f: Node? -> true }
        links.clear()
        minTP = 999999
        maxTP = -1
    }

    fun setupEmptyLinks() {
        linksNodesHub = RichNode()
        links = ConcurrentHashMap()
        minTP = 999999
        maxTP = -1
    }

    companion object {
        const val NAME_OF_NOT_USED_SPHERES = "not used now"
    }
}

data class LinkNode (val instance: InstancedNode.Instance, val from: Spot, val to: Spot, val tp: Int)