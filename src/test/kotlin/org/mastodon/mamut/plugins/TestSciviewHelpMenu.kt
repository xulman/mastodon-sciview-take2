package org.mastodon.mamut.plugins

import plugins.SciviewPlugin

object TestSciviewHelpMenu {
    @JvmStatic
    fun main(args: Array<String>) {
        val menu = SciviewPlugin()
        menu.run()
    }
}
