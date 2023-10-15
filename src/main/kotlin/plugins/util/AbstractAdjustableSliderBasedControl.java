package plugins.util;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.*;
import java.util.ArrayList;

public abstract class AbstractAdjustableSliderBasedControl {

	/** represents the Control key, which is changeable and shared among all such controls
	 * (to warrant all are controlled the same way) */
	public static int CONTROL_KEY_keycode = 17;
	/** represents the left mouse button, which is changeable and shared among all such controls
	 * (to warrant all are controlled the same way) */
	public static int MOUSE_BUTTON_code = 1;

	/** provide here own mouse movement to boundary change "scaler",
	 * this is intentionally available as of per-slider basis  */
	public BoundaryValuesProvider boundarySetter = BOUNDARY_SETTER_CUBE_FUN;

	// ================================= helper builders =================================
	public interface BoundaryValuesProvider {
		int boundaryDeltaOnThisMouseMove(final int mouseDeltaInPx);
	}
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
		return new SpinnerNumberModel(withThisCurrentValue, MIN_BOUND_LIMIT, MAX_BOUND_LIMIT, withThisStep);
	}

	// ================================= initialization =================================
	protected final JSlider slider;
	protected final JLabel lowBoundInfo;
	protected final JLabel highBoundInfo;

	//internal shortcuts
	static final int MIN_BOUND_LIMIT = 0;
	static final int MAX_BOUND_LIMIT = 65535;

	public AbstractAdjustableSliderBasedControl(final JSlider manageThisSlider,
	                                            final JSpinner associatedValueSpinner,
	                                            final JLabel associatedLowBound,
	                                            final JLabel associatedHighBound) {
		slider = manageThisSlider;
		lowBoundInfo = associatedLowBound;
		highBoundInfo = associatedHighBound;

		//add tooltip but only if there's none already
		if (slider.getToolTipText() == null) {
			slider.setToolTipText("Press and hold both CTRL and left-mouse-button while dragging the mouse horizontally to adjust sliding range.");
		}

		//listeners setup: setting the slider from the associated spinner
		final SpinnerModel m = associatedValueSpinner.getModel();
		if (! (m instanceof SpinnerNumberModel))
			throw new IllegalArgumentException("The provided spinner is expected to be of the type SpinnerNumberModel.");
		final SpinnerNumberModel nm = (SpinnerNumberModel)m; //NB: safe to cast...
		nm.addChangeListener(l -> {
			int value = (int)nm.getValue();
			value = Math.max(slider.getMinimum(), Math.min(value, slider.getMaximum()));
			slider.setValue(value);
			nm.setValue(value); //basically, assures that the spinner is also not outside the current bounds
		});

		//listeners setup: forwarder to the associated spinner and also
		//to client listeners (for which it triggers only on truly relevant slider changes)
		slider.addChangeListener(event -> {
			nm.setValue(slider.getValue());
			if (!isInControllingMode) tellListenersThatSliderHasChanged(event);
		});

		//listeners setup: managing slider's limits
		final EventHandler handler = new EventHandler();
		slider.addKeyListener(handler);
		slider.addMouseListener(handler);
		slider.addMouseMotionListener(handler);
	}

	public JSlider getSlider() {
		return slider;
	}

	/** only a shortcut to getSlider().getValue() */
	public int getValue() {
		return slider.getValue();
	}

	// ================================= execution: managing slider thumbs =================================
	//only for derived classes...
	protected int originalSliderValue = -1; //aka before-dragging-value

	protected void storeSliderThumbsPositions() {
		originalSliderValue = slider.getValue();
	}
	protected void fixupSliderThumbsPositions() {
		//make sure that the slider is not changing while adjusting its boundary,
		//which may not be always possible (as the boundary is allowed to move
		//irrespective of what the slider value was)
		if (originalSliderValue < slider.getMinimum()) slider.setValue(slider.getMinimum());
		else if (originalSliderValue > slider.getMaximum()) slider.setValue(slider.getMaximum());
		else slider.setValue(originalSliderValue);
	}
	protected boolean didSliderThumbsChangedPositions() {
		return slider.getValue() != originalSliderValue;
	}

	// ================================= execution: internal state =================================
	private boolean isControlKeyPressed = false;
	private boolean isMouseLBpressed = false;
	private boolean isInControllingMode = false;
	private int initialMousePosition = 0;
	private int initialBoundaryValue = 0;
	private boolean isMinBoundaryControlled = false;

	//meant originally for derived classes...
	public boolean isInControllingMode() {
		return isInControllingMode;
	}

	// ================================= execution: events handling =================================
	protected class EventHandler
	implements KeyListener, MouseListener, MouseMotionListener {
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
					initialBoundaryValue = isMinBoundaryControlled ? slider.getMinimum() : slider.getMaximum();
					storeSliderThumbsPositions();
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
					//make sure the value is within the min model limits,
					newSliderValue = Math.max(MIN_BOUND_LIMIT, Math.min(newSliderValue, MAX_BOUND_LIMIT));
					//and set min only if it is not beyond (greater than) the max boundary
					if (newSliderValue < slider.getMaximum()) {
						slider.setMinimum(newSliderValue);
						lowBoundInfo.setText(String.valueOf(newSliderValue));
					}
				} else {
					//right part
					//make sure the value is within the max model limits,
					newSliderValue = Math.max(MIN_BOUND_LIMIT, Math.min(newSliderValue, MAX_BOUND_LIMIT));
					//and set max only if it is not beyond (lesser than) the min boundary
					if (newSliderValue > slider.getMinimum()) {
						slider.setMaximum(newSliderValue);
						highBoundInfo.setText(String.valueOf(newSliderValue));
					}
				}
				fixupSliderThumbsPositions();
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
	protected final java.util.List<ChangeListener> listeners = new ArrayList<>(10);

	public void addChangeListener(final ChangeListener listener) {
		listeners.add(listener);
	}

	public void removeChangeListener(final ChangeListener listener) {
		listeners.remove(listener);
	}

	protected void tellListenersThatSliderHasChanged(final ChangeEvent event) {
		listeners.forEach(listener -> listener.stateChanged(event));
	}

	protected void tellListenersThatWeEndedAdjustingMode() {
		//...but only when we really have changed the value before and after the adjustment
		if (didSliderThumbsChangedPositions()) {
			tellListenersThatSliderHasChanged(new ChangeEvent(slider));
		}
	}
}
