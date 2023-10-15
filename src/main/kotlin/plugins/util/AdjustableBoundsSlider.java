package plugins.util;

import javax.swing.*;
import java.awt.*;

public class AdjustableBoundsSlider extends AbstractAdjustableSliderBasedControl {
	public AdjustableBoundsSlider(final JSlider manageThisSlider,
	                              final JSpinner associatedValueSpinner,
	                              final JLabel associatedLowBound,
	                              final JLabel associatedHighBound) {
		super(manageThisSlider, associatedValueSpinner, associatedLowBound, associatedHighBound);
	}

	public static AdjustableBoundsSlider createAndPlaceHere(final Container intoThisComponent,
	                                                        final int initialValue,
	                                                        final int initialMin,
	                                                        final int initialMax) {
		if (initialValue < initialMin || initialValue > initialMax)
			throw new IllegalArgumentException("Refuse to create slider showing value that's outside the slider's min and max range.");
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
		JSlider slider = new JSlider(JSlider.HORIZONTAL, initialMin, initialMax, initialValue);
		JSpinner spinner = new JSpinner(
				AbstractAdjustableSliderBasedControl.createAppropriateSpinnerModel(initialValue) );
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
		intoThisComponent.add(spinner, c);
		c.weightx = 0.9;
		c.gridx = 1;
		c.insets = new Insets(defaultInset.top, 5, defaultInset.bottom, 5);
		intoThisComponent.add(slider, c);
		c.insets = defaultInset;
		c.gridheight = 1;
		c.weightx = 0.05;
		c.gridx = 2;
		c.insets = new Insets(defaultInset.top, 5, defaultInset.bottom, defaultInset.right);
		intoThisComponent.add(highBoundInformer, c);
		c.gridy = 1;
		intoThisComponent.add(lowBoundInformer, c);

		return new AdjustableBoundsSlider(slider,spinner,lowBoundInformer,highBoundInformer);
	}
}
