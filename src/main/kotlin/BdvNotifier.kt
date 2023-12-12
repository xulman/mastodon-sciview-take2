package org.mastodon.mamut

import bdv.viewer.TimePointListener
import bdv.viewer.TransformListener
import net.imglib2.realtransform.AffineTransform3D
import org.mastodon.graph.GraphChangeListener
import org.mastodon.mamut.model.Spot
import org.mastodon.mamut.views.bdv.MamutViewBdv
import org.mastodon.model.FocusListener
import org.mastodon.spatial.VertexPositionListener
import org.mastodon.ui.coloring.ColoringModel.ColoringChangedListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

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
class BdvNotifier(
    updateContentProcessor: Runnable,
    updateViewProcessor: Runnable,
    mastodonAppModel: ProjectModel,
    bdvWindow: MamutViewBdv
) {

    init {
        //create a listener for it (which will _immediately_ collect updates from BDV)
        val bdvUpdateListener: BdvEventsWatcher = BdvEventsWatcher(bdvWindow)

        //create a thread that would be watching over the listener and would take only
        //the most recent data if no updates came from BDV for a little while
        //(this is _delayed_ handling of the data, skipping over any intermediate changes)
        val cumulatingEventsHandlerThread: BdvEventsCatherThread = BdvEventsCatherThread(
            bdvUpdateListener, 10,
            updateContentProcessor, updateViewProcessor
        )

        //register the BDV listener and start the thread
        bdvWindow.viewerPanelMamut.renderTransformListeners().add(bdvUpdateListener)
        bdvWindow.viewerPanelMamut.timePointListeners().add(bdvUpdateListener)
        bdvWindow.viewerPanelMamut.addPropertyChangeListener(bdvUpdateListener)
        bdvWindow.coloringModel.listeners().add(bdvUpdateListener)
        mastodonAppModel.focusModel.listeners().add(bdvUpdateListener)
        mastodonAppModel.model.graph.addVertexPositionListener(bdvUpdateListener)
        mastodonAppModel.model.graph.addGraphChangeListener(bdvUpdateListener)
        cumulatingEventsHandlerThread.start()
        bdvWindow.onClose {
            println("Cleaning up while BDV window is closing.")
            bdvWindow.viewerPanelMamut.renderTransformListeners().remove(bdvUpdateListener)
            bdvWindow.viewerPanelMamut.timePointListeners().remove(bdvUpdateListener)
            bdvWindow.viewerPanelMamut.removePropertyChangeListener(bdvUpdateListener)
            bdvWindow.coloringModel.listeners().remove(bdvUpdateListener)
            mastodonAppModel.focusModel.listeners().remove(bdvUpdateListener)
            mastodonAppModel.model.graph.removeGraphChangeListener(bdvUpdateListener)
            mastodonAppModel.model.graph.removeVertexPositionListener(bdvUpdateListener)
            cumulatingEventsHandlerThread.stopTheWatching()
        }
    }

    /**
     * this class only registers timestamp of the most recently
     * occurred relevant BDV/Mastodon event, it recognized two types
     * of events: events requiring scene camera repositioning, and events
     * requiring scene content rebuild
     */
    internal inner class BdvEventsWatcher(val myBdvIamServicing: MamutViewBdv) : TransformListener<AffineTransform3D?>,
        TimePointListener, GraphChangeListener, VertexPositionListener<Spot>, PropertyChangeListener, FocusListener,
        ColoringChangedListener {
        override fun graphChanged() = contentChanged()
        override fun vertexPositionChanged(vertex: Spot) = contentChanged()
        override fun transformChanged(affineTransform3D: AffineTransform3D?) = viewChanged()

        override fun timePointChanged(timePointIndex: Int) {
            contentChanged()
        }

        override fun focusChanged() {
            contentChanged()
        }

        override fun propertyChange(propertyChangeEvent: PropertyChangeEvent) {
            contentChanged()
        }

        override fun coloringChanged() {
            contentChanged()
        }

        fun contentChanged() {
            timeStampOfLastEvent = System.currentTimeMillis()
            isLastContentEventValid = true
        }

        fun viewChanged() {
            timeStampOfLastEvent = System.currentTimeMillis()
            isLastViewEventValid = true
        }

        var isLastContentEventValid = false
        var isLastViewEventValid = false
        var timeStampOfLastEvent: Long = 0
    }

    companion object {
        private val SERVICE_NAME = "Mastodon BDV events watcher"
    }



    /**
     * this class iteratively inspects (via a busy-wait loop cycle in its own
     * separate thread) the associated BdvEventsWatcher if there is a recent
     * (and not out-dated) event(s) pending, and if so, it calls the respective
     * eventHandler(s)
     */
    internal inner class BdvEventsCatherThread(
        val eventsSource: BdvEventsWatcher,
        val updateInterval: Long,
        val contentEventProcessor: Runnable,
        val viewEventProcessor: Runnable
    ) : Thread(SERVICE_NAME) {
        var keepWatching = true
        fun stopTheWatching() {
            keepWatching = false
        }

        override fun run() {
            println("$SERVICE_NAME started")
            try {
                while (keepWatching) {
                    if (eventsSource.isLastContentEventValid || eventsSource.isLastViewEventValid && System.currentTimeMillis() - eventsSource.timeStampOfLastEvent > updateInterval) {
                        if (eventsSource.isLastContentEventValid) {
                            //System.out.println(SERVICE_NAME+": content event and silence detected -> processing it now");
                            eventsSource.isLastContentEventValid = false
                            contentEventProcessor.run()
                        }
                        if (eventsSource.isLastViewEventValid) {
                            //System.out.println(SERVICE_NAME+": view event and silence detected -> processing it now");
                            eventsSource.isLastViewEventValid = false
                            viewEventProcessor.run()
                        }
                    } else sleep(updateInterval / 2)
                }
            } catch (e: InterruptedException) { /* do nothing, silently stop */
            }
            println(SERVICE_NAME + " stopped")
        }
    }
}