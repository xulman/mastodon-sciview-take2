package org.mastodon.mamut

import graphics.scenery.utils.lazyLogger
import net.miginfocom.swing.MigLayout
import util.AdjustableBoundsRangeSlider
import util.GroupLocksHandling
import util.SphereLinkNodes
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.ChangeListener

class SciviewBridgeUIMig(controlledBridge: SciviewBridge, populateThisContainer: JPanel) {
    var controlledBridge: SciviewBridge?
    val windowPanel: JPanel
    private val logger by lazyLogger()

    lateinit var INTENSITY_CONTRAST: SpinnerModel
    lateinit var INTENSITY_SHIFT: SpinnerModel
    lateinit var INTENSITY_CLAMP_AT_TOP: SpinnerModel
    lateinit var INTENSITY_GAMMA: SpinnerModel
    lateinit var INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM: AdjustableBoundsRangeSlider
    lateinit var MIPMAP_LEVEL: SpinnerNumberModel
    lateinit var visToggleSpots: JButton
    lateinit var visToggleVols: JButton
    lateinit var visToggleTracks: JButton
    lateinit var linkRangeBackwards: SpinnerModel
    lateinit var linkRangeForwards: SpinnerModel
    lateinit var autoIntensityBtn: JToggleButton
    lateinit var lockGroupHandler: GroupLocksHandling
    lateinit var linkColorSelector: JComboBox<String>
    lateinit var volumeColorSelector: JComboBox<String>
    lateinit var startEyeTracking: JButton
    lateinit var stopEyeTracking: JButton

    private fun populatePane() {
        val bridge = this.controlledBridge ?: throw IllegalStateException("The passed bridge cannot be null.")

        windowPanel.layout = MigLayout("", "[][grow]", "")

        // Lock Group Handling and Mastodon
        lockGroupHandler = GroupLocksHandling(bridge, bridge.mastodon)
        windowPanel.add(lockGroupHandler.createAndActivate()!!, "span, growx")

        val openBdvBtn = JButton("Open synced Mastodon BDV").apply {
            addActionListener { bridge.openSyncedBDV() }
        }
        windowPanel.add(openBdvBtn, "span, growx")

        // Intensity controls
        windowPanel.add(
            JLabel("Volume pixel values 'v' are processed linearly, normalized, gamma, scaled back:"),
            "span, growx"
        )
        windowPanel.add(JLabel("   pow( min(contrast*v + shift, not_above)/not_above, gamma ) *not_above"), "span")

        addLabeledSpinner(
            "Apply on Volume this contrast factor:",
            SpinnerNumberModel(1.0, -100.0, 100.0, 0.5)
        ) { value -> bridge.intensity.contrast = value as Float }

        addLabeledSpinner(
            "Apply on Volume this shifting bias:",
            SpinnerNumberModel(0.0, -65535.0, 65535.0, 50.0)
        ) { value -> bridge.intensity.shift = value as Float }

        addLabeledSpinner(
            "Apply on Volume this gamma level:",
            SpinnerNumberModel(1.0, 0.1, 3.0, 0.1)
        ) { value -> bridge.intensity.gamma = value as Float }

        addLabeledSpinner(
            "Clamp all voxels so that their values are not above:",
            SpinnerNumberModel(700.0, 0.0, 65535.0, 50.0)
        ) { value -> bridge.intensity.clampTop = value as Float }

        // MIPMAP Level
        addLabeledSpinner("Choose Mipmap Level", SpinnerNumberModel(0, 0, 6, 1)) { level ->
            controlledBridge?.let {
                it.associatedUI?.setMaxMipmapLevel(it.sac.spimSource.numMipmapLevels - 1)
                it.setMipmapLevel(level as Float)
            }
        }

        // Range Slider
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM = AdjustableBoundsRangeSlider.createAndPlaceHere(
            windowPanel,
            bridge.intensity.rangeMin.toInt(),
            bridge.intensity.rangeMax.toInt(),
            0,
            10000
        )
        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.addChangeListener(rangeSliderListener)

        // Link range spinners
        addLabeledSpinner(
            "Link window range backwards",
            SpinnerNumberModel(bridge.mastodon.maxTimepoint, 0, bridge.mastodon.maxTimepoint, 1)
        ) { value ->
            bridge.sphereLinkNodes.linkBackwardRange = value.toInt()
            bridge.sphereLinkNodes.updateLinkVisibility(bridge.lastTpWhenVolumeWasUpdated)
        }

        addLabeledSpinner(
            "Link window range forwards",
            SpinnerNumberModel(bridge.mastodon.maxTimepoint, 0, bridge.mastodon.maxTimepoint, 1)
        ) { value ->
            bridge.sphereLinkNodes.linkForwardRange = value.toInt()
            bridge.sphereLinkNodes.updateLinkVisibility(bridge.lastTpWhenVolumeWasUpdated)
        }


        // Adding dropdowns for link LUTs and volume colors
        val colorSelectorPanel = JPanel(MigLayout("fillx, insets 0", "[right][grow, fill]", ""))

        // Link colors dropdown
        colorSelectorPanel.add(JLabel("Link colors:"), "gapright 10")
        val linkColorChoices = mutableListOf("By Spot")
        val availableLUTs = bridge.sciviewWin.getAvailableLUTs() as MutableList<String>
        for (i in availableLUTs.indices) {
            availableLUTs[i] = availableLUTs[i].removeSuffix(".lut")
        }
        linkColorChoices.addAll(availableLUTs)

        linkColorSelector = JComboBox(linkColorChoices.toTypedArray())
        linkColorSelector.setSelectedItem("Fire")
        linkColorSelector.addActionListener(chooseLinkColormap)
        colorSelectorPanel.add(linkColorSelector, "wrap")

        // Volume colors dropdown
        colorSelectorPanel.add(JLabel("Volume colors:"), "gapright 10")
        volumeColorSelector = JComboBox(availableLUTs.toTypedArray())
        volumeColorSelector.setSelectedItem("Grays")
        volumeColorSelector.addActionListener(chooseVolumeColormap)
        colorSelectorPanel.add(volumeColorSelector, "wrap")

        // Add the color selector panel to the main panel
        windowPanel.add(colorSelectorPanel, "span, growx, wrap")

        // Visualization Toggles
        visToggleSpots = JButton("Toggle spots").apply { addActionListener(toggleSpotsVisibility) }
        visToggleVols = JButton("Toggle volume").apply { addActionListener(toggleVolumeVisibility) }
        visToggleTracks = JButton("Toggle tracks").apply { addActionListener(toggleTrackVisibility) }
        autoIntensityBtn = JToggleButton("Auto Intensity", bridge.isVolumeAutoAdjust).apply {
            addActionListener(autoAdjustIntensity)
        }

        val visButtons = JPanel(MigLayout("fillx", "[grow]")).apply {
            add(autoIntensityBtn, "growx")
            add(visToggleSpots, "growx")
            add(visToggleVols, "growx")
            add(visToggleTracks, "growx")
        }
        windowPanel.add(visButtons, "span, growx")

        // Eye Tracking
        startEyeTracking = JButton("Start Eye Tracking").apply { addActionListener { bridge.launchEyeTracking() } }
        stopEyeTracking = JButton("Stop Eye Tracking").apply { addActionListener { bridge.stopEyeTracking() } }
        windowPanel.add(JPanel(MigLayout("fillx")).apply {
            add(startEyeTracking, "growx")
            add(stopEyeTracking, "growx")
        }, "span, growx")

        // Close Button
        val closeBtn = JButton("Close").apply { addActionListener { bridge.detachControllingUI() } }
        windowPanel.add(closeBtn, "span, right")
    }

