package org.mastodon.mamut.util;

import graphics.scenery.Node;
import org.joml.Vector3f;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.mamut.model.Spot;

import sc.iview.SciView;
import graphics.scenery.Sphere;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

public class SphereNodes {

	float SCALE_FACTOR = 1.f;

	final SciView sv;
	final Node parentNode;

	public SphereNodes(final SciView operateForThisSciview, final Node stackSpheresUnderThisParentNode) {
		this.sv = operateForThisSciview;
		this.parentNode = stackSpheresUnderThisParentNode;
	}

	final List<Sphere> knownNodes = new ArrayList<>(1000);
	final List<Sphere> addedExtraNodes = new LinkedList<>();

	public int showTheseSpots(final MamutAppModel mastodonData, final int timepoint) {
		int visibleNodesAfterall = 0;
		addedExtraNodes.clear();

		final SpatialIndex<Spot> spots
				= mastodonData.getModel().getSpatioTemporalIndex().getSpatialIndex(timepoint);
		Sphere node;
		for (Spot s : spots) {
			if (visibleNodesAfterall < knownNodes.size()) {
				//injecting into known nodes (already registered with sciview)
				node = knownNodes.get(visibleNodesAfterall);
				node.setVisible(true);
				//NB: make sure it's visible (could have got hidden
				// when there were fewer spots before)
			} else {
				//adding some new nodes
				//todo: fix me after #497 is solved
				node = new Sphere();
				parentNode.addChild(node);
				addedExtraNodes.add(node);
			}
			setSphereNode(node,s);
			++visibleNodesAfterall;
		}

		if (addedExtraNodes.size() > 0) {
			//NB: also means that the knownNodes were fully exhausted
			knownNodes.addAll(addedExtraNodes);
			addedExtraNodes.forEach(sv::publishNode);
			System.out.println("Added new "+addedExtraNodes.size()+" spheres");
		} else {
			System.out.println("Hide "+(knownNodes.size()-visibleNodesAfterall)+" spheres");
			//NB: mark not-touched knownNodes as hidden
			int i = visibleNodesAfterall;
			while (i < knownNodes.size()) {
				knownNodes.get(i).setName("not used now");
				knownNodes.get(i++).setVisible(false);
			}
		}
		System.out.println("Drawing currently in total "+visibleNodesAfterall
				+ " and there are "+(knownNodes.size()-visibleNodesAfterall)
				+ " hidden...");
		return visibleNodesAfterall;
	}

	private float[] spatialPos = new float[3];

	private void setSphereNode(final Sphere node, final Spot s) {
		node.setName(s.getLabel());

		s.localize(spatialPos);
		node.spatial().setPosition(spatialPos);

		node.spatial().setScale( new Vector3f(
				SCALE_FACTOR * (float)Math.sqrt(s.getBoundingSphereRadiusSquared()) ) );

		node.material().setDiffuse( new Vector3f(1.0f, 0.f, 0.f) );
	}

	public void decreaseSphereScale() {
		float oldScale = SCALE_FACTOR;
		SCALE_FACTOR -= 0.5f;
		if (SCALE_FACTOR < 0.4f) SCALE_FACTOR = 0.5f;
		final float factor = SCALE_FACTOR / oldScale;
		knownNodes.forEach(s -> s.spatial().setScale( s.spatial().getScale().mul(factor)) );
		System.out.println("decreasing scale to "+SCALE_FACTOR+", by factor "+factor);
	}
	public void increaseSphereScale() {
		float oldScale = SCALE_FACTOR;
		SCALE_FACTOR += 0.5f;
		final float factor = SCALE_FACTOR / oldScale;
		knownNodes.forEach(s -> s.spatial().setScale( s.spatial().getScale().mul(factor)) );
		System.out.println("increasing scale to "+SCALE_FACTOR+", by factor "+factor);
	}
}