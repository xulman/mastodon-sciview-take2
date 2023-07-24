package org.mastodon.mamut;

import bdv.ui.rangeslider.RangeSlider;
import org.mastodon.mamut.util.AdjustableSliderControls;

import javax.swing.*;
import java.awt.*;

public class TestingAdjustableRange {
	public static void main(String[] args) {
		JFrame frame = new JFrame("slider test");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final GridBagLayout gridBagLayout = new GridBagLayout();
		frame.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		//set to the current wanted range
		//JSlider slider = new JSlider(JSlider.HORIZONTAL, initialMin, initialMax, initialValue);
		RangeSlider slider = new RangeSlider(0,500);
		slider.setValue(30);
		slider.setUpperValue(170);

		JSpinner minSpinner = new JSpinner(
				AdjustableSliderControls.createAppropriateSpinnerModel( slider.getMinimum() ));
		JSpinner maxSpinner = new JSpinner(
				AdjustableSliderControls.createAppropriateSpinnerModel( slider.getMaximum() ));

		c.gridy = 0;
		c.weightx = 0.05;
		c.gridx = 0;
		frame.add(minSpinner, c);
		c.weightx = 0.9;
		c.gridx = 1;
		frame.add(slider, c);
		c.weightx = 0.05;
		c.gridx = 2;
		frame.add(maxSpinner, c);

		AdjustableSliderControls ctrl = new AdjustableSliderControls(slider, minSpinner, maxSpinner);

		JLabel msg = new JLabel("Values are "+slider.getValue()+" and "+slider.getUpperValue());
		ctrl.addChangeListener(l -> msg.setText("Values are "+slider.getValue()+" and "+slider.getUpperValue()) );
		//
		c.gridy = 1;
		c.gridx = 0;
		c.gridwidth = 3;
		c.weightx = 0.2;
		frame.add(msg, c);

		frame.pack();
		frame.setVisible(true);
	}
}