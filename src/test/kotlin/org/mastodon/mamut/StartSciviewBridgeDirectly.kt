package org.mastodon.mamut

import mpicbg.spim.data.SpimDataException
import org.mastodon.mamut.project.MamutProjectIO
import org.scijava.Context
import sc.iview.SciView
import sc.iview.SciView.Companion.create
import java.io.IOException
import javax.swing.WindowConstants

object StartSciviewBridgeDirectly {
    fun giveMeMastodon(scijavaCtx: Context?): WindowManager {
        //the central hub, a container to hold all
        val windowManager = WindowManager(scijavaCtx)

        //a GUI element wrapping around the hub
        val win = MainWindow(windowManager)

        //this makes the true Mastodon window visible
        //note: you can open project that restores/reopen e.g. TrackScheme window,
        //      yet the main Mastodon window is not shown... but this then runs non-stop
        win.isVisible = true

        //this makes the whole thing (incl. the central hub) go down when the GUI is closed
        win.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
        return windowManager
    }

    @Throws(IOException::class, SpimDataException::class)
    fun giveMeMastodonOfThisProject(scijavaCtx: Context?, projectPath: String?): WindowManager {
        //ImageJ ij = new ImageJ();
        //ij.ui().showUI();
        val m = giveMeMastodon(scijavaCtx)
        m.projectManager.open(MamutProjectIO().load(projectPath))
        return m
    }

    @Throws(Exception::class)
    fun createSciview(): SciView {
        return create()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            // --------------->>  <<---------------
            //point this to your testing project, or grab example project with:
            //git clone https://github.com/mastodon-sc/mastodon-example-data.git
            //String projectPath = "/home/ulman/Mette/e1/E1_reduced.mastodon";
            val projectPath = "/home/ulman/devel/sciview_hack2/mastodon-example-data/tgmm-mini/tgmm-mini.mastodon"
            // --------------->>  <<---------------
            val sv = createSciview()
            val mastodon = giveMeMastodonOfThisProject(sv.scijavaContext, projectPath)
            val bridge = SciviewBridge(mastodon, 0, 2, sv)
            //bridge.openSyncedBDV();
            mastodon.appModel.projectClosedListeners().add(CloseListener {
                println("Mastodon project was closed, cleaning up in sciview:")
                bridge.close() //calls also bridge.detachControllingUI();
            })
        } catch (e: Exception) {
            println("Got this exception: " + e.message)
        }
    }
}
