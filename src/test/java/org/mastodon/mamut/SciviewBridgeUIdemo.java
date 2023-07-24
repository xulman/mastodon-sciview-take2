package org.mastodon.mamut;

import org.mastodon.mamut.SciviewBridge;

public class SciviewBridgeUIdemo {
	public static void main(String[] args) {
		SciviewBridge bridge = new SciviewBridge();
		bridge.createAndShowControllingUI("demo controlling window");
	}
}