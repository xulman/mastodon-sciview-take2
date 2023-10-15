package util

import bdv.ui.rangeslider.RangeSlider
import java.awt.*
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.UIManager
import javax.swing.event.ChangeEvent
import kotlin.math.max
import kotlin.math.min

class AdjustableBoundsRangeSlider(
    val rangeSlider: RangeSlider,
    associatedLowValueSpinner: JSpinner?,
    associatedHighValueSpinner: JSpinner,
    associatedLowBound: JLabel?,
    associatedHighBound: JLabel?
) : AbstractAdjustableSliderBasedControl(
    rangeSlider,
    associatedLowValueSpinner!!,
    associatedLowBound!!,
    associatedHighBound!!
) {
    val upperValue: Int
        get() = rangeSlider.upperValue
    protected var originalSliderUpperValue = -1 //aka before-dragging-value

    init {

        //listeners setup: setting the slider from the associated spinner
        val m = associatedHighValueSpinner.model
        require(m is SpinnerNumberModel) { "The provided spinner for high-value is expected to be of the type SpinnerNumberModel." }
        val nm = m //NB: safe to cast...
        nm.addChangeListener {
            var value = nm.value as Int
            value = max(slider.minimum.toDouble(), min(value.toDouble(), slider.maximum.toDouble()))
                .toInt()
            nm.setValue(value) //basically, assures that the spinner is also not outside the current bounds
            rangeSlider.setUpperValue(value)
        }

        //listeners setup: forwarder also to the associated high-value spinner
        rangeSlider.addChangeListener { nm.setValue(rangeSlider.upperValue) }
    }

    override fun storeSliderThumbsPositions() {
        super.storeSliderThumbsPositions()
        originalSliderUpperValue = rangeSlider.upperValue
    }

    override fun fixupSliderThumbsPositions() {
        super.fixupSliderThumbsPositions()
        if (originalSliderUpperValue < rangeSlider.minimum) rangeSlider.setUpperValue(rangeSlider.minimum) else if (originalSliderUpperValue > rangeSlider.maximum) rangeSlider.setUpperValue(
            rangeSlider.maximum
        ) else rangeSlider.setUpperValue(originalSliderUpperValue)
    }

    override fun didSliderThumbsChangedPositions(): Boolean {
        val lowChanged = super.didSliderThumbsChangedPositions()
        val highChanged = rangeSlider.upperValue != originalSliderUpperValue
        return lowChanged || highChanged
    }

    companion object {
        fun createAndPlaceHere(
            intoThisComponent: Container,
            initialLowValue: Int,
            initialHighValue: Int,
            initialMin: Int,
            initialMax: Int
        ): AdjustableBoundsRangeSlider {
            require(!(initialLowValue < initialMin || initialLowValue > initialMax)) { "Refuse to create slider showing \"low\" value that's outside the slider's min and max range." }
            require(!(initialHighValue < initialMin || initialHighValue > initialMax)) { "Refuse to create slider showing \"high\" value that's outside the slider's min and max range." }
            require(!(initialMin < MIN_BOUND_LIMIT || initialMin > MAX_BOUND_LIMIT)) { "Required MIN bound is outside the interval assumed by this governing class." }
            require(!(initialMax < MIN_BOUND_LIMIT || initialMax > MAX_BOUND_LIMIT)) { "Required MAX bound is outside the interval assumed by this governing class." }
            val gridBagLayout = GridBagLayout()
            intoThisComponent.setLayout(gridBagLayout)
            val c = GridBagConstraints()
            c.anchor = GridBagConstraints.LINE_START
            c.fill = GridBagConstraints.HORIZONTAL
            val defaultInset = c.insets

            //set to the current wanted range
            val slider = RangeSlider(initialMin, initialMax)
            slider.setValue(initialLowValue)
            slider.setUpperValue(initialHighValue)
            val lowSpinner = JSpinner(
                createAppropriateSpinnerModel(initialLowValue)
            )
            val highSpinner = JSpinner(
                createAppropriateSpinnerModel(initialHighValue)
            )
            val lowBoundInformer = JLabel(initialMin.toString())
            val highBoundInformer = JLabel(initialMax.toString())

            //from bigdataviewer-core/src/main/java/bdv/ui/convertersetupeditor/BoundedRangePanel.java,
            //method updateBoundLabelFonts(), L283
            val labelFont = UIManager.getFont("Label.font")
            val font = Font(labelFont.name, labelFont.style, 10)
            lowBoundInformer.setFont(font)
            highBoundInformer.setFont(font)
            c.gridheight = 2
            c.gridy = 0
            c.weightx = 0.05
            c.gridx = 0
            intoThisComponent.add(lowSpinner, c)
            c.weightx = 0.85
            c.gridx = 1
            c.insets = Insets(defaultInset.top, 5, defaultInset.bottom, 5)
            intoThisComponent.add(slider, c)
            c.insets = defaultInset
            c.weightx = 0.05
            c.gridx = 2
            intoThisComponent.add(highSpinner, c)
            c.gridheight = 1
            c.gridx = 3
            c.insets = Insets(defaultInset.top, 5, defaultInset.bottom, defaultInset.right)
            intoThisComponent.add(highBoundInformer, c)
            c.gridy = 1
            intoThisComponent.add(lowBoundInformer, c)
            return AdjustableBoundsRangeSlider(slider, lowSpinner, highSpinner, lowBoundInformer, highBoundInformer)
        }
    }
}
