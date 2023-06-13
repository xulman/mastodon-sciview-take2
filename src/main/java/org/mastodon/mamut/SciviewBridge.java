//TODO add license text
package org.mastodon.mamut;

import sc.iview.SciView;

public class SciviewBridge {

	public static void main(String[] args) {
		try {
			SciView sv = SciView.create();
		} catch (Exception e) {
			System.out.println("got exception: "+e.getMessage());
		}
	}
}
