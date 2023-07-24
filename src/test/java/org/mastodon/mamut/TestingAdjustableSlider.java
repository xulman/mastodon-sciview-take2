package org.mastodon.mamut;

import org.mastodon.mamut.util.AdjustableSliderControls;
import javax.swing.*;
import java.awt.*;

public class TestingAdjustableSlider {
	public static void main(String[] args) {
		JFrame frame = new JFrame("slider test");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final GridBagLayout gridBagLayout = new GridBagLayout();
		frame.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.weighty = 0.3;
		c.fill = GridBagConstraints.BOTH;

		//row
		c.gridy = 0;
		c.gridx = 0;
		c.weightx = 0.1;
		frame.add( new JButton("no op"), c);
		c.gridx = 1;
		c.weightx = 0.8;
		frame.add( new JButton("no op"), c);
		c.gridx = 2;
		c.weightx = 0.1;
		frame.add( new JButton("no op"), c);

		//row
		c.weightx = 0;
		c.gridy = 1;
		c.gridx = 0;
		frame.add( new JButton("no op"), c);
		//x=1 is missing for now
		c.gridx = 2;
		frame.add( new JButton("no op"), c);

		//row
		c.gridy = 2;
		c.gridx = 0;
		frame.add( new JButton("no op"), c);
		//x=1 is missing for now
		c.gridx = 2;
		frame.add( new JButton("no op"), c);

		final JPanel placeHolder = new JPanel();
		c.gridy = 1;
		c.gridx = 1;
		frame.add(placeHolder, c);
		//
		AdjustableSliderControls slider
				= AdjustableSliderControls.createAndPlaceHere(placeHolder, 33, 0,100);

		final JLabel msg = new JLabel("Current slider value: "+slider.getValue());
		c.gridy = 2;
		c.gridx = 1;
		frame.add(msg, c);
		//
		slider.addChangeListener(l -> {
			msg.setText("Current slider value: "+slider.getValue());
			System.out.print('.');
		});

		frame.pack();
		frame.setVisible(true);
	}
}