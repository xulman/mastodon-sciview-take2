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
		private final JSlider slider;
		private final JSpinner maxSpinner;

		//internal shortcuts
		private final SpinnerNumberModel minModel;
		private final SpinnerNumberModel maxModel;
		private final Comparable<Integer> minModel_MinBound;
		private final Comparable<Integer> minModel_MaxBound;
		private final Comparable<Integer> maxModel_MinBound;
		private final Comparable<Integer> maxModel_MaxBound;

		public AdjustableSliderControls(final JSlider manageThisSlider,
		                                final JSpinner associatedMinSpinner,
		                                final JSpinner associatedMaxSpinner) {
			slider = manageThisSlider;
			minSpinner = associatedMinSpinner;
			maxSpinner = associatedMaxSpinner;

			//shortcuts
			minModel = (SpinnerNumberModel)minSpinner.getModel();
			maxModel = (SpinnerNumberModel)maxSpinner.getModel();
			minModel_MinBound = (Comparable<Integer>)minModel.getMinimum();
			minModel_MaxBound = (Comparable<Integer>)minModel.getMaximum();
			maxModel_MinBound = (Comparable<Integer>)maxModel.getMinimum();
			maxModel_MaxBound = (Comparable<Integer>)maxModel.getMaximum();

			//listeners setup: making the trio play together
			minModel.addChangeListener(l -> slider.setMinimum( (int)minModel.getValue() ));
			maxModel.addChangeListener(l -> slider.setMaximum( (int)maxModel.getValue() ));
			//note that the min/max spinners should not change their limits (as
			//they correspond to the displayed data type, whose value range is firm)

			//listeners setup: managing slider's limits
			slider.addKeyListener(this);
			slider.addMouseListener(this);
			slider.addMouseMotionListener(this);
		}

		boolean isControlKeyPressed = false;
		boolean isMouseLBpressed = false;
		boolean isInControllingMode = false;
		int initialMousePosition = 0;
		int initialBoundaryValue = 0;
		boolean isMinBoundaryControlled = false;

		@Override
		public void keyPressed(KeyEvent keyEvent) {
			//isControlKeyPressed = keyEvent.getKeyCode() == CONTROL_KEY_keycode || isControlKeyPressed;
			if (keyEvent.getKeyCode() == CONTROL_KEY_keycode) {
				isControlKeyPressed = true;
				//System.out.println("CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
				if (isMouseLBpressed) isInControllingMode = true;
				//System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
			}
		}
		@Override
		public void keyReleased(KeyEvent keyEvent) {
			//isControlKeyPressed = keyEvent.getKeyCode() != CONTROL_KEY_keycode && isControlKeyPressed;
			if (keyEvent.getKeyCode() == CONTROL_KEY_keycode) {
				isControlKeyPressed = false;
				//System.out.println("CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
				isInControllingMode = false;
				//System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
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
					//System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode+", FOR PART: "+(isLeftPartControlled ? "LEFT":"RIGHT"));

					initialMousePosition = mouseEvent.getXOnScreen();
					isMinBoundaryControlled = ((float)mouseEvent.getX()/(float)slider.getWidth()) < 0.5f;
					initialBoundaryValue = isMinBoundaryControlled ? (int)minModel.getValue() : (int)maxModel.getValue();
				}
				/* else {
					System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
				} */
			}
		}
		@Override
		public void mouseReleased(MouseEvent mouseEvent) {
			//isMouseLBpressed = mouseEvent.getButton() != 1 && isMouseLBpressed;
			if (mouseEvent.getButton() == MOUSE_BUTTON_code) {
				isMouseLBpressed = false;
				//System.out.println("CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
				isInControllingMode = false;
				//System.out.println("CONTROLLING MODE STATUS: "+isInControllingMode);
			}
		}

		@Override
		public void mouseDragged(MouseEvent mouseEvent) {
			if (isInControllingMode) {
				int deltaMove = mouseEvent.getXOnScreen() - initialMousePosition;
				//System.out.println("CONTROLLING MODE: dragged a mouse by " + deltaMove);
				int newSliderValue = initialBoundaryValue + deltaMove;
				if (isMinBoundaryControlled) {
					//set value only if it is within the min model limits, and
					//set min only if it is not beyond (greater than) the max boundary
					if (minModel_MinBound.compareTo(newSliderValue) <= 0
						&& minModel_MaxBound.compareTo(newSliderValue) >= 0
						&& newSliderValue < (int)maxModel.getValue()) minModel.setValue( newSliderValue );
				} else {
					//right part
					//set value only if it is within the max model limits, and
					//set max only if it is not beyond (lesser than) the min boundary
					if (maxModel_MinBound.compareTo(newSliderValue) <= 0
						&& maxModel_MaxBound.compareTo(newSliderValue) >= 0
						&& newSliderValue > (int)minModel.getValue()) maxModel.setValue( newSliderValue );
				}
			}
		}

		@Override
		public void mouseEntered(MouseEvent mouseEvent) {
			//during the mouse dragging, we might have gotten out of the slider area;
			//when outside, the keyboard and mouse buttons change might have changed but
			//this object is now aware of it (as its listeners couldn't be triggered);
			//
			//now, when the mouse pointer is coming back, we have to reset the statuses
			isControlKeyPressed = (mouseEvent.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) > 0;
			isMouseLBpressed = (mouseEvent.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) > 0;
			isInControllingMode = isControlKeyPressed && isMouseLBpressed;
			//System.out.println("EVERYTHING IS RESET: CTRL status: "+isControlKeyPressed+",  MOUSE status: "+isMouseLBpressed);
		}

		@Override
		public void keyTyped(KeyEvent keyEvent) { /* intentionally empty */ }
		@Override
		public void mouseClicked(MouseEvent mouseEvent) { /* intentionally empty */ }
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
		slider.addChangeListener(l -> {
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