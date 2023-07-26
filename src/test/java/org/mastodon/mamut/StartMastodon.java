package org.mastodon.mamut;

import net.imagej.ImageJ;

public class StartMastodon {
	public static void main(String[] args) {
		try {
			ImageJ ij = new ImageJ();
			ij.ui().showUI();
			//NB: otherwise GUI harvesting of plugins will not happen

			StartSciviewBridgeDirectly.giveMeSomeMastodon(ij.context());
		} catch (Exception e) {
			System.out.println("Got this exception: "+e.getMessage());
		}
	}
}
