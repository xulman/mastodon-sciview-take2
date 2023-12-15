/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2021, Vladimír Ulman
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
package plugins

import org.mastodon.app.ui.ViewMenuBuilder
import org.mastodon.mamut.ProjectModel
import org.mastodon.mamut.plugin.MamutPlugin
import org.mastodon.mamut.KeyConfigScopes
import org.mastodon.ui.keymap.KeyConfigContexts
import org.scijava.AbstractContextual
import org.scijava.command.CommandService
import org.scijava.plugin.Plugin
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider
import org.scijava.ui.behaviour.io.gui.CommandDescriptions
import org.scijava.ui.behaviour.util.AbstractNamedAction
import org.scijava.ui.behaviour.util.Actions
import org.scijava.ui.behaviour.util.RunnableAction
import plugins.scijava.MastodonSidePlugin

@Plugin(type = MamutPlugin::class)
class MastodonPlugin : AbstractContextual(), MamutPlugin {
    override fun getMenuTexts(): Map<String, String> {
        return Companion.menuTexts
    }

    override fun getMenuItems(): List<ViewMenuBuilder.MenuItem> {
        return listOf<ViewMenuBuilder.MenuItem>(ViewMenuBuilder.menu("Window", ViewMenuBuilder.item(OPEN_SCIVIEW)))
    }

    /** Command descriptions for all provided commands  */
    @Plugin(type = Descriptions::class)
    class Descriptions : CommandDescriptionProvider(KeyConfigScopes.MAMUT,
                KeyConfigContexts.TRACKSCHEME, KeyConfigContexts.BIGDATAVIEWER) {
        override fun getCommandDescriptions(descriptions: CommandDescriptions) {
            descriptions.add(OPEN_SCIVIEW, OPEN_SCIVIEW_KEYS, "TBA")
        }
    }

    //------------------------------------------------------------------------
    private val actionOpenSciview: AbstractNamedAction
    private var pluginAppModel: ProjectModel? = null

    init {
        actionOpenSciview = RunnableAction(OPEN_SCIVIEW) { openSciview() }
        updateEnabledActions()
    }

    override fun setAppPluginModel(model: ProjectModel) {
        pluginAppModel = model
        updateEnabledActions()
    }

    override fun installGlobalActions(actions: Actions) {
        actions.namedAction(actionOpenSciview, *OPEN_SCIVIEW_KEYS)
    }

    /** enables/disables menu items based on the availability of some project  */
    private fun updateEnabledActions() {
        val appModel = if (pluginAppModel == null) null else pluginAppModel!!
        actionOpenSciview.setEnabled(appModel != null)
    }

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    private fun openSciview() {
        context.getService(CommandService::class.java).run(
            MastodonSidePlugin::class.java, true,
            "mastodon", pluginAppModel!!.windowManager
        )
    }

    companion object {
        private const val OPEN_SCIVIEW = "open in sciview"
        private val OPEN_SCIVIEW_KEYS = arrayOf("not mapped")

        //------------------------------------------------------------------------
        private val menuTexts: MutableMap<String, String> = HashMap()

        init {
            menuTexts[OPEN_SCIVIEW] = "New sciview"
        }
    }
}
