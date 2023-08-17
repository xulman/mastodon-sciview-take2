package org.mastodon.mamut.util;

import net.imagej.ImageJ;
import sc.iview.SciView;

public class StartFiji {
	public static void main(String[] args) {
		try {
			SciView.xinitThreads();
			ImageJ ij = new ImageJ();
			ij.ui().showUI();
			//NB: otherwise GUI harvesting of plugins will not happen
		} catch (Exception e) {
			System.out.println("Got this exception: " + e.getMessage());
		}
	}
}