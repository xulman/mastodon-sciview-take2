package org.mastodon.mamut

object SciviewBridgeUIdemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val bridge = SciviewBridge()
        bridge.createAndShowControllingUI("demo controlling window")
    }
}