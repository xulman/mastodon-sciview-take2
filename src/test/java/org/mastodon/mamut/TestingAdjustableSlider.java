package org.mastodon.mamut;

import org.mastodon.mamut.util.AdjustableSliderControls;

import javax.swing.*;
import java.awt.*;

public class TestingAdjustableSlider {

	void demo() {
		JFrame frame = new JFrame("slider test");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final GridBagLayout gridBagLayout = new GridBagLayout();
		frame.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;


		//set to the current wanted range
		JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 100, 33);

		//set limits to the data type (here, GRAY16)
		SpinnerNumberModel minModel = new SpinnerNumberModel(slider.getMinimum(), 0,65535, 50);
		SpinnerNumberModel maxModel = new SpinnerNumberModel(slider.getMaximum(), 0,65535, 50);
		JSpinner minSpinner = new JSpinner(minModel);
		JSpinner maxSpinner = new JSpinner(maxModel);
		//
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

		AdjustableSliderControls ctrl = new AdjustableSliderControls(slider,minSpinner,maxSpinner);

		JLabel msg = new JLabel("Current slider value: "+slider.getValue());
		c.gridx=1;
		c.gridy=1;
		frame.add(msg, c);
		ctrl.addChangeListener(l -> {
			msg.setText("Current slider value: "+slider.getValue());
			System.out.print('.');
		});

		frame.pack();
		frame.setVisible(true);
	}


	public static void main(String[] args) {
		new TestingAdjustableSlider().demo();
	}
}