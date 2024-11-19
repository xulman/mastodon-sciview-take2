package util

import bdv.ui.rangeslider.RangeSlider
import net.miginfocom.swing.MigLayout
import java.awt.*
import javax.swing.*
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
            intoThisComponent: JPanel,
            initialLowValue: Int,
            initialHighValue: Int,
            initialMin: Int,
            initialMax: Int
        ): AdjustableBoundsRangeSlider {
            require(!(initialLowValue < initialMin || initialLowValue > initialMax)) { "Refuse to create slider showing \"low\" value that's outside the slider's min and max range." }
            require(!(initialHighValue < initialMin || initialHighValue > initialMax)) { "Refuse to create slider showing \"high\" value that's outside the slider's min and max range." }
            require(!(initialMin < MIN_BOUND_LIMIT || initialMin > MAX_BOUND_LIMIT)) { "Required MIN bound is outside the interval assumed by this governing class." }
            require(!(initialMax < MIN_BOUND_LIMIT || initialMax > MAX_BOUND_LIMIT)) { "Required MAX bound is outside the interval assumed by this governing class." }

            // Set to the current wanted range
            val slider = RangeSlider(initialMin, initialMax)
            slider.setValue(initialLowValue)
            slider.setUpperValue(initialHighValue)
            val lowSpinner = JSpinner(createAppropriateSpinnerModel(initialLowValue))
            val highSpinner = JSpinner(createAppropriateSpinnerModel(initialHighValue))
            val lowBoundInformer = JLabel(initialMin.toString())
            val highBoundInformer = JLabel(initialMax.toString())

            // Font setup (copied from the original logic)
            val labelFont = UIManager.getFont("Label.font")
            val font = Font(labelFont.name, labelFont.style, 10)
            lowBoundInformer.font = font
            highBoundInformer.font = font

            // Use MigLayout to position components
            intoThisComponent.layout = MigLayout(
                "insets 5",  // Insets around the panel
                "[grow,fill]10[grow,fill]10[fill]10[fill]", // Columns layout
                "" // Rows layout, dynamically adjusts
            )

            intoThisComponent.add(lowSpinner, "cell 0 0")
            intoThisComponent.add(slider, "cell 1 0, spanx 2, growx") // Span across two columns
            intoThisComponent.add(highSpinner, "cell 3 0")

            intoThisComponent.add(lowBoundInformer, "cell 0 1")
            intoThisComponent.add(highBoundInformer, "cell 3 1")

            return AdjustableBoundsRangeSlider(slider, lowSpinner, highSpinner, lowBoundInformer, highBoundInformer)
        }
    }
}