package util

import graphics.scenery.Node
import graphics.scenery.Sphere
import org.joml.Vector3f
import org.mastodon.mamut.ProjectModel
import org.mastodon.mamut.model.Link
import org.mastodon.mamut.model.Spot
import org.mastodon.ui.coloring.GraphColorGenerator
import sc.iview.SciView
import java.util.*
import kotlin.math.sqrt

class SphereNodes //FAILED to hook up here a 'parentNode' listener that would setVisible(false) on all children
//of the parent node that are "not used"
    (val sv: SciView, val parentNode: Node) {
    var SCALE_FACTOR = 1f
    var DEFAULT_COLOR = 0x00FFFFFF
    val knownNodes: MutableList<Sphere> = ArrayList(1000)
    val addedExtraNodes: MutableList<Sphere> = LinkedList()
    private var spotRef: Spot? = null
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
                //NB: make sure it's visible (could have got hidden
                // when there were fewer spots before)
            } else {
                //adding some new nodes
                node = Sphere()
                node.visible = finalVisibilityState
                sv.addNode(node, false, parentNode)
//                parentNode.addChild(node)
                addedExtraNodes.add(node)
            }
            setSphereNode(node, s, colorizer)
            if (focusedSpotRef != null && focusedSpotRef.internalPoolIndex == s.internalPoolIndex) {
                node.material().wireframe = true
            }
            ++visibleNodeCount
        }
        sv.publishNode(parentNode)
        if (addedExtraNodes.size > 0) {
            //NB: also means that the knownNodes were fully exhausted
            knownNodes.addAll(addedExtraNodes)
            sv.publishNode(addedExtraNodes[0]) //NB: publishes only once
            //System.out.println("Added new "+addedExtraNodes.size()+" spheres");
        } else {
            //System.out.println("Hide "+(knownNodes.size()-visibleNodesAfterall)+" spheres");
            //NB: mark not-touched knownNodes as hidden
            var i = visibleNodeCount
            while (i < knownNodes.size) {
                knownNodes[i].name = NAME_OF_NOT_USED_SPHERES
                knownNodes[i++].visible = false
            }
        }
        /*
		System.out.println("Drawing currently in total "+visibleNodesAfterall
				+ " and there are "+(knownNodes.size()-visibleNodesAfterall)
				+ " hidden..."); */
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
        node.material().wireframe = false
    }

    fun decreaseSphereScale() {
        val oldScale = SCALE_FACTOR
        SCALE_FACTOR -= 0.5f
        if (SCALE_FACTOR < 0.4f) SCALE_FACTOR = 0.5f
        val factor = SCALE_FACTOR / oldScale
        knownNodes.forEach { s: Sphere -> s.spatial().scale.mul(factor) }
        println("Decreasing scale to $SCALE_FACTOR, by factor $factor")
    }

    fun increaseSphereScale() {
        val oldScale = SCALE_FACTOR
        SCALE_FACTOR += 0.5f
        val factor = SCALE_FACTOR / oldScale
        knownNodes.forEach { s: Sphere -> s.spatial().scale.mul(factor) }
        println("Increasing scale to $SCALE_FACTOR, by factor $factor")
    }

    companion object {
        const val NAME_OF_NOT_USED_SPHERES = "not used now"
    }
}