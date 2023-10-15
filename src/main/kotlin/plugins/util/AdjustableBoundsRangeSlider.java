package plugins.util;

import bdv.ui.rangeslider.RangeSlider;

import javax.swing.*;
import java.awt.*;

public class AdjustableBoundsRangeSlider extends AbstractAdjustableSliderBasedControl {
	protected final RangeSlider rangeSlider;
	public AdjustableBoundsRangeSlider(final RangeSlider manageThisSlider,
	                                   final JSpinner associatedLowValueSpinner,
	                                   final JSpinner associatedHighValueSpinner,
	                                   final JLabel associatedLowBound,
	                                   final JLabel associatedHighBound) {
		super(manageThisSlider, associatedLowValueSpinner, associatedLowBound, associatedHighBound);
		this.rangeSlider = manageThisSlider;

		//listeners setup: setting the slider from the associated spinner
		final SpinnerModel m = associatedHighValueSpinner.getModel();
		if (! (m instanceof SpinnerNumberModel))
			throw new IllegalArgumentException("The provided spinner for high-value is expected to be of the type SpinnerNumberModel.");
		final SpinnerNumberModel nm = (SpinnerNumberModel)m; //NB: safe to cast...
		nm.addChangeListener(l -> {
			int value = (int)nm.getValue();
			value = Math.max(slider.getMinimum(), Math.min(value, slider.getMaximum()));
			nm.setValue(value); //basically, assures that the spinner is also not outside the current bounds
			rangeSlider.setUpperValue(value);
		});

		//listeners setup: forwarder also to the associated high-value spinner
		rangeSlider.addChangeListener(event -> {
			nm.setValue(rangeSlider.getUpperValue());
		});
	}

	public RangeSlider getRangeSlider() {
		return rangeSlider;
	}

	public int getUpperValue() {
		return rangeSlider.getUpperValue();
	}

	protected int originalSliderUpperValue = -1; //aka before-dragging-value
	@Override
	protected void storeSliderThumbsPositions() {
		super.storeSliderThumbsPositions();
		originalSliderUpperValue = rangeSlider.getUpperValue();
	}
	@Override
	protected void fixupSliderThumbsPositions() {
		super.fixupSliderThumbsPositions();
		if (originalSliderUpperValue < rangeSlider.getMinimum()) rangeSlider.setUpperValue(rangeSlider.getMinimum());
		else if (originalSliderUpperValue > rangeSlider.getMaximum()) rangeSlider.setUpperValue(rangeSlider.getMaximum());
		else rangeSlider.setUpperValue(originalSliderUpperValue);
	}
	@Override
	protected boolean didSliderThumbsChangedPositions() {
		boolean lowChanged = super.didSliderThumbsChangedPositions();
		boolean highChanged = rangeSlider.getUpperValue() != originalSliderUpperValue;
		return lowChanged || highChanged;
	}

	public static AdjustableBoundsRangeSlider createAndPlaceHere(final Container intoThisComponent,
	                                                             final int initialLowValue,
	                                                             final int initialHighValue,
	                                                             final int initialMin,
	                                                             final int initialMax) {
		if (initialLowValue < initialMin || initialLowValue > initialMax)
			throw new IllegalArgumentException("Refuse to create slider showing \"low\" value that's outside the slider's min and max range.");
		if (initialHighValue < initialMin || initialHighValue > initialMax)
			throw new IllegalArgumentException("Refuse to create slider showing \"high\" value that's outside the slider's min and max range.");
		if (initialMin < MIN_BOUND_LIMIT || initialMin > MAX_BOUND_LIMIT)
			throw new IllegalArgumentException("Required MIN bound is outside the interval assumed by this governing class.");
		if (initialMax < MIN_BOUND_LIMIT || initialMax > MAX_BOUND_LIMIT)
			throw new IllegalArgumentException("Required MAX bound is outside the interval assumed by this governing class.");

		final GridBagLayout gridBagLayout = new GridBagLayout();
		intoThisComponent.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		final Insets defaultInset = c.insets;

		//set to the current wanted range
		RangeSlider slider = new RangeSlider(initialMin, initialMax);
		slider.setValue(initialLowValue);
		slider.setUpperValue(initialHighValue);
		JSpinner lowSpinner = new JSpinner(
				AbstractAdjustableSliderBasedControl.createAppropriateSpinnerModel(initialLowValue) );
		JSpinner highSpinner = new JSpinner(
				AbstractAdjustableSliderBasedControl.createAppropriateSpinnerModel(initialHighValue) );
		JLabel lowBoundInformer = new JLabel(String.valueOf(initialMin));
		JLabel highBoundInformer = new JLabel(String.valueOf(initialMax));

		//from bigdataviewer-core/src/main/java/bdv/ui/convertersetupeditor/BoundedRangePanel.java,
		//method updateBoundLabelFonts(), L283
		final Font labelFont = UIManager.getFont( "Label.font" );
		final Font font = new Font( labelFont.getName(), labelFont.getStyle(), 10 );
		lowBoundInformer.setFont( font );
		highBoundInformer.setFont( font );

		c.gridheight = 2;
		c.gridy = 0;
		c.weightx = 0.05;
		c.gridx = 0;
		intoThisComponent.add(lowSpinner, c);
		c.weightx = 0.85;
		c.gridx = 1;
		c.insets = new Insets(defaultInset.top, 5, defaultInset.bottom, 5);
		intoThisComponent.add(slider, c);
		c.insets = defaultInset;
		c.weightx = 0.05;
		c.gridx = 2;
		intoThisComponent.add(highSpinner, c);
		c.gridheight = 1;
		c.gridx = 3;
		c.insets = new Insets(defaultInset.top, 5, defaultInset.bottom, defaultInset.right);
		intoThisComponent.add(highBoundInformer, c);
		c.gridy = 1;
		intoThisComponent.add(lowBoundInformer, c);

		return new AdjustableBoundsRangeSlider(slider,lowSpinner,highSpinner,lowBoundInformer,highBoundInformer);
	}
}
