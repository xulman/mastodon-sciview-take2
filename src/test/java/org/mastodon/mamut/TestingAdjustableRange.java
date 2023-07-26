package org.mastodon.mamut;

import bdv.ui.rangeslider.RangeSlider;
import org.mastodon.mamut.util.AbstractAdjustableSliderBasedControl;
import org.mastodon.mamut.util.AdjustableBoundsRangeSlider;

import javax.swing.*;
import java.awt.*;

public class TestingAdjustableRange {
	public static final int MINBOUND = 0;
	public static final int MAXBOUND = 500;
	public static final int LOWVALUE = 30;
	public static final int HIGHVALUE = 170;

	public static void ownLayoutOfControls() {
		JFrame frame = new JFrame("range slider test - own layout");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final GridBagLayout gridBagLayout = new GridBagLayout();
		frame.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		//set to the current wanted range
		RangeSlider slider = new RangeSlider(MINBOUND,MAXBOUND);
		slider.setValue(LOWVALUE);
		slider.setUpperValue(HIGHVALUE);

		JSpinner lowValueSpinner = new JSpinner(
				AbstractAdjustableSliderBasedControl.createAppropriateSpinnerModel( slider.getValue() ));
		JSpinner highValueSpinner = new JSpinner(
				AbstractAdjustableSliderBasedControl.createAppropriateSpinnerModel( slider.getUpperValue() ));

		JLabel minBoundInfo = new JLabel(String.valueOf( slider.getMinimum() ));
		JLabel maxBoundInfo = new JLabel(String.valueOf( slider.getMaximum() ));

		c.insets = new Insets(5,10,5,10);
		c.gridy = 0;
		c.weightx = 0.05;
		c.gridx = 0;
		frame.add(minBoundInfo, c);
		c.weightx = 0.1;
		c.gridx = 1;
		frame.add(lowValueSpinner, c);
		c.weightx = 0.7;
		c.gridx = 2;
		frame.add(new JLabel("  <------------>  "), c);
		c.weightx = 0.1;
		c.gridx = 3;
		frame.add(highValueSpinner, c);
		c.weightx = 0.05;
		c.gridx = 4;
		frame.add(maxBoundInfo, c);

		c.gridy = 1;
		c.weightx = 0.9;
		c.gridx = 0;
		c.gridwidth = 5;
		frame.add(slider, c);

		AdjustableBoundsRangeSlider ctrl = new AdjustableBoundsRangeSlider(slider,
				lowValueSpinner, highValueSpinner,
				minBoundInfo, maxBoundInfo );

		JLabel msg = new JLabel("Values are "+slider.getValue()+" and "+slider.getUpperValue());
		ctrl.addChangeListener(l -> msg.setText("Values are "+slider.getValue()+" and "+slider.getUpperValue()) );
		//
		c.gridy = 2;
		c.gridx = 0;
		c.gridwidth = 3;
		c.weightx = 0.2;
		frame.add(msg, c);

		frame.pack();
		frame.setVisible(true);
	}

	public static void defaultLayoutOfControls() {
		JFrame frame = new JFrame("range slider test - default layout");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final GridBagLayout gridBagLayout = new GridBagLayout();
		frame.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 0.1;

		c.insets = new Insets(5,10,5,10);
		c.gridy = 0;
		c.gridx = 0;
		JPanel placeHolder = new JPanel();
		frame.add(placeHolder, c);

		AdjustableBoundsRangeSlider ctrl = AdjustableBoundsRangeSlider
				.createAndPlaceHere(placeHolder, LOWVALUE,HIGHVALUE, MINBOUND,MAXBOUND);

		c.gridy = 1;
		c.gridx = 0;
		JLabel msg = new JLabel("Values are "+ctrl.getValue()+" and "+ctrl.getUpperValue());
		ctrl.addChangeListener(l -> msg.setText("Values are "+ctrl.getValue()+" and "+ctrl.getUpperValue()) );
		frame.add(msg, c);

		frame.pack();
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		ownLayoutOfControls();
		defaultLayoutOfControls();
	}
}