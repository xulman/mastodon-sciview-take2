package org.mastodon.mamut.util;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;

public class AdjustableSliderControls {

	/** represents the Control key, changeable, shared among all such controls */
	public static int CONTROL_KEY_keycode = 17;
	/** represents the left mouse button, changeable, shared among all such controls */
	public static int MOUSE_BUTTON_code = 1;

	public interface BoundaryValuesProvider {
		int boundaryDeltaOnThisMouseMove(final int mouseDeltaInPx);
	}

	/** provide here own mouse movement to boundary change "scaler",
	 * this is intentionally available as of per-slider basis  */
	public BoundaryValuesProvider boundarySetter = BOUNDARY_SETTER_CUBE_FUN;

	// ================================= helper builders =================================
	public final static BoundaryValuesProvider BOUNDARY_SETTER_IDENTITY_FUN = mouseDeltaInPx -> mouseDeltaInPx;
	public final static BoundaryValuesProvider BOUNDARY_SETTER_SQUARE_FUN = mouseDeltaInPx -> {
		float d = (float)mouseDeltaInPx/4.f;
		d *= d;
		return mouseDeltaInPx > 0 ? (int)d : (int)-d;
	};
	public final static BoundaryValuesProvider BOUNDARY_SETTER_CUBE_FUN = mouseDeltaInPx -> {
		float d = (float)mouseDeltaInPx/15.f;
		d *= d*d;
		return (int)d;
	};

	public static SpinnerNumberModel createAppropriateSpinnerModel(int withThisCurrentValue) {
		return createAppropriateSpinnerModel(withThisCurrentValue, 50);
	}
	public static SpinnerNumberModel createAppropriateSpinnerModel(int withThisCurrentValue,
	                                                               int withThisStep) {
		return new SpinnerNumberModel(withThisCurrentValue, 0,65535, withThisStep);
	}

	public static AdjustableSliderControls createAndPlaceHere(final Container intoThisComponent,
	                                                          final int initialValue,
	                                                          final int initialMin,
	                                                          final int initialMax) {
		final GridBagLayout gridBagLayout = new GridBagLayout();
		intoThisComponent.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		//set to the current wanted range
		JSlider slider = new JSlider(JSlider.HORIZONTAL, initialMin, initialMax, initialValue);

		JSpinner minSpinner = new JSpinner(
				AdjustableSliderControls.createAppropriateSpinnerModel( slider.getMinimum() ));
		JSpinner maxSpinner = new JSpinner(
				AdjustableSliderControls.createAppropriateSpinnerModel( slider.getMaximum() ));

		c.gridy = 0;
		c.weightx = 0.05;
		c.gridx = 0;
		intoThisComponent.add(minSpinner, c);
		c.weightx = 0.9;
		c.gridx = 1;
		intoThisComponent.add(slider, c);
		c.weightx = 0.05;
		c.gridx = 2;
		intoThisComponent.add(maxSpinner, c);

		return new AdjustableSliderControls(slider,minSpinner,maxSpinner);
	}


	// ================================= stuff for the initialization =================================
	private final JSlider slider;

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

		SpinnerModel m = associatedMinSpinner.getModel();
		if (! (m instanceof SpinnerNumberModel))
			throw new IllegalArgumentException("The min-boundary spinner is expected to be SpinnerNumberModel.");
		minModel = (SpinnerNumberModel)m;

		m = associatedMaxSpinner.getModel();
		if (! (m instanceof SpinnerNumberModel))
			throw new IllegalArgumentException("The max-boundary spinner is expected to be SpinnerNumberModel.");
		maxModel = (SpinnerNumberModel) associatedMaxSpinner.getModel();

		//shortcuts
		minModel_MinBound = (Comparable<Integer>) minModel.getMinimum();
		minModel_MaxBound = (Comparable<Integer>) minModel.getMaximum();
		maxModel_MinBound = (Comparable<Integer>) maxModel.getMinimum();
		maxModel_MaxBound = (Comparable<Integer>) maxModel.getMaximum();

		//listeners setup: making the trio play together
		minModel.addChangeListener(l -> slider.setMinimum((int) minModel.getValue()));
		maxModel.addChangeListener(l -> slider.setMaximum((int) maxModel.getValue()));
		//note that the min/max spinners should not change their limits (as
		//they correspond to the displayed data type, whose value range is firm)

		//listeners setup: managing slider's limits
		final EventHandler handler = new EventHandler();
		slider.addKeyListener(handler);
		slider.addMouseListener(handler);
		slider.addMouseMotionListener(handler);

