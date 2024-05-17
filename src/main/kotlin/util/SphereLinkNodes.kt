package util

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.extensions.*
import graphics.scenery.utils.lazyLogger
import org.apache.commons.math3.stat.correlation.Covariance
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
import kotlin.math.sqrt
import kotlin.time.TimeSource


//TODO FAILED to hook up here a 'parentNode' listener that would setVisible(false) on all children
// of the parent node that are "not used"
class SphereLinkNodes
    (val sv: SciView, val parentNode: Node) {
        private val logger by lazyLogger()
        var SCALE_FACTOR = 1f
        var DEFAULT_COLOR = 0x00FFFFFF
        val knownNodes: MutableList<Sphere> = ArrayList(1000)
        val addedExtraNodes: MutableList<Sphere> = LinkedList()
        val spotList: MutableList<IndexedSpotInstance> = LinkedList()
        private var spotRef: Spot? = null
        var events: EventService? = null

    init {
        events = sv.scijavaContext?.getService(EventService::class.java)
    }

    /** Data class that combines the spatio-temporal index and the corresponding instance. */
    data class IndexedSpotInstance(val spot: Spot, val instance: InstancedNode.Instance)

    val sphere = Icosphere(1f, 2)
    lateinit var mainInstance: InstancedNode
    lateinit var spots: SpatialIndex<Spot>

    /** Initializes the main spot instance, publishes it to the scene and populates it with instances from time-point 0. */
    fun initializeInstancedSpots(
        mastodonData: ProjectModel,
        timepoint: Int,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        logger.debug("Initializing Spots")
        sphere.setMaterial(ShaderMaterial.fromFiles("DeferredInstancedColor.vert", "DeferredInstancedColor.frag")) {
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
            ambient = Vector3f(1.0f, 1.0f, 1.0f)
            specular = Vector3f(.0f, 1.0f, 1.0f)
            metallic = 0.0f
            roughness = 1.0f
        }
        mainInstance = InstancedNode(sphere)
        // Instanced properties should be aligned to 4*32bit boundaries, hence the use of Vector4f instead of Vector3f here
        mainInstance.instancedProperties["Color"] = { Vector4f(1f) }
        if (spotRef == null) spotRef = mastodonData.model.graph.vertexRef()
        val focusedSpotRef = mastodonData.focusModel.getFocusedVertex(spotRef)
        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(timepoint)
        sv.blockOnNewNodes = false

        sv.addNode(mainInstance, parent = parentNode)
        val covArray = Array(3) { DoubleArray(3) }
        var cov: Covariance
        var inst: InstancedNode.Instance
        for (s in spots) {
            inst = mainInstance.addInstance()
            inst.name = "${s.internalPoolIndex}"
            inst.addAttribute(Material::class.java, sphere.material())
            // add the instance to spotList, connected with the corresponding spatio-temporal index
            spotList.add(IndexedSpotInstance(s, inst))
            s.localize(spotPosition)
            s.getCovariance(covArray)
            cov = Covariance(covArray)
            if (s.internalPoolIndex%5 == 0) {
                logger.info("covariance for spot ${s.internalPoolIndex} is ${cov.covarianceMatrix}")
            }
            inst.spatial {
                position = Vector3f(spotPosition)
            }
            inst.spatial().scale = Vector3f(
                SCALE_FACTOR * sqrt(s.boundingSphereRadiusSquared).toFloat()
            )
            setInstancedSphereColor(inst, colorizer, s,false)
            inst.parent = parentNode
            logger.info("initialized spot ${s.internalPoolIndex} at pos ${inst.spatial().position}")
        }
    }

    fun updateInstancedSpots(
        mastodonData: ProjectModel,
        timepoint: Int,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(timepoint)
        logger.info("updating: got ${spots.size()} spots for timepoint $timepoint")
        for (s in spots) {
            s.localize(spotPosition)
            val existingSpot = spotList.find { it.spot == s }
//            logger.info("found spot ${s.internalPoolIndex}: ${existingSpot != null}")
            if (existingSpot != null) {
                logger.info("found spot ${s.internalPoolIndex} in existing spots")
                // update existing spot
                existingSpot.instance.visible = true
                existingSpot.instance.spatial {
                    position = Vector3f(spotPosition)
                }
                logger.info("updated existing spot ${s.internalPoolIndex} to pos ${existingSpot.instance.spatial().position}")
            } else {
                logger.info("added spot ${s.internalPoolIndex} to list because it didn't exist")
                // create a new spot if none exists in spotList yet
                val inst = mainInstance.addInstance()
                inst.addAttribute(Material::class.java, sphere.material())
                spotList.add(IndexedSpotInstance(s, inst))
                inst.spatial {
                    position = Vector3f(spotPosition)
                }
                setInstancedSphereColor(inst, colorizer, s,false)
                inst.parent = parentNode
                logger.info("added new spot ${s.internalPoolIndex} at pos ${inst.spatial().position}")
            }
        }

        // disable all left-over spots that exist in spotList but not in the current time-point
//        val spotsToDisable = spotList.filter { inst ->
//            !spots.any { spot -> spot == inst.spot }
//        }
        for (s in spotList) {
            val existingSpot = spots.find { it == s.spot }
            if (existingSpot == null) {
                s.instance.visible = false
            }
        }
//        logger.info("disabled ${spotsToDisable.size} spots")

//        for (s in spotsToDisable) {
//            s.instance.visible = false
//        }
    }

    fun showTheseSpots(
        mastodonData: ProjectModel,
        timepoint: Int,
        colorizer: GraphColorGenerator<Spot, Link>
    ): Int {
        var visibleNodeCount = 0
        addedExtraNodes.clear()

        //nodes should honor if they are wished to be immediately visible or not
        val finalVisibilityState = parentNode.visible
        if (spotRef == null) spotRef = mastodonData.model.graph.vertexRef()
        val focusedSpotRef = mastodonData.focusModel.getFocusedVertex(spotRef)
        val spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(timepoint)
        var node: Sphere
        sv.blockOnNewNodes = false
        for (s in spots) {
            if (visibleNodeCount < knownNodes.size) {
                //injecting into known nodes (already registered with sciview)
                node = knownNodes[visibleNodeCount]
                node.visible = finalVisibilityState
                //NB: make sure it's visible (could have got hidden when there were fewer spots before)
            } else {
                //adding some new nodes
                node = Sphere()
                node.visible = finalVisibilityState
                sv.addNode(node, false, parentNode)
                addedExtraNodes.add(node)
            }
            setSphereNode(node, s, colorizer)
            if (focusedSpotRef != null && focusedSpotRef.internalPoolIndex == s.internalPoolIndex) {
                node.material().wireframe = true
            }

//            setupEmptyLinks()
//            registerNewSpot(s)
//            updateLinks(30, 30)

            ++visibleNodeCount
        }
        if (addedExtraNodes.size > 0) {
            //NB: also means that the knownNodes were fully exhausted
            knownNodes.addAll(addedExtraNodes)
            sv.publishNode(addedExtraNodes[0]) //NB: publishes only once
            logger.info("Added ${addedExtraNodes.size} new spheres")
        } else {
            logger.debug("Hide ${(knownNodes.size-visibleNodeCount)} spheres")
            //NB: mark not-touched knownNodes as hidden
            var i = visibleNodeCount
            while (i < knownNodes.size) {
                knownNodes[i].name = NAME_OF_NOT_USED_SPHERES
                knownNodes[i++].visible = false
            }
        }
        return visibleNodeCount
    }

    private val spotPosition = FloatArray(3)
    private val minusThisOffset = floatArrayOf(0f, 0f, 0f)
    fun setDataCenter(center: Vector3f) {
        minusThisOffset[0] = center.x
        minusThisOffset[1] = center.y
        minusThisOffset[2] = center.z
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
//        var inst = mainInstance.instances.find { i -> i.name == s.internalPoolIndex.toString() }
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

    private fun setSphereNode(
        node: Sphere,
        s: Spot,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        node.name = s.label
        s.localize(spotPosition)
        spotPosition[0] -= minusThisOffset[0]
        spotPosition[1] -= minusThisOffset[1]
        spotPosition[2] -= minusThisOffset[2]
        spotPosition[1] *= -1f
        spotPosition[2] *= -1f
        node.spatial().setPosition(spotPosition)
        node.spatial().scale = Vector3f(
            SCALE_FACTOR * sqrt(s.boundingSphereRadiusSquared).toFloat()
        )
        var intColor = colorizer.color(s)
        if (intColor == 0x00000000) intColor = DEFAULT_COLOR
        val r = (intColor shr 16 and 0x000000FF) / 255f
        val g = (intColor shr 8 and 0x000000FF) / 255f
        val b = (intColor and 0x000000FF) / 255f
        node.material().diffuse[r, g] = b
//        node.material().diffuse = Vector3f(r, g, b)
//        node.material().blending = Blending(transparent = true, opacity = 0.2f)
        node.material().wireframe = false
    }

    fun decreaseSphereScale() {
        val oldScale = SCALE_FACTOR
        SCALE_FACTOR -= 0.1f
        if (SCALE_FACTOR < 0.1f) SCALE_FACTOR = 0.1f
        val factor = SCALE_FACTOR / oldScale
//        knownNodes.forEach { s: Sphere -> s.spatial().scale *= Vector3f(factor)  }
        mainInstance.instances.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Decreasing scale to $SCALE_FACTOR, by factor $factor")
    }

    fun increaseSphereScale() {
        val oldScale = SCALE_FACTOR
        SCALE_FACTOR += 0.1f
        val factor = SCALE_FACTOR / oldScale
//        knownNodes.forEach { s: Sphere -> s.spatial().scale *= Vector3f(factor) }
        mainInstance.instances.forEach { s -> s.spatial().scale *= Vector3f(factor) }
        logger.debug("Increasing scale to $SCALE_FACTOR, by factor $factor")
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

    fun addLink(from: Spot, to: Spot) {
        from.localize(pos)
        to.localize(pos)

        posT.sub(posF)

        //NB: posF is base of the "vector" link, posT is the "vector" link itself
        val node = Cylinder(linkRadius, posT.length(), 8)
        node.spatial().scale.set(linkSize * 100, 1.0, linkSize * 100)
        node.spatial().rotation = Quaternionf().rotateTo(Vector3f(0f, 1f, 0f), posT).normalize()
        node.spatial().position = Vector3f(posF)

        node.name = from.label + " --> " + to.label
        //node.setMaterial( linksNodesHub.getMaterial() );
        logger.debug("add node : " + node.name)
        linksNodesHub?.addChild(node)
        links?.addLast(LinkNode(node, from.timepoint, to.timepoint))

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
        logger.info("spot.getTimepoint():" + spot.timepoint)
        logger.info("TPtill:$toTP")

        if (spot.timepoint >= toTP) return
        logger.info("forward search!")
        //enumerate all forward links
        val s = spot.modelGraph.vertexRef()
        for (l in spot.incomingEdges()) {
            logger.info("forward search: incoming edges")
            if (l.getSource(s).timepoint > spot.timepoint && s.timepoint <= toTP) {
                addLink(spot, s)
                forwardSearch(s, toTP)
            }
        }
        for (l in spot.outgoingEdges()) {
            logger.info("forward search: outgoing edges")
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
                    linksNodesHub?.removeChild(link.node)
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

class LinkNode (var node: Cylinder, var fromTP: Int, var toTP: Int)