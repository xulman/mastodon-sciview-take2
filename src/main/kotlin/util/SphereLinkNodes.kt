package util

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.extensions.*
import graphics.scenery.utils.lazyLogger
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
import sc.iview.event.NodeChangedEvent
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.sqrt

class SphereLinkNodes(
    val sv: SciView,
    val sphereParentNode: Node,
    val linkParentNode: Node
) {

    private val logger by lazyLogger()
    var SCALE_FACTOR = 1f
    var DEFAULT_COLOR = 0x00FFFFFF
    val spotPool: MutableList<InstancedNode.Instance> = ArrayList()
    private var spotRef: Spot? = null
    var events: EventService? = null

    init {
        events = sv.scijavaContext?.getService(EventService::class.java)
    }

    /** Data class that combines the mastodon spot, the corresponding instance and its time-point. */
    data class IndexedSpotInstance(val spot: Spot, val instance: InstancedNode.Instance, val tp: Int)

    val sphere = Icosphere(1f, 2)
    val cylinder = Cylinder(1f, 1f, 4)
    lateinit var mainSpotInstance: InstancedNode
    lateinit var mainLinkInstance: InstancedNode

    lateinit var spots: SpatialIndex<Spot>

    /** Shows or initializes the main spot instance, publishes it to the scene and populates it with instances from the current time-point. */
    fun showInstancedSpots(
        mastodonData: ProjectModel,
        timepoint: Int,
        colorizer: GraphColorGenerator<Spot, Link>,
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
                scale = axisLengths * SCALE_FACTOR
                rotation = matrixToQuaternion(eigenvectors)
            }
            setInstancedSphereColor(inst, colorizer, spot,false)

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
        colorizer: GraphColorGenerator<Spot, Link>,
        s: Spot,
        randomColors: Boolean = false,
        isPartyMode: Boolean = false
    ) {
        var intColor = colorizer.color(s)
        if (intColor == 0x00000000) intColor = DEFAULT_COLOR
        val r = (intColor shr 16 and 0x000000FF) / 255f
        val g = (intColor shr 8 and 0x000000FF) / 255f
        val b = (intColor and 0x000000FF) / 255f
        if (isPartyMode) {
            val col = Random.random3DVectorFromRange(0f, 1f)
            inst.instancedProperties["Color"] = { col.xyzw() }
        } else {
            if (!randomColors) {
                inst.instancedProperties["Color"] = { Vector4f(r, g, b, 1.0f) }
            } else {
                val col = Random.random3DVectorFromRange(0f, 1f).stretchColor()
                inst.instancedProperties["Color"] = { col.xyzw() }
            }
        }
    }

    fun decreaseSphereScale() {
        val oldScale = SCALE_FACTOR
        SCALE_FACTOR -= 0.1f
        if (SCALE_FACTOR < 0.1f) SCALE_FACTOR = 0.1f
        val factor = SCALE_FACTOR / oldScale
        mainSpotInstance.instances.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Decreasing scale to $SCALE_FACTOR, by factor $factor")
    }

    fun increaseSphereScale() {
        val oldScale = SCALE_FACTOR
        SCALE_FACTOR += 0.1f
        val factor = SCALE_FACTOR / oldScale
        mainSpotInstance.instances.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Increasing scale to $SCALE_FACTOR, by factor $factor")
    }


    fun initializeInstancedLinks(
        mastodonData: ProjectModel,
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
        val foo = mainLinkInstance.addInstance()
        foo.addAttribute(Material::class.java, cylinder.material())
        foo.parent = linkParentNode

        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(10)
        for (spot in spots) {
            forwardSearch(spot, 3)//mastodonData.maxTimepoint)
        }
        logger.info("iterated everything, we now have ${links?.size} links and ${mainLinkInstance.instances.size} instances")
    }


    val linkSize = 1.0

    var linksNodesHub: Node? = null // gathering node in sciview -- a links node associated to its spots node
    var links: MutableList<LinkNode>? = null // list of links of this spot

    var selectionStorage: Node = RichNode()
    var refSpot: Spot? = null
    var minTP = 0
    var maxTP = 0

    var linkRadius: Float = 0.01f
    private val pos = FloatArray(3)
    private val posF = Vector3f()
    private val posT = Vector3f()

    fun registerNewSpot(spot: Spot) {
        if (refSpot != null) refSpot!!.modelGraph.releaseRef(refSpot)
        refSpot = spot.modelGraph.vertexRef()
        refSpot?.refTo(spot)
        minTP = spot.timepoint
        maxTP = minTP
    }

    private fun addLink(from: Spot, to: Spot) {
        from.localize(pos)
        to.localize(pos)

        posT.sub(posF)

        //NB: posF is base of the "vector" link, posT is the "vector" link itself
//        val node = Cylinder(linkRadius, posT.length(), 8)
        val inst = mainLinkInstance.addInstance()
        inst.addAttribute(Material::class.java, cylinder.material())
        inst.spatial {
            scale.set(linkSize * 100, 1.0, linkSize * 100)
            rotation = Quaternionf().rotateTo(Vector3f(0f, 1f, 0f), posT).normalize()
            position = Vector3f(posF)
        }

        inst.name = from.label + " --> " + to.label
        inst.parent = linkParentNode
        //node.setMaterial( linksNodesHub.getMaterial() );
        logger.info("add instance at ${inst.spatial().position} with scale ${inst.spatial().scale} and rot ${inst.spatial().rotation}")
//        linksNodesHub?.addChild(node)
        links?.addLast(LinkNode(inst, from.timepoint, to.timepoint))

        minTP = minTP.coerceAtMost(from.timepoint)
        maxTP = maxTP.coerceAtLeast(to.timepoint)
    }

    fun updateLinks(TPsInPast: Int, TPsAhead: Int) {
        logger.info("updatelinks!")
        refSpot?.let {
            clearLinksOutsideRange(it.timepoint, it.timepoint)
            backwardSearch(it, it.timepoint - TPsInPast)
            forwardSearch(it, it.timepoint + TPsAhead)
        }
        events?.publish(NodeChangedEvent(linksNodesHub))
    }

    private fun forwardSearch(spot: Spot, toTP: Int) {

        if (spot.timepoint >= toTP) return
        //enumerate all forward links
        val s = spot.modelGraph.vertexRef()
        for (l in spot.incomingEdges()) {
            if (l.getSource(s).timepoint > spot.timepoint && s.timepoint <= toTP) {
                addLink(spot, s)
                forwardSearch(s, toTP)
            }
        }
        for (l in spot.outgoingEdges()) {
            if (l.getTarget(s).timepoint > spot.timepoint && s.timepoint <= toTP) {
                addLink(spot, s)
                forwardSearch(s, toTP)
            }
        }
        spot.modelGraph.releaseRef(s)
    }

    private fun backwardSearch(spot: Spot, fromTP: Int) {
        if (spot.timepoint <= fromTP) return
        //enumerate all backward links
        val s = spot.modelGraph.vertexRef()
        for (l in spot.incomingEdges()) {
            logger.info("backward search: incoming edges")
            if (l.getSource(s).timepoint < spot.timepoint && s.timepoint >= fromTP) {
                addLink(s, spot)
                backwardSearch(s, fromTP)
            }
        }
        for (l in spot.outgoingEdges()) {
            logger.info("backward search: outgoing edges")
            if (l.getTarget(s).timepoint < spot.timepoint && s.timepoint >= fromTP) {
                addLink(s, spot)
                backwardSearch(s, fromTP)
            }
        }
        spot.modelGraph.releaseRef(s)
    }

    fun clearLinksOutsideRange(fromTP: Int, toTP: Int) {
        links?.iterator().let {
            while (it?.hasNext() == true) {
                val link = it.next()
                if (link.fromTP < fromTP || link.toTP > toTP) {
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
        links?.clear()
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

data class LinkNode (var instance: InstancedNode.Instance, var fromTP: Int, var toTP: Int)