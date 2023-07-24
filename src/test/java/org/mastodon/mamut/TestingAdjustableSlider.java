package org.mastodon.mamut;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class TestingAdjustableSlider {
	public class AdjustableSliderControls
			implements KeyListener, MouseListener, MouseMotionListener {

		public int CONTROL_KEY_keycode = 17;
		public int MOUSE_BUTTON_code = 1;

		private final JSpinner minSpinner;
		private final SpinnerModel minModel;
		private final JSlider slider;
		private final JSpinner maxSpinner;
		private final SpinnerModel maxModel;

		public AdjustableSliderControls(final JSlider manageThisSlider,
		                                final JSpinner associatedMinSpinner,
		                                final JSpinner associatedMaxSpinner) {
			slider = manageThisSlider;
			minSpinner = associatedMinSpinner;
			maxSpinner = associatedMaxSpinner;

			//shortcuts
			minModel = minSpinner.getModel();
			maxModel = maxSpinner.getModel();

			//listeners setup: making the trio play together
			minModel.addChangeListener(l -> slider.setMinimum( (int)minModel.getValue() ));
			maxModel.addChangeListener(l -> slider.setMaximum( (int)maxModel.getValue() ));

			//listeners setup: managing slider's limits
			slider.addKeyListener(this);
			slider.addMouseListener(this);
			slider.addMouseMotionListener(this);

			slider.addChangeListener(l -> System.out.println("slider value "+slider.getValue()));
		}

		boolean isControlKeyPressed = false;
		boolean isMouseLBpressed = false;
		boolean isInControllingMode = false;
		int initialMousePosition = 0;
		int initialSliderValue = 0;
		boolean isLeftPartControlled = false;

		@Override
		public void keyPressed(KeyEvent keyEvent) {
			//isControlKeyPressed = keyEvent.getKeyCode() == CONTROL_KEY_keycode || isControlKeyPressed;
			if (keyEvent.getKeyCode() == CONTROL_KEY_keycode) {
				isControlKeyPressed = true;
				//System.out.println("CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
				if (isMouseLBpressed) isInControllingMode = true;
				System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
			}
		}
		@Override
		public void keyReleased(KeyEvent keyEvent) {
			//isControlKeyPressed = keyEvent.getKeyCode() != CONTROL_KEY_keycode && isControlKeyPressed;
			if (keyEvent.getKeyCode() == CONTROL_KEY_keycode) {
				isControlKeyPressed = false;
				//System.out.println("CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
				isInControllingMode = false;
				System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
			}
		}

		@Override
		public void mousePressed(MouseEvent mouseEvent) {
			//isMouseLBpressed = mouseEvent.getButton() == 1 || isMouseLBpressed;
			if (mouseEvent.getButton() == MOUSE_BUTTON_code) {
				isMouseLBpressed = true;
				//System.out.println("CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
				if (isControlKeyPressed) {
					isInControllingMode = true;
					System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode+", FOR PART: "+(isLeftPartControlled ? "LEFT":"RIGHT"));

					initialMousePosition = mouseEvent.getXOnScreen();
					isLeftPartControlled = ((float)mouseEvent.getX()/(float)slider.getWidth()) < 0.5f;
					initialSliderValue = isLeftPartControlled ? (int)minModel.getValue() : (int)maxModel.getValue();
				} else {
					System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
				}
			}
		}
		@Override
		public void mouseReleased(MouseEvent mouseEvent) {
			//isMouseLBpressed = mouseEvent.getButton() != 1 && isMouseLBpressed;
			if (mouseEvent.getButton() == MOUSE_BUTTON_code) {
				isMouseLBpressed = false;
				//System.out.println("CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
				isInControllingMode = false;
				System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
			}
		}

		@Override
		public void mouseDragged(MouseEvent mouseEvent) {
			if (isInControllingMode) {
				int deltaMove = mouseEvent.getXOnScreen() - initialMousePosition;
				System.out.println("CONTROLLING MODE: dragged a mouse by " + deltaMove);
				int newSliderValue = initialSliderValue + deltaMove;
				if (isLeftPartControlled) {
					//set min only if it is not beyond (greater than) the max boundary
					if (newSliderValue < (int)maxModel.getValue()) minModel.setValue( newSliderValue );
				} else {
					//right part
					//set max only if it is not beyond (lesser than) the min boundary
					if (newSliderValue > (int)minModel.getValue()) maxModel.setValue( newSliderValue );
				}
			}
		}

		@Override
		public void keyTyped(KeyEvent keyEvent) { /* intentionally empty */ }
		@Override
		public void mouseClicked(MouseEvent mouseEvent) { /* intentionally empty */ }
		@Override
		public void mouseEntered(MouseEvent mouseEvent) { /* intentionally empty */ }
		@Override
		public void mouseExited(MouseEvent mouseEvent) { /* intentionally empty */ }
		@Override
		public void mouseMoved(MouseEvent mouseEvent) { /* intentionally empty */ }
	}

	void demo() {
		JFrame frame = new JFrame("slider test");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final GridBagLayout gridBagLayout = new GridBagLayout();
		frame.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;


		JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 100, 33);
		SpinnerNumberModel minModel = new SpinnerNumberModel(
				slider.getMinimum(), slider.getMinimum(), slider.getMaximum(), 1);
		SpinnerNumberModel maxModel = new SpinnerNumberModel(
				slider.getMaximum(), slider.getMinimum(), slider.getMaximum(), 1);
		JSpinner minSpinner = new JSpinner(minModel);
		JSpinner maxSpinner = new JSpinner(maxModel);
		//
		c.gridy = 0;
		c.weightx = 0.1;
		c.gridx = 0;
		frame.add(minSpinner, c);
		c.weightx = 0.8;
		c.gridx = 1;
		frame.add(slider, c);
		c.weightx = 0.1;
		c.gridx = 2;
		frame.add(maxSpinner, c);

		AdjustableSliderControls ctrl = new AdjustableSliderControls(slider,minSpinner,maxSpinner);

		frame.pack();
		frame.setVisible(true);
	}


	public static void main(String[] args) {
		new TestingAdjustableSlider().demo();
	}
}