		//listeners setup: forwarder to the client listeners
		//(triggers only on truly relevant slider changes)
		slider.addChangeListener(event -> {
			if (!isInControllingMode) tellListenersThatSliderHasChanged(event);
		});
	}

	public JSlider getSlider() {
		return slider;
	}

	/** only a shortcut to getSlider().getValue() */
	public int getValue() {
		return slider.getValue();
	}

	// ================================= stuff for the execution =================================
	private boolean isControlKeyPressed = false;
	private boolean isMouseLBpressed = false;
	private boolean isInControllingMode = false;
	private int initialMousePosition = 0;
	private int initialBoundaryValue = 0;
	private int originalSliderValue = -1;
	private boolean isMinBoundaryControlled = false;

	// ================================= execution: events handling =================================
	private class EventHandler implements KeyListener, MouseListener, MouseMotionListener {
		@Override
		public void keyPressed(KeyEvent keyEvent) {
			if (keyEvent.getKeyCode() == CONTROL_KEY_keycode) {
				isControlKeyPressed = true;
				if (isMouseLBpressed) isInControllingMode = true;
			}
		}

		@Override
		public void keyReleased(KeyEvent keyEvent) {
			if (keyEvent.getKeyCode() == CONTROL_KEY_keycode) {
				isControlKeyPressed = false;
				if (isInControllingMode) tellListenersThatWeEndedAdjustingMode();
				isInControllingMode = false;
			}
		}

		@Override
		public void mousePressed(MouseEvent mouseEvent) {
			if (mouseEvent.getButton() == MOUSE_BUTTON_code) {
				isMouseLBpressed = true;
				if (isControlKeyPressed) {
					isInControllingMode = true;

					//store the initial state now--at the beginning of the dragging
					initialMousePosition = mouseEvent.getXOnScreen();
					isMinBoundaryControlled = ((float) mouseEvent.getX() / (float) slider.getWidth()) < 0.5f;
					initialBoundaryValue = isMinBoundaryControlled ? (int) minModel.getValue() : (int) maxModel.getValue();
					originalSliderValue = slider.getValue();
				}
			}
		}

		@Override
		public void mouseReleased(MouseEvent mouseEvent) {
			if (mouseEvent.getButton() == MOUSE_BUTTON_code) {
				isMouseLBpressed = false;
				if (isInControllingMode) tellListenersThatWeEndedAdjustingMode();
				isInControllingMode = false;
			}
		}

		@Override
		public void mouseDragged(MouseEvent mouseEvent) {
			if (isInControllingMode) {
				int deltaMove = mouseEvent.getXOnScreen() - initialMousePosition;
				int newSliderValue = initialBoundaryValue + boundarySetter.boundaryDeltaOnThisMouseMove(deltaMove);
				if (isMinBoundaryControlled) {
					//set value only if it is within the min model limits, and
					//set min only if it is not beyond (greater than) the max boundary
					if (minModel_MinBound.compareTo(newSliderValue) <= 0
							&& minModel_MaxBound.compareTo(newSliderValue) >= 0
							&& newSliderValue < (int) maxModel.getValue()) minModel.setValue(newSliderValue);
				} else {
					//right part
					//set value only if it is within the max model limits, and
					//set max only if it is not beyond (lesser than) the min boundary
					if (maxModel_MinBound.compareTo(newSliderValue) <= 0
							&& maxModel_MaxBound.compareTo(newSliderValue) >= 0
							&& newSliderValue > (int) minModel.getValue()) maxModel.setValue(newSliderValue);
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
			boolean wasInControllingMode = isInControllingMode;
			isInControllingMode = isControlKeyPressed && isMouseLBpressed;
			if (wasInControllingMode && !isInControllingMode) tellListenersThatWeEndedAdjustingMode();
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

	// ================================= execution: listeners =================================
	private final java.util.List<ChangeListener> listeners = new ArrayList<>(10);

	public void addChangeListener(final ChangeListener listener) {
		listeners.add(listener);
	}

	public void removeChangeListener(final ChangeListener listener) {
		listeners.remove(listener);
	}

	private void tellListenersThatSliderHasChanged(final ChangeEvent event) {
		listeners.forEach(listener -> listener.stateChanged(event));
	}

	private void tellListenersThatWeEndedAdjustingMode() {
		//...but only when we really have changed the value before and after the adjustment
		if (slider.getValue() != originalSliderValue) {
			tellListenersThatSliderHasChanged(new ChangeEvent(slider));
		}
	}
}
