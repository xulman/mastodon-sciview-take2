package util

import graphics.scenery.*
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Arrow
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
import org.joml.Vector3i
import org.joml.Vector4f
import org.mastodon.mamut.ProjectModel
import org.mastodon.mamut.SciviewBridge
import org.mastodon.mamut.model.Link
import org.mastodon.mamut.model.Spot
import org.mastodon.spatial.SpatialIndex
import org.mastodon.ui.coloring.GraphColorGenerator
import org.scijava.event.EventService
import sc.iview.SciView
import sc.iview.commands.demo.advanced.HedgehogAnalysis.SpineGraphVertex
import java.awt.Color
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.math.sqrt
import kotlin.time.TimeSource

class SphereLinkNodes(
    val sv: SciView,
    val bridge: SciviewBridge,
    val mastodonData: ProjectModel,
    val sphereParentNode: Node,
    val linkParentNode: Node
) {

    private val logger by lazyLogger()
    var sphereScaleFactor = 5f
    var linkScaleFactor = 1f
    var DEFAULT_COLOR = 0x00FFFFFF
    var numTimePoints: Int
    lateinit var lut: ColorTable
    var currentColorMode: ColorMode
    val spotPool: MutableList<InstancedNode.Instance> = ArrayList(10000)
    val linkPool: MutableList<InstancedNode.Instance> = ArrayList(10000)
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
        val tStart = TimeSource.Monotonic.markNow()
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
            mainSpot.name = "SpotInstance"
            // Instanced properties should be aligned to 4*32bit boundaries, hence the use of Vector4f instead of Vector3f here
            mainSpot.instancedProperties["Color"] = { Vector4f(1f) }
            var inst: InstancedNode.Instance
            // initialize the whole pool with instances once
            for (i in 0..<10000) {
                inst = mainSpot.addInstance()
                inst.parent = sphereParentNode
                spotPool.add(inst)
            }

            sv.addNode(mainSpot, parent = sphereParentNode)
            mainSpotInstance = mainSpot
        }

        // ensure that mainSpotInstance is not null and properly initialized
        val mainSpot = mainSpotInstance ?: throw IllegalStateException("InstancedSpot is null, instance was not initialized.")

        if (spotRef == null) spotRef = mastodonData.model.graph.vertexRef()
        val focusedSpotRef = mastodonData.focusModel.getFocusedVertex(spotRef)
        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(timepoint)
        sv.blockOnNewNodes = false

        val spotPosition = FloatArray(3)
        val covArray = Array(3) { DoubleArray(3) }
        var covariance: Array2DRowRealMatrix
        var inst: InstancedNode.Instance
        var axisLengths: Vector3f

        var index = 0
        logger.debug("we have ${spots.size()} spots in this Mastodon time point.")
        for (spot in spots) {
            // reuse a spot instance from the pool if the pool is large enough
            if (index < spotPool.size) {
                inst = spotPool[index]
                inst.visible = true
            }
            // otherwise create a new instance and add it to the pool
            else {
                inst = mainSpot.addInstance()
                inst.parent = sphereParentNode
                spotPool.add(inst)
            }
            inst.name = "spot_${spot.internalPoolIndex}"
            // get spot covariance and calculate the scaling and rotation from it
            spot.localize(spotPosition)
            spot.getCovariance(covArray)
            covariance = Array2DRowRealMatrix(covArray)
            val (eigenvalues, eigenvectors) = computeEigen(covariance)
            axisLengths = computeSemiAxes(eigenvalues)

            inst.spatial {
                position = Vector3f(spotPosition)
                scale = Vector3f(sphereScaleFactor *  sqrt(spot.boundingSphereRadiusSquared.toFloat()) / 10f)
                // TODO add ellipsoid scale & rotation to instances
                // scale = axisLengths * sphereScaleFactor * 0.5f
                // rotation = eigenvectors.toQuaternion()
            }

//            inst.drawEigenVectors(eigenvectors, axisLengths)

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
        val tElapsed = TimeSource.Monotonic.markNow() - tStart
        logger.debug("Spot updates took $tElapsed")
    }

    private fun computeEigen(covariance: Array2DRowRealMatrix): Pair<DoubleArray, RealMatrix> {
        val eigenDecomposition = EigenDecomposition(covariance)
        val eigenvalues = eigenDecomposition.realEigenvalues
        val eigenvectors = eigenDecomposition.v
        return Pair(eigenvalues, eigenvectors)
    }

    // helper variable to make it easy to try out different vector orders
    // for converting covariance matrices to rotation quaternions
    val matrixOrder = Vector3i(0, 1, 2)

    private fun computeSemiAxes(eigenvalues: DoubleArray): Vector3f {
        return Vector3f(
            sqrt(eigenvalues[matrixOrder[0]]).toFloat(),
            sqrt(eigenvalues[matrixOrder[1]]).toFloat(),
            sqrt(eigenvalues[matrixOrder[2]]).toFloat()
        )
    }

    /** Debug function to help with aligning ellipsoids with the eigenvectors from the covariance matrix.
     * @param [eigenVectors] The column-based eigenvectors of the covariance matrix
     * @param [axisLengths] The lengths per axis, given as [Vector3f]
     * */
    fun InstancedNode.Instance.drawEigenVectors(eigenVectors: RealMatrix, axisLengths: Vector3f) {

        val x = Vector3f(eigenVectors.getColumn(0).map { it.toFloat() }.toFloatArray()).normalize()
        val y = Vector3f(eigenVectors.getColumn(1).map { it.toFloat() }.toFloatArray()).normalize()
        val z = Vector3f(eigenVectors.getColumn(2).map { it.toFloat() }.toFloatArray()).normalize()

        val red = DefaultMaterial()
        red.diffuse = Vector3f(1f,0.2f, 0.2f)
        red.cullingMode = Material.CullingMode.None
        val green = DefaultMaterial()
        green.diffuse = Vector3f(0.2f,1f,0.2f)
        green.cullingMode = Material.CullingMode.None
        val blue = DefaultMaterial()
        blue.diffuse = Vector3f(0.2f,0.2f,1f)
        blue.cullingMode = Material.CullingMode.None

        val arrowX = Arrow(x.times(axisLengths.x))
        arrowX.addAttribute(Material::class.java, red)
        val arrowY = Arrow(y.times(axisLengths.y))
        arrowY.addAttribute(Material::class.java, green)
        val arrowZ = Arrow(z.times(axisLengths.z))
        arrowZ.addAttribute(Material::class.java, blue)

        for (a in arrayOf(arrowX, arrowY, arrowZ)) {
            a.spatial().position = this.spatial().position
            sv.addNode(a, false, parent = sphereParentNode)
        }
    }

    /** Converts this [RealMatrix] into a rotation [Quaternionf]. */
    private fun RealMatrix.matrixToQuaternion(verbose: Boolean = false): Quaternionf {

        val matrix3f = Matrix3f()

        val x = Vector3f(getColumn(0).map { it.toFloat() }.toFloatArray())
        val y = Vector3f(getColumn(1).map { it.toFloat() }.toFloatArray())
        val z = Vector3f(getColumn(2).map { it.toFloat() }.toFloatArray())

        matrix3f.setRow(matrixOrder.x, x)
        matrix3f.setRow(matrixOrder.y, y)
        matrix3f.setRow(matrixOrder.z, z)

        // matrix3f.transpose()

        val quaternion = Quaternionf()
        matrix3f.getNormalizedRotation(quaternion)
        quaternion.invert()
        if (verbose) {
            logger.info("converted matrix is \n $matrix3f")
            logger.info("quaternion is $quaternion")
        }
        return quaternion
    }

    /** Converts this [RealMatrix] into a rotation [Quaternionf].
     * Uses a different approach than [RealMatrix.matrixToQuaternion] for testing purposes. */
    private fun RealMatrix.alignToQuaternion(): Quaternionf {

        // extract
        val v1 = Vector3f(getColumn(0).map { it.toFloat() }.toFloatArray()).normalize()
        val v2 = Vector3f(getColumn(1).map { it.toFloat() }.toFloatArray()).normalize()
        val v3 = Vector3f(getColumn(2).map { it.toFloat() }.toFloatArray()).normalize()

        val quaternion = Quaternionf()
        // align longest axis
        quaternion.rotateTo(Vector3f(1f, 0f, 0f), v1)
        // align second longest axis
        val tempY = Vector3f(0f, 1f, 0f).rotate(quaternion)
        val correction = Quaternionf().rotateTo(tempY, v2)
        quaternion.mul(correction)
        // TODO does this need to be flipped for right- vs left-handed coordinate system? (Mastodon vs Sciview)
        quaternion.invert()
        return quaternion
    }

    // stretch color channels
    private fun Vector3f.stretchColor(): Vector3f {
        this.x.coerceIn(0f, 1f)
        this.y.coerceIn(0f, 1f)
        this.z.coerceIn(0f, 1f)
        val max = this.max()
        return this + Vector3f(1 - max)
    }

    private fun Vector3f.toDoubleArray(): DoubleArray {
        return this.toFloatArray().map { it.toDouble() }.toDoubleArray()
    }

    /** Extension function that takes a spot and colors the corresponding instance according to the [colorizer]. */
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

    /** Tries to find a spot in the current time point for the given [instance].
     * It does that by filtering through the names of the spots.
     * @return either a [Spot] or null. */
    fun findSpotFromInstance(instance: InstancedNode.Instance): Spot? {
        if (instance.name.startsWith("spot")) {
            val name = instance.name.removePrefix("spot_")
            val selectedSpot = spots.find { it.internalPoolIndex == name.toInt() }
            return selectedSpot
        } else {
            return null
        }
    }

    /** Tries to find a spot instance in the current time point for the given [spot].
     * It does that by filtering through the names of the instances, which contain the internalPoolIndex.
     * @return either an [InstancedNode.Instance] or null. */
    fun findInstanceFromSpot(spot: Spot): InstancedNode.Instance? {
        return spotPool.find { it.name.removePrefix("spot_").toInt() == spot.internalPoolIndex }
    }

    /** Tries to find a link instance for the given [link].
     * It does that by filtering through the names, which contain the internalPoolIndex. */
    fun findInstanceFromLink(link: Link): InstancedNode.Instance? {
        val results = links.filterValues { it.instance.name.toInt() == link.internalPoolIndex }
        return if (results.isNotEmpty()) {
            results.entries.first().value.instance
        } else {
            null
        }
    }

    fun selectSpot(instance: InstancedNode.Instance) {
        // if one accidentally clicks a link instance and triggers this function, don't continue
        val selectedSpot = findSpotFromInstance(instance)
        selectedSpot?.let {
            // Remove previous selections first
            clearSpotSelection()
            mastodonData.focusModel.focusVertex(it)
            mastodonData.highlightModel.highlightVertex(it)
            mastodonData.selectionModel.setSelected(it, true)
        }
    }

    fun clearSpotSelection() {
        mastodonData.focusModel.focusVertex(null)
        mastodonData.selectionModel.clearSelection()
        mastodonData.highlightModel.clearHighlight()
    }

    /** Takes the given spot instance that was already moved in Sciview and moves it in the BDV window.  */
    fun moveSpotInBDV(instance: InstancedNode.Instance?, distance: Vector3f) {
        val selectedSpot = instance?.let { findSpotFromInstance(it) }
        selectedSpot?.let {
            mastodonData.model.graph.vertexRef().refTo(selectedSpot).move(distance.toFloatArray())
        }
    }

    /** Takes the given spot that was already moved in the BDV window and moves it in Sciview.
     * It also updates the connected edges and it is also called when a vertex is scaled on the BDV side. */
    fun moveAndScaleSpotInSciview(spot: Spot) {
        val selectedInstance = findInstanceFromSpot(spot)
        val spotPosition = FloatArray(3)
        spot.localize(spotPosition)
        selectedInstance?.spatial {
            position = Vector3f(spotPosition)
            scale = Vector3f(sphereScaleFactor *  sqrt(spot.boundingSphereRadiusSquared.toFloat()) / 50f)
        }
        val edges = spot.incomingEdges() + spot.outgoingEdges()
        for (edge in edges) {
            findInstanceFromLink(edge)?.let {
                setLinkTransforms(edge.source, edge.target, it)
            }
        }
    }

    /** Called when a spot is scaled in the sciview window.
     * This function then scales both the instance and the vertex on the BDV side.
     * Setting the [direction] to true means to scale up, false means scale down. */
    fun scaleSpotAndInstance(instance: InstancedNode.Instance?, direction: Boolean) {
        val factor = if (direction) 1.1 else 0.9
        instance?.let {
            val spot = findSpotFromInstance(it)
            val covArray = Array(3) { DoubleArray(3) }
            spot?.getCovariance(covArray)
            for (i in covArray.indices) {
                for (j in covArray[i].indices) {
                    covArray[i][j] *= factor
                }
            }
            spot?.setCovariance(covArray)
            mastodonData.model.graph.notifyGraphChanged()
            it.spatial().scale *= Vector3f(factor.toFloat())
        }
    }

    fun updateLinkTransforms(edges: MutableList<Link>) {
        for (edge in edges) {
            findInstanceFromLink(edge)?.let {
                setLinkTransforms(edge.source, edge.target, it)
            }
        }
    }

    /** Sort a list of instances by their distance to a given [origin] position (e.g. of the camera)
     * @return a sorted copy of the mutable instance list.*/
    fun sortInstancesByDistance(
        spots: MutableList<InstancedNode. Instance>, origin: Vector3f
    ): MutableList<InstancedNode.Instance> {

        val start = TimeSource.Monotonic.markNow()
        val sortedSpots = spots.sortedBy { it.spatial().position.distance(origin) }.toMutableList()
        val end = TimeSource.Monotonic.markNow()
        logger.info("Spot sorting took ${end - start}.")
        return sortedSpots
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

    /** Shows or initializes the main links instance, publishes it to the scene and populates it with instances from the current Mastodon graph. */
    fun showInstancedLinks(
        colorMode: ColorMode,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        val tStart = TimeSource.Monotonic.markNow()

        links.clear()
        if (mainLinkInstance == null) {
            cylinder.setMaterial(
                ShaderMaterial.fromFiles("DeferredInstancedColor.vert", "DeferredInstancedColor.frag" )
            ) {
                diffuse = Vector3f(1.0f, 1.0f, 1.0f)
                ambient = Vector3f(1.0f, 1.0f, 1.0f)
                specular = Vector3f(.0f, 1.0f, 1.0f)
                metallic = 0.0f
                roughness = 1.0f
            }
            val mainLink = InstancedNode(cylinder)
            mainLink.name = "LinkInstance"
            mainLink.instancedProperties["Color"] = { Vector4f(1f) }

            // initialize the whole pool with instances once
            for (i in 0..<10000) {
                linkPool.add(mainLink.addInstance())
            }
            logger.info("initialized mainLinkInstance")
            sv.addNode(mainLink, parent = linkParentNode)
            mainLinkInstance = mainLink
        }

        val mainLink = mainLinkInstance ?: throw IllegalStateException("InstancedLink is null, instance was not initialized.")

        currentColorMode = colorMode
        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(0)
        numTimePoints = mastodonData.maxTimepoint

        var inst: InstancedNode.Instance
        var index = 0
        val start = TimeSource.Monotonic.markNow()
        // TODO use coroutines for this
        logger.info("iterating over ${mastodonData.model.graph.edges().size} mastodon edges...")
        mastodonData.model.graph.edges().forEach { edge ->

            // reuse a link instance from the pool if the pool is large enough
            if (index < linkPool.size) {
                inst = linkPool[index]
                inst.visible = true
            }
            // otherwise create a new instance and add it to the pool
            else {
                inst = mainLink.addInstance()
//                inst.addAttribute(Material::class.java, cylinder.material())
                inst.parent = linkParentNode
                linkPool.add(inst)
            }

            val from = edge.source
            val to = edge.target

            setLinkTransforms(from, to, inst)
            inst.instancedProperties["Color"] = { Vector4f(1f, 1f, 1f, 1f) }
            inst.name = "${edge.internalPoolIndex}"
            inst.parent = linkParentNode
            // add a new key-value pair to the hash map
            links[to.hashCode()] = LinkNode(inst, from, to, to.timepoint)

            index++
        }

        // turn all leftover links from the pool invisible
        var i = index
        while (i < linkPool.size) {
            linkPool[i++].visible = false
        }
        logger.info("link content is ${links.size}, and mainLinkInstance has ${mainLinkInstance!!.instances.size} links")
        val end = TimeSource.Monotonic.markNow()

        logger.info("Edge traversel took ${end - start}.")
        logger.info("Found a total of ${links.size} links. Should be ${mastodonData.model.graph.edges().size}.")
        // first update the link colors without providing a colorizer, because no BDV window has been opened yet
        updateLinkColors(colorizer)


        val tElapsed = TimeSource.Monotonic.markNow() - tStart
        logger.info("Link updates took $tElapsed")
    }

    /** Takes a cylinder instance [inst] and two spots, [from] and [to], and positions the cylinder between them. */
    fun setLinkTransforms(from: Spot, to: Spot, inst: InstancedNode.Instance) {

        // temporary container to get the position as array
        val pos = FloatArray(3)
        from.localize(pos)
        val posOrigin = Vector3f(pos)
        to.localize(pos)
        val posTarget = Vector3f(pos)
        posTarget.sub(posOrigin)
        inst.spatial {
            scale.set(linkSize, posTarget.length().toDouble(), linkSize)
            rotation = Quaternionf().rotateTo(Vector3f(0f, 1f, 0f), posTarget).normalize()
            position = Vector3f(posOrigin)
        }
    }

    /** Traverse and update the colors of all [links] using the provided color mode [cm].
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
                    for (tp in 0 .. numTimePoints) {
                        val spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(tp)
                        spots.forEach { spot ->
                            links[spot.hashCode()]?.instance?.setColorFromSpot(spot, colorizer)
                        }
                    }
                }
            }
        }
        val end = TimeSource.Monotonic.markNow()
        logger.debug("Updating link colors took ${end - start}.")
    }

    fun updateLinkVisibility(currentTP: Int) {
        links.forEach {link ->
            // turns the link on if it is within range, otherwise turns it off
            link.value.instance.visible = link.value.tp in currentTP - linkBackwardRange..currentTP + linkForwardRange
        }
    }

    /** Passed to EyeTracking to send a list of vertices from sciview to Mastodon. */
    val addTrackToMastodon: (List<Pair<Vector3f, SpineGraphVertex>>) -> Unit = { list ->
        logger.info("got this track list: l${list.joinToString { ", " }}")
        var prevVertex: Spot? = null

        list.forEachIndexed { index, (pos, spineVertex) ->
            val v = mastodonData.model.graph.addVertex()
            val p = pos.toDoubleArray()
            v.init(spineVertex.timepoint, p, 20.0)
            logger.info("added vertex $v at position $pos")
            // start adding edges once the first vertex was added
            if (index > 0) {
                val e = mastodonData.model.graph.addEdge(prevVertex, v)
                e.init()
                mastodonData.model.graph.notifyGraphChanged()
            }
            prevVertex = v
        }
    }

    /** Lambda that is passed to sciview to send individual spots from sciview to Mastodon. */
    val addSpotToMastodon: (Int, Vector3f) -> Unit = { tp, sciviewPos ->
        val pos = bridge.sciviewToMastodonCoords(sciviewPos)
        val bb = bridge.volumeNode.boundingBox
        if (bb != null) {
            if (bb.isInside(pos)) {
                val v = mastodonData.model.graph.addVertex()
                v.init(tp, pos.toDoubleArray(), 20.0)
                logger.info("Added new spot with controller at position $pos.")
            } else {
                logger.warn("Not adding new spot, $pos is outside the volume!")
            }
        }
    }

    val linkSize = 2.0

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

    // TODO also deprecated. We loop over all edges without needing recursion
    fun updateLinks(TPsInPast: Int, TPsAhead: Int) {
//        logger.info("updatelinks!")
//        refSpot?.let {
//            clearLinksOutsideRange(it.timepoint, it.timepoint)
//            backwardSearch(it, it.timepoint - TPsInPast)
//            forwardSearch(it, it.timepoint + TPsAhead)
//        }
//        events?.publish(NodeChangedEvent(linksNodesHub))
    }

    fun addLinkToMastodon(from: Spot, to: Spot) {
        mastodonData.model.graph.addEdge(from, to)
    }

    /** Recursive method that traverses the links of the provided [origin] up until the given timepoint [toTP].
     * Forward search is enabled when [forward] is true, otherwise it searches backwards. */
    // TODO probably not needed anymore. Just keeping this here in case I am wrong.
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
//            for (l in spot.outgoingEdges()) {
//                if (l.getTarget(targetRef).timepoint > spot.timepoint && targetRef.timepoint <= toTP) {
//                    addLink(spot, targetRef, mainInstance, colorizer)
//                    searchAndConnectSpots(targetRef, toTP, colorizer, true)
//                }
//            }
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

    fun clearAllLinks() {
        links.clear()
        minTP = 999999
        maxTP = -1
    }

    fun setupEmptyLinks() {
        links = ConcurrentHashMap()
        minTP = 999999
        maxTP = -1
    }

    companion object {
        const val NAME_OF_NOT_USED_SPHERES = "not used now"
    }
}

data class LinkNode (val instance: InstancedNode.Instance, val from: Spot, val to: Spot, val tp: Int)