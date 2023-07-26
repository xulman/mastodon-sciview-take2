package org.mastodon.mamut.util;

import bdv.viewer.TimePointListener;
import org.mastodon.app.ui.GroupLocksPanel;
import org.mastodon.grouping.GroupHandle;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.SciviewBridge;
import org.mastodon.mamut.WindowManager;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.Spot;
import org.mastodon.model.NavigationListener;
import org.mastodon.model.TimepointListener;
import org.mastodon.pool.PoolCollectionWrapper;

import java.util.Optional;

public class GroupLocksHandling {
	private final SciviewBridge bridge;    //controls sciview via this bridge obj
	private final MamutAppModel appModel;  //controls Mastodon
	private final PoolCollectionWrapper<Spot> vertices; //shortcut to inside of Mastodon
	private final NavigationRequestsHandler navigationRequestsHandler = new NavigationRequestsHandler();

	public GroupLocksHandling(final SciviewBridge bridge, final WindowManager mastodon) {
		this.bridge = bridge;
		this.appModel = mastodon.getAppModel();
		this.vertices = appModel.getModel().getGraph().vertices();
		this.isActive = false; //'cause it's not having any group handle yet
	}


	private GroupHandle myGroupHandle;
	private boolean isActive;

	public GroupLocksPanel createAndActivate() {
		if (isActive) return null;
		isActive = true;

		myGroupHandle = appModel.getGroupManager().createGroupHandle();
		myGroupHandle.getModel(appModel.NAVIGATION).listeners().add(navigationRequestsHandler);
		myGroupHandle.getModel(appModel.TIMEPOINT).listeners().add(navigationRequestsHandler);
		return new GroupLocksPanel(myGroupHandle);
	}

	public void deactivate() {
		if (!isActive) return;
		isActive = false;

		myGroupHandle.getModel(appModel.NAVIGATION).listeners().remove(navigationRequestsHandler);
		myGroupHandle.getModel(appModel.TIMEPOINT).listeners().remove(navigationRequestsHandler);
		appModel.getGroupManager().removeGroupHandle(myGroupHandle);
	}


	class NavigationRequestsHandler implements NavigationListener<Spot,Link>, TimepointListener {
		@Override
		public void navigateToVertex(Spot vertex) {
			if (isActive) focusSciviewToNode(vertex.getLabel());
		}
		@Override
		public void navigateToEdge(Link edge) {
			if (isActive) focusSciviewToNode(edge.getSource().getLabel());
		}

		@Override
		public void timepointChanged() {
			bridge.showTimepoint( myGroupHandle.getModel(appModel.TIMEPOINT).getTimepoint() );
		}
	}

	public void focusMastodonToSpot(final String name) {
		Optional<Spot> res = vertices.parallelStream().filter(s -> s.getLabel().equals(name)).findFirst();
		res.ifPresent(spot -> myGroupHandle.getModel(appModel.NAVIGATION).notifyNavigateToVertex(spot));
	}

	public void focusSciviewToNode(final String name) {
		bridge.focusSpot(name);
	}
}
