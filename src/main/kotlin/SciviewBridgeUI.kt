package org.mastodon.mamut

import util.AdjustableBoundsRangeSlider
import util.GroupLocksHandling
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemListener
import java.util.function.Consumer
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class SciviewBridgeUI(controlledBridge: SciviewBridge, populateThisContainer: Container) {
    var controlledBridge: SciviewBridge?
    val controlsWindowPanel: Container

    // -------------------------------------------------------------------------------------------
    fun populatePane() {
        controlsWindowPanel.setLayout(GridBagLayout())
        val c = GridBagConstraints()
        c.anchor = GridBagConstraints.LINE_START
        c.fill = GridBagConstraints.HORIZONTAL
        val MastodonRowPlaceHolder = JPanel()
        MastodonRowPlaceHolder.setLayout(GridBagLayout())
        val mc = GridBagConstraints()
        mc.anchor = GridBagConstraints.LINE_START
        mc.fill = GridBagConstraints.HORIZONTAL
        mc.insets = Insets(0, 0, 0, 0)
        mc.weightx = 0.2
        mc.gridx = 0
        lockGroupHandler = GroupLocksHandling(controlledBridge!!, controlledBridge!!.mastodon!!)
        MastodonRowPlaceHolder.add(lockGroupHandler.createAndActivate()!!, mc)
        mc.weightx = 0.6
        mc.gridx = 1
        val openBdvBtn = JButton("Open synced Mastodon BDV")
        openBdvBtn.addActionListener { l: ActionEvent? -> controlledBridge!!.openSyncedBDV() }
        MastodonRowPlaceHolder.add(openBdvBtn, mc)
        //
        c.gridy = 0
        c.gridwidth = 2
        c.gridx = 0
        c.weightx = 0.1
        c.insets = Insets(4, sideSpace, 8, sideSpace - 2)
        controlsWindowPanel.add(MastodonRowPlaceHolder, c)
        c.gridy++
        c.insets = Insets(2, sideSpace, 2, sideSpace)
        controlsWindowPanel.add(
            JLabel("Volume pixel values 'v' are processed linearly, normalized, gamma, scaled back:"),
            c
        )
        c.gridwidth = 1
        c.gridy++
        insertNote("   pow( min(contrast*v +shift, not_above)/not_above, gamma ) *not_above", c)
        c.gridy++
        c.gridx = 0
        insertLabel("Apply on Volume this contrast factor:", c)
        //
        c.gridx = 1
        INTENSITY_CONTRAST = SpinnerNumberModel(1.0, -100.0, 100.0, 0.5)
        insertSpinner(INTENSITY_CONTRAST, { f: Float? -> controlledBridge!!.INTENSITY_CONTRAST = f!!.toDouble() }, c)
        c.gridy++
        c.gridx = 0
        insertLabel("Apply on Volume this shifting bias:", c)
        //
        c.gridx = 1
        INTENSITY_SHIFT = SpinnerNumberModel(0.0, -65535.0, 65535.0, 50.0)
        insertSpinner(INTENSITY_SHIFT, { f: Float? -> controlledBridge!!.INTENSITY_SHIFT = f!!.toDouble() }, c)
        c.gridy++
        c.gridx = 0
        insertLabel("Apply on Volume this gamma level:", c)
        //
        c.gridx = 1
        INTENSITY_GAMMA = SpinnerNumberModel(1.0, 0.1, 3.0, 0.1)
        insertSpinner(INTENSITY_GAMMA, { f: Float? -> controlledBridge!!.INTENSITY_GAMMA = f!!.toDouble() }, c)
        c.gridy++
        c.gridx = 0
        insertLabel("Clamp all voxels so that their values are not above:", c)
        //
        c.gridx = 1
        INTENSITY_CLAMP_AT_TOP = SpinnerNumberModel(700.0, 0.0, 65535.0, 50.0)
        insertSpinner(
            INTENSITY_CLAMP_AT_TOP,
            { f: Float? -> controlledBridge!!.INTENSITY_CLAMP_AT_TOP = f!!.toDouble() },
            c
        )

        // -------------- separator --------------
        c.gridy++
        insertSeparator(c)
        c.gridy++
        insertNote("Shortcuts to the standard sciview view controls, incl. Volume intensity mapping", c)
        c.gridy++
        val sliderBarPlaceHolder = JPanel()
        c.gridx = 0
        c.gridwidth = 2
        controlsWindowPanel.add(sliderBarPlaceHolder, c)
        //
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM = AdjustableBoundsRangeSlider.createAndPlaceHere(
            sliderBarPlaceHolder,
            controlledBridge!!.INTENSITY_RANGE_MIN.toInt(),
            controlledBridge!!.INTENSITY_RANGE_MAX.toInt(),
            0,
            10000
        )
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.addChangeListener(rangeSliderListener)
        c.gridy++
        visToggleSpots = JButton("Toggle visibility of SPOTS")
        visToggleSpots.addActionListener(toggleSpotsVisibility)
        visToggleVols = JButton("Toggle visibility of VOLUME")
        visToggleVols.addActionListener(toggleVolumeVisibility)
        //
        val twoCenteredButtonsPlaceHolder = JPanel()
        controlsWindowPanel.add(twoCenteredButtonsPlaceHolder, c)
        //
        twoCenteredButtonsPlaceHolder.setLayout(GridBagLayout())
        val bc = GridBagConstraints()
        bc.fill = GridBagConstraints.HORIZONTAL
        bc.weightx = 0.4
        bc.gridx = 0
        bc.insets = Insets(0, 0, 0, 20)
        twoCenteredButtonsPlaceHolder.add(visToggleSpots, bc)
        bc.gridx = 1
        bc.insets = Insets(0, 20, 0, 0)
        twoCenteredButtonsPlaceHolder.add(visToggleVols, bc)

        // -------------- separator --------------
        c.gridy++
        insertSeparator(c)
        c.gridy++
        insertNote("Parameters of the spots in-painting into the Volume", c)
        c.gridy++
        INTENSITY_OF_COLORS_APPLY =
            JCheckBox("Enable spots in-painting into the Volume (visible only on next repainting of Volume)")
        insertCheckBox(INTENSITY_OF_COLORS_APPLY, c)
        c.gridy++
        c.gridx = 0
        insertLabel("Next repainting of Volume will be triggered:", c)
        //
        c.gridx = 1
        UPDATE_VOLUME_AUTOMATICALLY = JComboBox(arrayOf(updVolMsgA, updVolMsgM))
        UPDATE_VOLUME_AUTOMATICALLY.addActionListener(updVolAutoListener)
        c.anchor = GridBagConstraints.LINE_END
        insertRColumnItem(UPDATE_VOLUME_AUTOMATICALLY, c)
        c.anchor = GridBagConstraints.LINE_START
        c.gridy++
        c.gridx = 0
        insertLabel("When repainting, draw colors at this nominal intensity:", c)
        //
        c.gridx = 1
        INTENSITY_OF_COLORS = SpinnerNumberModel(2400.0, 0.0, 65535.0, 50.0)
        insertSpinner(INTENSITY_OF_COLORS, { f: Float? -> controlledBridge!!.INTENSITY_OF_COLORS = f!!.toDouble() }, c)
        c.gridy++
        c.gridx = 0
        insertLabel("When repainting, multiply spots radii with:", c)
        //
        c.gridx = 1
        SPOT_RADIUS_SCALE = SpinnerNumberModel(3.0, 0.0, 50.0, 1.0)
        insertSpinner(SPOT_RADIUS_SCALE, { f: Float? -> controlledBridge!!.SPOT_RADIUS_SCALE = f!!.toDouble() }, c)
        c.gridy++
        INTENSITY_OF_COLORS_BOOST = JCheckBox("Enable enhancing of spot colors when repainting them into the Volume")
        insertCheckBox(INTENSITY_OF_COLORS_BOOST, c)
        c.gridy++
        UPDATE_VOLUME_VERBOSE_REPORTS = JCheckBox("Verbose/debug reporting during Volume repainting")
        insertCheckBox(UPDATE_VOLUME_VERBOSE_REPORTS, c)

        // -------------- button row --------------
        c.gridy++
        c.gridx = 0
        c.gridwidth = 1
        c.insets = Insets(10, sideSpace, sideSpace, sideSpace)
        val redrawBtn = JButton("  Repaint now (the recently painted timepoint)  ")
        redrawBtn.addActionListener { controlledBridge!!.updateSciviewColoringNow() }
        controlsWindowPanel.add(redrawBtn, c)
        c.gridx = 1
        c.anchor = GridBagConstraints.LINE_END
        val closeBtn = JButton("Close")
        closeBtn.addActionListener { controlledBridge!!.detachControllingUI() }
        insertRColumnItem(closeBtn, c)
    }

    val sideSpace = 15
    val noteSpace = Insets(2, sideSpace, 8, 2 * sideSpace)
    fun insertNote(noteText: String?, c: GridBagConstraints) {
        val prevGridW = c.gridwidth
        val prevInsets = c.insets
        c.gridwidth = 2
        c.insets = noteSpace
        c.weightx = 0.1
        c.gridx = 0
        controlsWindowPanel.add(JLabel(noteText), c)
        c.gridwidth = prevGridW
        c.insets = prevInsets
    }

    fun insertLabel(labelText: String?, c: GridBagConstraints) {
        c.weightx = 0.5
        controlsWindowPanel.add(JLabel(labelText), c)
    }

    val spinnerMinDim = Dimension(200, 20)
    val spinnerMaxDim = Dimension(1000, 20)
    fun insertSpinner(
        model: SpinnerModel,
        updaterOnEvents: Consumer<Float>,
        c: GridBagConstraints
    ) {
        c.anchor = GridBagConstraints.LINE_END
        insertRColumnItem(JSpinner(model), c)
        c.anchor = GridBagConstraints.LINE_START
        val l = OwnerAwareSpinnerChangeListener(updaterOnEvents, model)
        model.addChangeListener(l)
        spinnerModelsWithListeners.add(l)
    }

    fun insertRColumnItem(item: JComponent, c: GridBagConstraints) {
        item.minimumSize = spinnerMinDim
        item.preferredSize = spinnerMinDim
        item.maximumSize = spinnerMaxDim
        c.weightx = 0.3
        controlsWindowPanel.add(item, c)
    }

    fun insertCheckBox(cbox: JCheckBox, c: GridBagConstraints) {
        val prevFill = c.fill
        val prevGridW = c.gridwidth
        cbox.addItemListener(checkboxChangeListener)
        checkBoxesWithListeners.add(cbox)
        c.fill = GridBagConstraints.HORIZONTAL
        c.gridwidth = 2
        c.gridx = 0
        c.weightx = 0.1
        controlsWindowPanel.add(cbox, c)
        c.fill = prevFill
        c.gridwidth = prevGridW
    }

    val sepSpace = Insets(8, sideSpace, 8, sideSpace)
    fun insertSeparator(c: GridBagConstraints) {
        val prevFill = c.fill
        val prevGridW = c.gridwidth
        val prevInsets = c.insets
        c.fill = GridBagConstraints.HORIZONTAL
        c.gridwidth = 2
        c.weightx = 0.1
        c.insets = sepSpace
        c.gridx = 0
        controlsWindowPanel.add(JSeparator(JSeparator.HORIZONTAL), c)
        c.fill = prevFill
        c.gridwidth = prevGridW
        c.insets = prevInsets
    }

    inner class OwnerAwareSpinnerChangeListener //
        (
        val pushChangeToHere: Consumer<Float>,
        val observedSource: SpinnerModel
    ) : ChangeListener {
        //
        override fun stateChanged(changeEvent: ChangeEvent) {
            val s = changeEvent.source as SpinnerNumberModel
            pushChangeToHere.accept(s.number.toFloat())
            if (controlledBridge!!.UPDATE_VOLUME_AUTOMATICALLY) controlledBridge!!.updateSciviewColoringNow()
        }
    }

    var checkboxChangeListener = ItemListener { itemEvent ->
        val cb = itemEvent.source as JCheckBox
        if (cb === INTENSITY_OF_COLORS_APPLY) {
            controlledBridge.INTENSITY_OF_COLORS_APPLY = cb.isSelected
            if (controlledBridge.UPDATE_VOLUME_AUTOMATICALLY) controlledBridge.updateSciviewColoringNow()
        } else if (cb === INTENSITY_OF_COLORS_BOOST) {
            controlledBridge.INTENSITY_OF_COLORS_BOOST = cb.isSelected
            if (controlledBridge.UPDATE_VOLUME_AUTOMATICALLY) controlledBridge.updateSciviewColoringNow()
        } else if (cb === UPDATE_VOLUME_VERBOSE_REPORTS) {
            controlledBridge.UPDATE_VOLUME_VERBOSE_REPORTS = cb.isSelected
        }
    }
    val spinnerModelsWithListeners: MutableList<OwnerAwareSpinnerChangeListener> = ArrayList(10)
    val checkBoxesWithListeners: MutableList<JCheckBox> = ArrayList(10)

    //
    //below is also defined: rangeSliderListener
    //
    val updVolAutoListener = ActionListener {
        val prevBridgeState = controlledBridge.UPDATE_VOLUME_AUTOMATICALLY
        controlledBridge.UPDATE_VOLUME_AUTOMATICALLY = UPDATE_VOLUME_AUTOMATICALLY.getSelectedIndex() == 0
        if (controlledBridge.UPDATE_VOLUME_AUTOMATICALLY && !prevBridgeState) {
            //NB: a protection "if" here because this listener can be triggered even
            //when one re-chooses (re-clicks) the already chosen element
            controlledBridge.updateSciviewColoringNow()
        }
    }
    val toggleSpotsVisibility = ActionListener {
        val newState = !controlledBridge.sphereParent!!.visible
        controlledBridge.setVisibilityOfSpots(newState)
    }
    val toggleVolumeVisibility = ActionListener {
        val newState = !controlledBridge.redVolChannelNode!!.visible
        controlledBridge.setVisibilityOfVolume(newState)
    }

    /**
     * Disable all listeners to make sure that, even if this UI window would ever
     * be re-displayed, its controls could not control anything (and would throw
     * NPEs if the controls would actually be used).
     */
    fun deactivateAndForget() {
        //listeners tear-down here
        lockGroupHandler.deactivate()
        spinnerModelsWithListeners.forEach(Consumer { c: OwnerAwareSpinnerChangeListener ->
            c.observedSource.removeChangeListener(
                c
            )
        })
        checkBoxesWithListeners.forEach(Consumer { c: JCheckBox -> c.removeItemListener(checkboxChangeListener) })
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.removeChangeListener(rangeSliderListener)
        UPDATE_VOLUME_AUTOMATICALLY.removeActionListener(updVolAutoListener)
        visToggleSpots.removeActionListener(toggleSpotsVisibility)
        visToggleVols.removeActionListener(toggleVolumeVisibility)
        controlledBridge = null
    }

    fun updatePaneValues() {
        val updVolAutoBackup = controlledBridge!!.UPDATE_VOLUME_AUTOMATICALLY
        //temporarily disable because setting the controls trigger their listeners
        //that trigger (not all of them) the expensive volume updating
        controlledBridge!!.UPDATE_VOLUME_AUTOMATICALLY = false
        INTENSITY_CONTRAST.value = controlledBridge!!.INTENSITY_CONTRAST
        INTENSITY_SHIFT.value = controlledBridge!!.INTENSITY_SHIFT
        INTENSITY_CLAMP_AT_TOP.value = controlledBridge!!.INTENSITY_CLAMP_AT_TOP
        INTENSITY_GAMMA.value = controlledBridge!!.INTENSITY_GAMMA
        INTENSITY_OF_COLORS.value = controlledBridge!!.INTENSITY_OF_COLORS
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM
            .slider
            .setValue(controlledBridge!!.INTENSITY_RANGE_MIN.toInt())
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM
            .rangeSlider
            .setUpperValue(controlledBridge!!.INTENSITY_RANGE_MAX.toInt())
        INTENSITY_OF_COLORS_APPLY.setSelected(controlledBridge!!.INTENSITY_OF_COLORS_APPLY)
        INTENSITY_OF_COLORS_BOOST.setSelected(controlledBridge!!.INTENSITY_OF_COLORS_BOOST)
        SPOT_RADIUS_SCALE.value = controlledBridge!!.SPOT_RADIUS_SCALE
        UPDATE_VOLUME_AUTOMATICALLY.setSelectedItem(if (updVolAutoBackup) updVolMsgA else updVolMsgM)
        UPDATE_VOLUME_VERBOSE_REPORTS.setSelected(controlledBridge!!.UPDATE_VOLUME_VERBOSE_REPORTS)
        controlledBridge!!.UPDATE_VOLUME_AUTOMATICALLY = updVolAutoBackup
    }

    //int SOURCE_ID = 0;
    //int SOURCE_USED_RES_LEVEL = 0;
    lateinit var INTENSITY_CONTRAST: SpinnerModel
    lateinit var INTENSITY_SHIFT: SpinnerModel
    lateinit var INTENSITY_CLAMP_AT_TOP: SpinnerModel
    lateinit var INTENSITY_GAMMA: SpinnerModel
    lateinit var INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM: AdjustableBoundsRangeSlider
    lateinit var INTENSITY_OF_COLORS: SpinnerModel
    val rangeSliderListener = ChangeListener {
        controlledBridge.INTENSITY_RANGE_MIN = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.value.toDouble()
        controlledBridge.INTENSITY_RANGE_MAX = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.upperValue.toDouble()
        controlledBridge.redVolChannelNode!!.minDisplayRange = controlledBridge.INTENSITY_RANGE_MIN.toFloat()
        controlledBridge.redVolChannelNode.maxDisplayRange = controlledBridge.INTENSITY_RANGE_MAX.toFloat()
    }

    //
    lateinit var visToggleSpots: JButton
    lateinit var visToggleVols: JButton
    lateinit var INTENSITY_OF_COLORS_APPLY: JCheckBox
    lateinit var INTENSITY_OF_COLORS_BOOST: JCheckBox
    lateinit var SPOT_RADIUS_SCALE: SpinnerModel
    lateinit var UPDATE_VOLUME_AUTOMATICALLY: JComboBox<String>
    lateinit var UPDATE_VOLUME_VERBOSE_REPORTS: JCheckBox
    lateinit var lockGroupHandler: GroupLocksHandling

    init {
        this.controlledBridge = controlledBridge
        controlsWindowPanel = populateThisContainer
        populatePane()
        updatePaneValues()
    }

    companion object {
        const val updVolMsgA = "Automatically"
        const val updVolMsgM = "Manually"
    }
}