package plugins.util;

import graphics.scenery.Node;
import graphics.scenery.Sphere;
import org.joml.Vector3f;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.ui.coloring.GraphColorGenerator;
import sc.iview.SciView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SphereNodes {

	float SCALE_FACTOR = 1.f;
	int DEFAULT_COLOR = 0x00FFFFFF;
	public static final String NAME_OF_NOT_USED_SPHERES = "not used now";

	final SciView sv;
	final Node parentNode;

	public SphereNodes(final SciView operateForThisSciview, final Node stackSpheresUnderThisParentNode) {
		this.sv = operateForThisSciview;
		this.parentNode = stackSpheresUnderThisParentNode;

		//FAILED to hook up here a 'parentNode' listener that would setVisible(false) on all children
		//of the parent node that are "not used"
	}

	final List<Sphere> knownNodes = new ArrayList<>(1000);
	final List<Sphere> addedExtraNodes = new LinkedList<>();

	private Spot spotRef = null;

	public int showTheseSpots(final MamutAppModel mastodonData,
	                          final int timepoint,
	                          final GraphColorGenerator<Spot, Link> colorizer) {
		int visibleNodesAfterall = 0;
		addedExtraNodes.clear();

		//nodes should honor if they are wished to be immediately visible or not
		final boolean finalVisiblityState = parentNode.getVisible();

		if (spotRef == null) spotRef = mastodonData.getModel().getGraph().vertexRef();
		Spot focusedSpotRef = mastodonData.getFocusModel().getFocusedVertex(spotRef);

		final SpatialIndex<Spot> spots
				= mastodonData.getModel().getSpatioTemporalIndex().getSpatialIndex(timepoint);
		Sphere node;
		for (Spot s : spots) {
			if (visibleNodesAfterall < knownNodes.size()) {
				//injecting into known nodes (already registered with sciview)
				node = knownNodes.get(visibleNodesAfterall);
				node.setVisible(finalVisiblityState);
				//NB: make sure it's visible (could have got hidden
				// when there were fewer spots before)
			} else {
				//adding some new nodes
				//todo: fix me after #497 is solved
				node = new Sphere();
				parentNode.addChild(node);
				addedExtraNodes.add(node);
				node.setVisible(finalVisiblityState);
			}
			setSphereNode(node,s,colorizer);
			if (focusedSpotRef != null && focusedSpotRef.getInternalPoolIndex() == s.getInternalPoolIndex()) {
				node.material().setWireframe(true);
			}
			++visibleNodesAfterall;
		}

		if (addedExtraNodes.size() > 0) {
			//NB: also means that the knownNodes were fully exhausted
			knownNodes.addAll(addedExtraNodes);
			sv.publishNode( addedExtraNodes.get(0) ); //NB: publishes only once
			//System.out.println("Added new "+addedExtraNodes.size()+" spheres");
		} else {
			//System.out.println("Hide "+(knownNodes.size()-visibleNodesAfterall)+" spheres");
			//NB: mark not-touched knownNodes as hidden
			int i = visibleNodesAfterall;
			while (i < knownNodes.size()) {
				knownNodes.get(i).setName(NAME_OF_NOT_USED_SPHERES);
				knownNodes.get(i++).setVisible(false);
			}
		}
		/*
		System.out.println("Drawing currently in total "+visibleNodesAfterall
				+ " and there are "+(knownNodes.size()-visibleNodesAfterall)
				+ " hidden...");
		*/
		return visibleNodesAfterall;
	}

	private final float[] auxSpatialPos = new float[3];
	private final float[] minusThisOffset = {0,0,0};

	public void setDataCentre(final Vector3f centre) {
		minusThisOffset[0] = centre.x;
		minusThisOffset[1] = centre.y;
		minusThisOffset[2] = centre.z;
	}

	private void setSphereNode(final Sphere node,
	                           final Spot s,
	                           final GraphColorGenerator<Spot, Link> colorizer) {
		node.setName(s.getLabel());

		s.localize(auxSpatialPos);
		auxSpatialPos[0] -= minusThisOffset[0];
		auxSpatialPos[1] -= minusThisOffset[1];
		auxSpatialPos[2] -= minusThisOffset[2];
		auxSpatialPos[1] *= -1;
		auxSpatialPos[2] *= -1;
		node.spatial().setPosition(auxSpatialPos);

		node.spatial().setScale( new Vector3f(
				SCALE_FACTOR * (float)Math.sqrt(s.getBoundingSphereRadiusSquared()) ) );

		int intColor = colorizer.color(s);
		if (intColor == 0x00000000) intColor = DEFAULT_COLOR;
		float r = ((intColor >> 16) & 0x000000FF) / 255.f;
		float g = ((intColor >> 8) & 0x000000FF) / 255.f;
		float b =  (intColor & 0x000000FF) / 255.f;
		node.material().getDiffuse().set(r,g,b);
		node.material().setWireframe(false);
	}

	public void decreaseSphereScale() {
		float oldScale = SCALE_FACTOR;
		SCALE_FACTOR -= 0.5f;
		if (SCALE_FACTOR < 0.4f) SCALE_FACTOR = 0.5f;
		final float factor = SCALE_FACTOR / oldScale;
		knownNodes.forEach(s -> s.spatial().setScale( s.spatial().getScale().mul(factor)) );
		System.out.println("Decreasing scale to "+SCALE_FACTOR+", by factor "+factor);
	}
	public void increaseSphereScale() {
		float oldScale = SCALE_FACTOR;
		SCALE_FACTOR += 0.5f;
		final float factor = SCALE_FACTOR / oldScale;
		knownNodes.forEach(s -> s.spatial().setScale( s.spatial().getScale().mul(factor)) );
		System.out.println("Increasing scale to "+SCALE_FACTOR+", by factor "+factor);
	}
}