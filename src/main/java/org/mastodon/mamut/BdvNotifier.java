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

import bdv.viewer.TimePointListener;
import bdv.viewer.TransformListener;
import net.imglib2.realtransform.AffineTransform3D;
import org.mastodon.graph.GraphChangeListener;
import org.mastodon.model.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.mastodon.spatial.VertexPositionListener;
import org.mastodon.ui.coloring.ColoringModel;

public class BdvNotifier {
	/**
	 * Runs the updateContentProcessor() when an event occurs on the given
	 * BDV window, an event that is worth rebuilding the scene content. Similarly,
	 * runs the updateViewProcessor() when an event occurs on the given BDV
	 * window, an event that is worth moving the scene's camera. A shortcut
	 * reference (to the underlying Mastodon data that the BDV window
	 * operates over) must be provided.
	 *
	 * @param updateContentProcessor  handler of the scene rebuilding event
	 * @param updateViewProcessor  handler of the scene viewing-angle event
	 * @param mastodonAppModel  the underlying Mastodon data
	 * @param bdvWindow  BDV window that operated on the underlying Mastodon data
	 */
	public BdvNotifier(final Runnable updateContentProcessor,
	                   final Runnable updateViewProcessor,
	                   final MamutAppModel mastodonAppModel,
	                   final MamutViewBdv bdvWindow)
	{
		//create a listener for it (which will _immediately_ collect updates from BDV)
		final BdvEventsWatcher bdvUpdateListener = new BdvEventsWatcher(bdvWindow);

		//create a thread that would be watching over the listener and would take only
		//the most recent data if no updates came from BDV for a little while
		//(this is _delayed_ handling of the data, skipping over any intermediate changes)
		final BdvEventsCatherThread cumulatingEventsHandlerThread
				= new BdvEventsCatherThread(bdvUpdateListener, 10,
						updateContentProcessor, updateViewProcessor);

		//register the BDV listener and start the thread
		bdvWindow.getViewerPanelMamut().renderTransformListeners().add(bdvUpdateListener);
		bdvWindow.getViewerPanelMamut().timePointListeners().add(bdvUpdateListener);
		bdvWindow.getViewerPanelMamut().addPropertyChangeListener(bdvUpdateListener);
		bdvWindow.getColoringModel().listeners().add(bdvUpdateListener);
		mastodonAppModel.getFocusModel().listeners().add(bdvUpdateListener);
		mastodonAppModel.getModel().getGraph().addVertexPositionListener(bdvUpdateListener);
		mastodonAppModel.getModel().getGraph().addGraphChangeListener(bdvUpdateListener);
		cumulatingEventsHandlerThread.start();

		bdvWindow.onClose(() -> {
			System.out.println("Cleaning up while BDV window is closing.");
			bdvWindow.getViewerPanelMamut().renderTransformListeners().remove(bdvUpdateListener);
			bdvWindow.getViewerPanelMamut().timePointListeners().remove(bdvUpdateListener);
			bdvWindow.getViewerPanelMamut().removePropertyChangeListener(bdvUpdateListener);
			bdvWindow.getColoringModel().listeners().remove(bdvUpdateListener);
			mastodonAppModel.getFocusModel().listeners().remove(bdvUpdateListener);
			mastodonAppModel.getModel().getGraph().removeGraphChangeListener(bdvUpdateListener);
			mastodonAppModel.getModel().getGraph().removeVertexPositionListener(bdvUpdateListener);
			cumulatingEventsHandlerThread.stopTheWatching();
		});
	}

	/**
	 * this class only registers timestamp of the most recently
	 * occurred relevant BDV/Mastodon event, it recognized two types
	 * of events: events requiring scene camera repositioning, and events
	 * requiring scene content rebuild
	 */
	class BdvEventsWatcher implements
			TransformListener<AffineTransform3D>,
			TimePointListener,
			GraphChangeListener,
			VertexPositionListener,
			PropertyChangeListener,
			FocusListener,
			ColoringModel.ColoringChangedListener
	{
		final MamutViewBdv myBdvIamServicing;
		BdvEventsWatcher(final MamutViewBdv viewBdv) {
			myBdvIamServicing = viewBdv;
		}

		@Override
		public void graphChanged() { contentChanged(); }
		@Override
		public void vertexPositionChanged(Object vertex) { contentChanged(); }
		@Override
		public void transformChanged(AffineTransform3D affineTransform3D) { viewChanged(); }
		@Override
		public void timePointChanged(int timePointIndex) { contentChanged(); }
		@Override
		public void focusChanged() { contentChanged(); }
		@Override
		public void propertyChange(PropertyChangeEvent propertyChangeEvent) { contentChanged(); }
		@Override
		public void coloringChanged() { contentChanged(); }

		void contentChanged() {
			timeStampOfLastEvent = System.currentTimeMillis();
			isLastContentEventValid = true;
		}
		void viewChanged() {
			timeStampOfLastEvent = System.currentTimeMillis();
			isLastViewEventValid = true;
		}

		boolean isLastContentEventValid = false;
		boolean isLastViewEventValid = false;
		long timeStampOfLastEvent = 0;
	}

	/**
	 * this class iteratively inspects (via a busy-wait loop cycle in its own
	 * separate thread) the associated BdvEventsWatcher if there is a recent
	 * (and not out-dated) event(s) pending, and if so, it calls the respective
	 * eventHandler(s)
	 */
	class BdvEventsCatherThread extends Thread
	{
		final BdvEventsWatcher eventsSource;
		final Runnable contentEventProcessor;
		final Runnable viewEventProcessor;

		final long updateInterval;
		boolean keepWatching = true;

		final private static String SERVICE_NAME = "Mastodon BDV events watcher";
		BdvEventsCatherThread(final BdvEventsWatcher dataSupplier,
		                      final long updateIntervalInMilis,
		                      final Runnable contentEventProcessor,
		                      final Runnable viewEventProcessor) {
			super(SERVICE_NAME);
			eventsSource = dataSupplier;
			updateInterval = updateIntervalInMilis;
			this.contentEventProcessor = contentEventProcessor;
			this.viewEventProcessor = viewEventProcessor;
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
					if (eventsSource.isLastContentEventValid || eventsSource.isLastViewEventValid
							&& (System.currentTimeMillis() - eventsSource.timeStampOfLastEvent > updateInterval))
					{
						if (eventsSource.isLastContentEventValid) {
							//System.out.println(SERVICE_NAME+": content event and silence detected -> processing it now");
							eventsSource.isLastContentEventValid = false;
							contentEventProcessor.run();
						}
						if (eventsSource.isLastViewEventValid) {
							//System.out.println(SERVICE_NAME+": view event and silence detected -> processing it now");
							eventsSource.isLastViewEventValid = false;
							viewEventProcessor.run();
						}
					} else sleep(updateInterval/2);
				}
			}
			catch (InterruptedException e)
			{ /* do nothing, silently stop */ }
			System.out.println(SERVICE_NAME+" stopped");
		}
	}
}