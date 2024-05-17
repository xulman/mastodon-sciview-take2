package org.mastodon.mamut

import util.AdjustableBoundsRangeSlider
import util.GroupLocksHandling
import java.awt.*
import java.awt.event.ActionListener
import java.util.function.Consumer
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class SciviewBridgeUI(controlledBridge: SciviewBridge, populateThisContainer: Container) {
    var controlledBridge: SciviewBridge?
    val controlsWindowPanel: Container

    // -------------------------------------------------------------------------------------------
    private fun populatePane() {
        val bridge = this.controlledBridge ?: throw IllegalStateException("The passed bridge cannot be null.")

        controlsWindowPanel.setLayout(GridBagLayout())
        val c = GridBagConstraints()
        c.anchor = GridBagConstraints.LINE_START
        c.fill = GridBagConstraints.HORIZONTAL
        val mastodonRowPlaceHolder = JPanel()
        mastodonRowPlaceHolder.setLayout(GridBagLayout())
        val mc = GridBagConstraints()
        mc.anchor = GridBagConstraints.LINE_START
        mc.fill = GridBagConstraints.HORIZONTAL
        mc.insets = Insets(0, 0, 0, 0)
        mc.weightx = 0.2
        mc.gridx = 0
        lockGroupHandler = GroupLocksHandling(bridge, bridge.mastodon)
        mastodonRowPlaceHolder.add(lockGroupHandler.createAndActivate()!!, mc)
        mc.weightx = 0.6
        mc.gridx = 1
        val openBdvBtn = JButton("Open synced Mastodon BDV")
        openBdvBtn.addActionListener { bridge.openSyncedBDV() }
        mastodonRowPlaceHolder.add(openBdvBtn, mc)
        //
        c.gridy = 0
        c.gridwidth = 2
        c.gridx = 0
        c.weightx = 0.1
        c.insets = Insets(4, sideSpace, 8, sideSpace - 2)
        controlsWindowPanel.add(mastodonRowPlaceHolder, c)
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
        insertSpinner(INTENSITY_CONTRAST, { f: Float -> bridge.intensity.contrast = f }, c)
        c.gridy++
        c.gridx = 0
        insertLabel("Apply on Volume this shifting bias:", c)
        //
        c.gridx = 1
        INTENSITY_SHIFT = SpinnerNumberModel(0.0, -65535.0, 65535.0, 50.0)
        insertSpinner(INTENSITY_SHIFT, { f: Float -> bridge.intensity.shift = f }, c)
        c.gridy++
        c.gridx = 0
        insertLabel("Apply on Volume this gamma level:", c)
        //
        c.gridx = 1
        INTENSITY_GAMMA = SpinnerNumberModel(1.0, 0.1, 3.0, 0.1)
        insertSpinner(INTENSITY_GAMMA, { f: Float -> bridge.intensity.gamma = f }, c)
        c.gridy++
        c.gridx = 0
        insertLabel("Clamp all voxels so that their values are not above:", c)
        //
        c.gridx = 1
        INTENSITY_CLAMP_AT_TOP = SpinnerNumberModel(700.0, 0.0, 65535.0, 50.0)
        insertSpinner(
            INTENSITY_CLAMP_AT_TOP,
            { f: Float -> bridge.intensity.clampTop = f },
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
            bridge.intensity.rangeMin.toInt(),
            bridge.intensity.rangeMax.toInt(),
            0,
            10000
        )
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.addChangeListener(rangeSliderListener)
        c.gridy++
        visToggleSpots = JButton("Toggle visibility of SPOTS")
        visToggleSpots.addActionListener(toggleSpotsVisibility)
        visToggleVols = JButton("Toggle visibility of VOLUME")
        visToggleVols.addActionListener(toggleVolumeVisibility)
        autoIntensityBtn = JToggleButton("Auto Intensity", bridge.isVolumeAutoAdjust)
        autoIntensityBtn.addActionListener(autoAdjustIntensity)
        //
        val threeCenteredButtonsPlaceHolder = JPanel()
        controlsWindowPanel.add(threeCenteredButtonsPlaceHolder, c)
        //
        threeCenteredButtonsPlaceHolder.setLayout(GridBagLayout())
        val bc = GridBagConstraints()
        bc.fill = GridBagConstraints.HORIZONTAL
        bc.weightx = 0.4
        bc.gridx = 0
//        bc.insets = Insets(0, 20, 0, 0)
        threeCenteredButtonsPlaceHolder.add(autoIntensityBtn, bc)
        bc.gridx = 1
        bc.insets = Insets(0, 20, 0, 0)
        threeCenteredButtonsPlaceHolder.add(visToggleSpots, bc)
        bc.gridx = 2
        bc.insets = Insets(0, 20, 0, 0)
        threeCenteredButtonsPlaceHolder.add(visToggleVols, bc)
//        bc.insets = Insets(0, 0, 0, 20)

        // -------------- button row --------------
        c.gridy++
        c.gridx = 0
        c.gridwidth = 1
        c.anchor = GridBagConstraints.LINE_END
        val closeBtn = JButton("Close")
        closeBtn.addActionListener { bridge.detachControllingUI() }
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
            val bridge = this@SciviewBridgeUI.controlledBridge ?: throw IllegalStateException("Bridge is null.")
            val s = changeEvent.source as SpinnerNumberModel
            pushChangeToHere.accept(s.number.toFloat())
            if (bridge.updateVolAutomatically) bridge.updateVolume()
        }
    }

    val spinnerModelsWithListeners: MutableList<OwnerAwareSpinnerChangeListener> = ArrayList(10)
    val checkBoxesWithListeners: MutableList<JCheckBox> = ArrayList(10)

    val toggleSpotsVisibility = ActionListener {
        val newState = !controlledBridge.sphereParent.visible
        controlledBridge.setVisibilityOfSpots(newState)
    }
    val toggleVolumeVisibility = ActionListener {
        val newState = !controlledBridge.volumeNode.visible
        controlledBridge.setVisibilityOfVolume(newState)
    }

    val autoAdjustIntensity = ActionListener {
        controlledBridge.autoAdjustIntensity()
    }

    /**
     * Disable all listeners to make sure that, even if this UI window would ever
     * be re-displayed, its controls could not control anything (and would throw
     * NPEs if the controls would actually be used).
     */
    fun deactivateAndForget() {
        //listeners tear-down here
        lockGroupHandler.deactivate()
        spinnerModelsWithListeners.forEach { c: OwnerAwareSpinnerChangeListener ->
            c.observedSource.removeChangeListener(
                c
            )
        }
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.removeChangeListener(rangeSliderListener)
        visToggleSpots.removeActionListener(toggleSpotsVisibility)
        visToggleVols.removeActionListener(toggleVolumeVisibility)
        autoIntensityBtn.removeActionListener(autoAdjustIntensity)
        controlledBridge = null
    }

    fun updatePaneValues() {
        val bridge = this.controlledBridge ?: throw IllegalStateException("Bridge is null.")
        val updVolAutoBackup = bridge.updateVolAutomatically
        //temporarily disable because setting the controls trigger their listeners
        //that trigger (not all of them) the expensive volume updating

        bridge.updateVolAutomatically = false
        INTENSITY_CONTRAST.value = bridge.intensity.contrast
        INTENSITY_SHIFT.value = bridge.intensity.shift
        INTENSITY_CLAMP_AT_TOP.value = bridge.intensity.clampTop
        INTENSITY_GAMMA.value = bridge.intensity.gamma
        val upperValBackup = bridge.intensity.rangeMax

        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM
            .rangeSlider
            .value = bridge.intensity.rangeMin.toInt()
        //NB: this triggers a "value changed listener" which updates _both_ the value and upperValue,
        //    which resets the value with the new one (so no change in the end) but clears upperValue
        //    to the value the dialog was left with (forgets the new upperValue effectively)
        bridge.intensity.rangeMax = upperValBackup
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM
            .rangeSlider
            .upperValue = bridge.intensity.rangeMax.toInt()

        autoIntensityBtn.isSelected = bridge.isVolumeAutoAdjust
        bridge.updateVolAutomatically = updVolAutoBackup
    }

    //int SOURCE_ID = 0;
    //int SOURCE_USED_RES_LEVEL = 0;
    lateinit var INTENSITY_CONTRAST: SpinnerModel
    lateinit var INTENSITY_SHIFT: SpinnerModel
    lateinit var INTENSITY_CLAMP_AT_TOP: SpinnerModel
    lateinit var INTENSITY_GAMMA: SpinnerModel
    lateinit var INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM: AdjustableBoundsRangeSlider
    val rangeSliderListener = ChangeListener {
        controlledBridge.intensity.rangeMin = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.value.toFloat()
        controlledBridge.intensity.rangeMax = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.upperValue.toFloat()
        controlledBridge.volumeNode.minDisplayRange = controlledBridge.intensity.rangeMin
        controlledBridge.volumeNode.maxDisplayRange = controlledBridge.intensity.rangeMax
    }

    //
    lateinit var visToggleSpots: JButton
    lateinit var visToggleVols: JButton
    lateinit var autoIntensityBtn: JToggleButton
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