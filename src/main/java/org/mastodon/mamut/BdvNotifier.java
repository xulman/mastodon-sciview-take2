/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2023, Vladimír Ulman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.mastodon.mamut;

import bdv.viewer.TransformListener;
import net.imglib2.realtransform.AffineTransform3D;
import org.mastodon.graph.GraphChangeListener;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.spatial.VertexPositionListener;

public class BdvNotifier {
	/**
	 * Runs the redisplayProcessor when an event occurs on the given
	 * BDV window, an event that is worth redrawing the scene. A shortcut
	 * reference (to the underlying Mastodon data that the BDV window
	 * operates over) must be provided.
	 *
	 * @param redisplayProcessor  handler of the redrawing event
	 * @param mastodonAppModel  the underlying Mastodon data
	 * @param bdvWindow  BDV window that operated on the underlying Mastodon data
	 */
	public BdvNotifier(final Runnable redisplayProcessor,
	                   final MamutAppModel mastodonAppModel,
	                   final MamutViewBdv bdvWindow)
	{
		//create a listener for it (which will _immediately_ collect updates from BDV)
		final BdvEventsWatcher bdvUpdateListener = new BdvEventsWatcher(bdvWindow);

		//create a thread that would be watching over the listener and would take only
		//the most recent data if no updates came from BDV for a little while
		//(this is _delayed_ handling of the data, skipping over any intermediate changes)
		final BdvEventsCatherThread blenderSenderThread
				= new BdvEventsCatherThread(bdvUpdateListener, 100, redisplayProcessor);

		//register the BDV listener and start the thread
		bdvWindow.getViewerPanelMamut().renderTransformListeners().add(bdvUpdateListener);
		mastodonAppModel.getModel().getGraph().addVertexPositionListener(bdvUpdateListener);
		mastodonAppModel.getModel().getGraph().addGraphChangeListener(bdvUpdateListener);
		blenderSenderThread.start();

		bdvWindow.onClose(() -> {
			System.out.println("Cleaning up while BDV window is closing.");
			bdvWindow.getViewerPanelMamut().renderTransformListeners().remove(bdvUpdateListener);
			mastodonAppModel.getModel().getGraph().removeGraphChangeListener(bdvUpdateListener);
			mastodonAppModel.getModel().getGraph().removeVertexPositionListener(bdvUpdateListener);
			blenderSenderThread.stopTheWatching();
		});
	}

	/**
	 * this class only registers timestamp of the most recently
	 * occurred relevant BDV/Mastodon event
	 */
	class BdvEventsWatcher implements
			TransformListener<AffineTransform3D>,
			GraphChangeListener,
			VertexPositionListener
	{
		final MamutViewBdv myBdvIamServicing;
		BdvEventsWatcher(final MamutViewBdv viewBdv) {
			myBdvIamServicing = viewBdv;
		}

		@Override
		public void transformChanged(AffineTransform3D affineTransform3D) { somethingChanged(); }
		@Override
		public void graphChanged() { somethingChanged(); }
		@Override
		public void vertexPositionChanged(Object vertex) { somethingChanged(); }

		void somethingChanged() {
			timeStampOfLastRequest = System.currentTimeMillis();
			isLastRequestDataValid = true;
			//System.out.println("detected new tp and some new transform");
		}

		boolean isLastRequestDataValid = false;
		long timeStampOfLastRequest = 0;
	}

	/**
	 * this class iteratively inspects (via a busy-wait loop cycle in its own
	 * separate thread) the associated BdvEventsWatcher if there is a recent
	 * (and not out-dated) event pending, and if so, it calls the eventHandler
	 */
	class BdvEventsCatherThread extends Thread
	{
		final BdvEventsWatcher eventsSource;
		final long updateInterval;
		final Runnable eventProcessor;
		boolean keepWatching = true;

		final private static String SERVICE_NAME = "Mastodon BDV events watcher";
		BdvEventsCatherThread(final BdvEventsWatcher dataSupplier,
		                      final long updateIntervalInMilis,
		                      final Runnable dataEventHandler) {
			super(SERVICE_NAME);
			eventsSource = dataSupplier;
			updateInterval = updateIntervalInMilis;
			eventProcessor = dataEventHandler;
		}

		void stopTheWatching() {
			keepWatching = false;
		}

		@Override
		public void run() {
			System.out.println(SERVICE_NAME+" started");
			try {
				while (keepWatching)
				{
					if (eventsSource.isLastRequestDataValid
							&& (System.currentTimeMillis() - eventsSource.timeStampOfLastRequest > updateInterval))
					{
						System.out.println(SERVICE_NAME+": silence detected -> sending the current data");
						eventsSource.isLastRequestDataValid = false;
						eventProcessor.run();
					} else sleep(updateInterval/2);
				}
			}
			catch (InterruptedException e)
			{ /* do nothing, silently stop */ }
			System.out.println(SERVICE_NAME+" stopped");
		}
	}
}