package util

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.numerics.Random
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.lazyLogger
import org.joml.Quaternionf
import org.joml.Vector3f
import org.mastodon.mamut.ProjectModel
import org.mastodon.mamut.model.Link
import org.mastodon.mamut.model.Spot
import org.mastodon.ui.coloring.GraphColorGenerator
import org.scijava.event.EventService
import sc.iview.SciView
import sc.iview.event.NodeChangedEvent
import java.lang.Math.random
import java.util.*
import kotlin.math.floor
import kotlin.math.sqrt


//TODO FAILED to hook up here a 'parentNode' listener that would setVisible(false) on all children
// of the parent node that are "not used"
class SphereLinkNodes
    (val sv: SciView, val parentNode: Node) {
        private val logger by lazyLogger()
        var SCALE_FACTOR = 1f
        var DEFAULT_COLOR = 0x00FFFFFF
        val knownNodes: MutableList<Sphere> = ArrayList(1000)
        val addedExtraNodes: MutableList<Sphere> = LinkedList()
        private var spotRef: Spot? = null
        var events: EventService? = null

    init {
        events = sv.scijavaContext?.getService(EventService::class.java)
    }

    val sphere = Icosphere(1f, 2)

    fun initializeSpots(
        mastodonData: ProjectModel,
        timepoint: Int,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        logger.debug("Initializing Spots")
        sphere.setMaterial(ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag")) {
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
            ambient = Vector3f(1.0f, 1.0f, 1.0f)
            specular = Vector3f(.0f, 1.0f, 1.0f)
            metallic = 0.0f
            roughness = 1.0f
        }
        val sphereInstance = InstancedNode(sphere)

        if (spotRef == null) spotRef = mastodonData.model.graph.vertexRef()
        val focusedSpotRef = mastodonData.focusModel.getFocusedVertex(spotRef)
        val spots = mastodonData.model.spatioTemporalIndex.getSpatialIndex(timepoint)
        sv.blockOnNewNodes = false
        logger.info("got ${spots.size()} spots from mastodon timepoint $timepoint")

        sv.addNode(sphereInstance, parent = parentNode)
        var inst: InstancedNode.Instance
        for (s in spots) {
            inst = sphereInstance.addInstance()
            inst.name = "inst_${s.internalPoolIndex}"
            inst.addAttribute(Material::class.java, sphere.material())
            s.localize(auxSpatialPos)
            inst.spatial {
                position = Vector3f(auxSpatialPos) / parentNode.spatialOrNull()!!.scale - parentNode.spatialOrNull()!!.position
            }
            inst.spatial().scale = Vector3f(
                SCALE_FACTOR * sqrt(s.boundingSphereRadiusSquared).toFloat()
            ) / parentNode.spatialOrNull()!!.scale

        }
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

    private val auxSpatialPos = FloatArray(3)
    private val minusThisOffset = floatArrayOf(0f, 0f, 0f)
    fun setDataCenter(center: Vector3f) {
        minusThisOffset[0] = center.x
        minusThisOffset[1] = center.y
        minusThisOffset[2] = center.z
    }

    private fun setSphereNode(
        node: Sphere,
        s: Spot,
        colorizer: GraphColorGenerator<Spot, Link>
    ) {
        node.name = s.label
        s.localize(auxSpatialPos)
        auxSpatialPos[0] -= minusThisOffset[0]
        auxSpatialPos[1] -= minusThisOffset[1]
        auxSpatialPos[2] -= minusThisOffset[2]
        auxSpatialPos[1] *= -1f
        auxSpatialPos[2] *= -1f
        node.spatial().setPosition(auxSpatialPos)
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
        knownNodes.forEach { s: Sphere -> s.spatial().scale *= Vector3f(factor)  }
        logger.debug("Decreasing scale to $SCALE_FACTOR, by factor $factor")
    }

    fun increaseSphereScale() {
        val oldScale = SCALE_FACTOR
        SCALE_FACTOR += 0.1f
        val factor = SCALE_FACTOR / oldScale
        knownNodes.forEach { s: Sphere -> s.spatial().scale *= Vector3f(factor) }
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