    fun addLabeledSpinner(labelText: String, spinnerModel: SpinnerNumberModel, onChange: (Number) -> Unit) {
        val label = JLabel(labelText)
        val spinner = JSpinner(spinnerModel)

        spinner.addChangeListener { onChange(spinner.value as Number) }

        // Adding the label and spinner to the panel
        windowPanel.add(label)
        windowPanel.add(spinner, "wrap")
    }

    /** Sets the maximum mipmap level found in the volume node as the spinner's max value. */
    fun setMaxMipmapLevel(level: Int) {
        MIPMAP_LEVEL.maximum = level
    }

    val rangeSliderListener = ChangeListener {
        controlledBridge.intensity.rangeMin = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.value.toFloat()
        controlledBridge.intensity.rangeMax = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.upperValue.toFloat()
        controlledBridge.volumeNode.minDisplayRange = controlledBridge.intensity.rangeMin
        controlledBridge.volumeNode.maxDisplayRange = controlledBridge.intensity.rangeMax
    }

    val chooseLinkColormap = ActionListener { _ ->
        when (linkColorSelector.selectedItem) {
            "By Spot" -> {
                controlledBridge.sphereLinkNodes.currentColorMode = SphereLinkNodes.ColorMode.SPOT
                logger.info("Coloring links by spot color")
            }
            else -> {
                controlledBridge.sphereLinkNodes.currentColorMode = SphereLinkNodes.ColorMode.LUT
                controlledBridge.sphereLinkNodes.setLUT("${linkColorSelector.selectedItem}.lut")
                logger.info("Coloring links with LUT ${linkColorSelector.selectedItem}")
            }
        }
        controlledBridge.sphereLinkNodes.updateLinkColors(controlledBridge.recentColorizer ?: controlledBridge.noTSColorizer)
    }

    val chooseVolumeColormap = ActionListener {
        controlledBridge.sciviewWin.setColormap(controlledBridge.volumeNode, "${volumeColorSelector.selectedItem}.lut")
        logger.info("Coloring volume with LUT ${volumeColorSelector.selectedItem}")
    }

    val toggleSpotsVisibility = ActionListener {
        val spots = controlledBridge.volumeNode.getChildrenByName("SpotInstance").first()
        val newState = !spots.visible
        spots.visible = newState
    }
    val toggleVolumeVisibility = ActionListener {
        val spots = controlledBridge.volumeNode.getChildrenByName("SpotInstance").first()
        val spotVis = spots.visible
        val links = controlledBridge.volumeNode.getChildrenByName("LinkInstance").first()
        val linksVis = links.visible
        val newState = !controlledBridge.volumeNode.visible
        controlledBridge.setVisibilityOfVolume(newState)
        spots.visible = spotVis
        links.visible = linksVis
    }
    val toggleTrackVisibility = ActionListener {
        val links = controlledBridge.volumeNode.getChildrenByName("LinkInstance").first()
        val newState = !links.visible
        links.visible = newState
    }

    val autoAdjustIntensity = ActionListener {
        controlledBridge.autoAdjustIntensity()
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

    fun deactivateAndForget() {
        //listeners tear-down here
        lockGroupHandler.deactivate()
        // Remove listeners for link colors and volume colors
        linkColorSelector.removeActionListener(chooseLinkColormap)
        volumeColorSelector.removeActionListener(chooseVolumeColormap)

        INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.removeChangeListener(rangeSliderListener)
        visToggleSpots.removeActionListener(toggleSpotsVisibility)
        visToggleVols.removeActionListener(toggleVolumeVisibility)
        autoIntensityBtn.removeActionListener(autoAdjustIntensity)
        controlledBridge = null

    }

    init {
        this.controlledBridge = controlledBridge
        windowPanel = populateThisContainer
        populatePane()
    }
}
