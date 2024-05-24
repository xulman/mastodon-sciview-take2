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
import kotlin.collections.ArrayList
import kotlin.math.sqrt

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
    lateinit var colorizer: GraphColorGenerator<Spot, Link>
    var numTimePoints: Int
    var lut: ColorTable
    lateinit var currentColorMode: colorMode
    val spotPool: MutableList<InstancedNode.Instance> = ArrayList()
    private var spotRef: Spot? = null
    var events: EventService? = null

    val sphere = Icosphere(1f, 2)
    val cylinder = Cylinder(0.2f, 1f, 6, true, true)
    lateinit var mainSpotInstance: InstancedNode
    lateinit var mainLinkInstance: InstancedNode
    lateinit var spots: SpatialIndex<Spot>

    init {
        events = sv.scijavaContext?.getService(EventService::class.java)
        numTimePoints = mastodonData.maxTimepoint

        lut = sv.getLUT("Fire.lut")
        currentColorMode = colorMode.WHITE
    }

    /** The following types are allowed for track coloring:
     * - [LUT] uses a colormap, defaults to Fire.lut
     * - [GREYS] uses a gradient from black to white
     * - [WHITE] pure white color
     * - [RAINBOW] uses saturated colors across the whole hue spectrum
     * - [SPOT] uses the spot color from the connected spot */
    enum class colorMode { LUT, GREYS, WHITE, RAINBOW, SPOT }

    fun updateColorizer(colorizer: GraphColorGenerator<Spot, Link>) {
        this.colorizer = colorizer
    }

    /** Shows or initializes the main spot instance, publishes it to the scene and populates it with instances from the current time-point. */
    fun showInstancedSpots(
        timepoint: Int,
        initializing: Boolean = false
    ) {
        // only create and add the main instance once during initialization
        if (initializing) {
            sphere.setMaterial(ShaderMaterial.fromFiles("DeferredInstancedColor.vert", "DeferredInstancedColor.frag")) {
                diffuse = Vector3f(1.0f, 1.0f, 1.0f)
                ambient = Vector3f(1.0f, 1.0f, 1.0f)
                specular = Vector3f(.0f, 1.0f, 1.0f)
                metallic = 0.0f
                roughness = 1.0f
            }

            mainSpotInstance = InstancedNode(sphere)
            // Instanced properties should be aligned to 4*32bit boundaries, hence the use of Vector4f instead of Vector3f here
            mainSpotInstance.instancedProperties["Color"] = { Vector4f(1f) }
            sv.addNode(mainSpotInstance, parent = sphereParentNode)
        }

        if (spotRef == null) spotRef = mastodonData.model.graph.vertexRef()
        val focusedSpotRef = mastodonData.focusModel.getFocusedVertex(spotRef)
        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(timepoint)
        sv.blockOnNewNodes = false

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
                inst = mainSpotInstance.addInstance()
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
                rotation = matrixToQuaternion(eigenvectors)
            }
            setInstancedSphereColor(inst, spot,false)
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

    private val spotPosition = FloatArray(3)

    fun computeEigen(covariance: Array2DRowRealMatrix): Pair<DoubleArray, RealMatrix> {
        val eigenDecomposition = EigenDecomposition(covariance)
        val eigenvalues = eigenDecomposition.realEigenvalues
        val eigenvectors = eigenDecomposition.v
        return Pair(eigenvalues, eigenvectors)
    }

    fun computeSemiAxes(eigenvalues: DoubleArray): Vector3f {
        return Vector3f(
            sqrt(eigenvalues[0]).toFloat(),
            sqrt(eigenvalues[1]).toFloat(),
            sqrt(eigenvalues[2]).toFloat()
        )
    }

    fun matrixToQuaternion(eigenvectors: RealMatrix): Quaternionf {
        val matrix3f = Matrix3f()
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                matrix3f.set(j, i, eigenvectors.getEntry(j, i).toFloat())
            }
        }
        return Quaternionf().setFromUnnormalized(matrix3f)
    }

    // stretch color channels
    fun Vector3f.stretchColor(): Vector3f {
        this.x.coerceIn(0f, 1f)
        this.y.coerceIn(0f, 1f)
        this.z.coerceIn(0f, 1f)
        val max = this.max()
        return this + Vector3f(1 - max)
    }

    fun setInstancedSphereColor(
        inst: InstancedNode.Instance,
        s: Spot,
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
                inst.instancedProperties["Color"] = { col }
            } else {
                val col = Random.random3DVectorFromRange(0f, 1f).stretchColor()
                inst.instancedProperties["Color"] = { col.xyzw() }
            }
        } else {
            inst.instancedProperties["Color"] = { Random.random3DVectorFromRange(0f, 1f).xyzw() }
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
        mainSpotInstance.instances.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Decreasing scale to $sphereScaleFactor, by factor $factor")
    }

    fun increaseSphereScale() {
        val oldScale = sphereScaleFactor
        sphereScaleFactor += 0.1f
        val factor = sphereScaleFactor / oldScale
        mainSpotInstance.instances.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Increasing scale to $sphereScaleFactor, by factor $factor")
    }

    fun increaseLinkScale() {
        val oldScale = linkScaleFactor
        linkScaleFactor += 0.2f
        val factor = linkScaleFactor / oldScale
        mainLinkInstance.instances.forEach { l -> l.spatial().scale *= Vector3f(factor, 1f, factor) }
        logger.debug("Increasing scale to $linkScaleFactor, by factor $factor")
    }

    fun decreaseLinkScale() {
        val oldScale = linkScaleFactor
        linkScaleFactor -= 0.2f
        val factor = linkScaleFactor / oldScale
        mainLinkInstance.instances.forEach { l -> l.spatial().scale *= Vector3f(factor, 1f, factor) }
        logger.debug("Decreasing scale to $linkScaleFactor, by factor $factor")
    }

    fun initializeInstancedLinks(
        colorMode: colorMode = SphereLinkNodes.colorMode.WHITE
    ) {
        cylinder.setMaterial(ShaderMaterial.fromFiles("DeferredInstancedColor.vert", "DeferredInstancedColor.frag")) {
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
            ambient = Vector3f(1.0f, 1.0f, 1.0f)
            specular = Vector3f(.0f, 1.0f, 1.0f)
            metallic = 0.0f
            roughness = 1.0f
        }

        mainLinkInstance = InstancedNode(cylinder)
        mainLinkInstance.instancedProperties["Color"] = { Vector4f(1f) }
        sv.addNode(mainLinkInstance, parent = linkParentNode)

        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(0)
        numTimePoints = mastodonData.maxTimepoint
        spots.forEach { spot ->
            forwardSearch(spot, numTimePoints)
        }
    }

    fun updateInstancedLinkColors (
        colorMode: colorMode = SphereLinkNodes.colorMode.WHITE
    ) {
        for (link in links) {
//            setInstancedSphereColor(link.instance, link.from)

            val intColor = colorizer.color(link.from)
            val color = unpackRGB(intColor)
            logger.info("got link color ${color} from TP ${link.from.timepoint} with inst ${link.instance.name}")
            link.instance.instancedProperties["Color"] = { color }
        }
    }


    val linkSize = 2.0

    var linksNodesHub: Node? = null // gathering node in sciview -- a links node associated to its spots node
    var links: MutableList<LinkNode> = ArrayList() // list of links of this spot

    var selectionStorage: Node = RichNode()
    var refSpot: Spot? = null
    var minTP = 0
    var maxTP = 0

    private val pos = FloatArray(3)
    private var posF = Vector3f()
    private var posT = Vector3f()
    lateinit var inst: InstancedNode.Instance

    fun registerNewSpot(spot: Spot) {
        if (refSpot != null) refSpot!!.modelGraph.releaseRef(refSpot)
        refSpot = spot.modelGraph.vertexRef()
        refSpot?.refTo(spot)
        minTP = spot.timepoint
        maxTP = minTP
    }

    private fun addLink(from: Spot, to: Spot) {
        from.localize(pos)
        posF = Vector3f(pos)
        to.localize(pos)
        posT = Vector3f(pos)

        posT.sub(posF)
        //NB: posF is base of the "vector" link, posT is the "vector" link itself
        inst = mainLinkInstance.addInstance()
        inst.addAttribute(Material::class.java, cylinder.material())

        val color = unpackRGB(lut.lookupARGB(0.0, 1.0, to.timepoint / numTimePoints.toDouble()))
//        val color = hsvToArgb((from.timepoint.toFloat() / numTimePoints.toFloat() * 360).toInt(), 100, 100)
        inst.instancedProperties["Color"] = { color }
        inst.spatial {
            scale.set(linkSize, posT.length().toDouble(), linkSize)
            rotation = Quaternionf().rotateTo(Vector3f(0f, 1f, 0f), posT).normalize()
            position = Vector3f(posF)
        }

        inst.name = from.label + " --> " + to.label
        inst.parent = linkParentNode
//        setInstancedSphereColor(inst, from)
        links.add(LinkNode(inst, from, to))

        minTP = minTP.coerceAtMost(from.timepoint)
        maxTP = maxTP.coerceAtLeast(to.timepoint)
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

    private fun forwardSearch(spot: Spot, toTP: Int) {

        if (spot.timepoint >= toTP) return
        //enumerate all forward links
        val spotRef = spot.modelGraph.vertexRef()
        for (l in spot.incomingEdges()) {
            if (l.getSource(spotRef).timepoint > spot.timepoint && spotRef.timepoint <= toTP) {
                addLink(spot, spotRef)
                forwardSearch(spotRef, toTP)
            }
        }
        for (l in spot.outgoingEdges()) {
            if (l.getTarget(spotRef).timepoint > spot.timepoint && spotRef.timepoint <= toTP) {
                addLink(spot, spotRef)
                forwardSearch(spotRef, toTP)
            }
        }
        spot.modelGraph.releaseRef(spotRef)
    }

    private fun backwardSearch(spot: Spot, fromTP: Int) {
        if (spot.timepoint <= fromTP) return
        //enumerate all backward links
        val spotRef = spot.modelGraph.vertexRef()
        for (l in spot.incomingEdges()) {
            if (l.getSource(spotRef).timepoint < spot.timepoint && spotRef.timepoint >= fromTP) {
                addLink(spotRef, spot)
                backwardSearch(spotRef, fromTP)
            }
        }
        for (l in spot.outgoingEdges()) {
            if (l.getTarget(spotRef).timepoint < spot.timepoint && spotRef.timepoint >= fromTP) {
                addLink(spotRef, spot)
                backwardSearch(spotRef, fromTP)
            }
        }
        spot.modelGraph.releaseRef(spotRef)
    }

    fun clearLinksOutsideRange(fromTP: Int, toTP: Int) {
        links.iterator().let {
            while (it.hasNext() == true) {
                val link = it.next()
                if (link.from.timepoint < fromTP || link.to.timepoint > toTP) {
                    linksNodesHub?.removeChild(link.instance)
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
        links = LinkedList()
        minTP = 999999
        maxTP = -1
    }

    companion object {
        const val NAME_OF_NOT_USED_SPHERES = "not used now"
    }
}

data class LinkNode (var instance: InstancedNode.Instance, var from: Spot, var to: Spot)