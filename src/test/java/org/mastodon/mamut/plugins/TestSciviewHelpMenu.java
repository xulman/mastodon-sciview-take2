package org.mastodon.mamut.plugins;

import net.imagej.ImageJ;
import org.scijava.log.LogService;

public class TestSciviewHelpMenu {
	public static void main(String[] args) {
		ImageJ ij = new ImageJ();
		ij.ui().showUI();

		SciviewPlugin menu = new SciviewPlugin();
		menu.logService = ij.context().getService(LogService.class);
		menu.run();
	}
}
