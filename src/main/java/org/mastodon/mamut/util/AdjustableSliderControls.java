package org.mastodon.mamut.util;

import bdv.ui.rangeslider.RangeSlider;

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
		return new SpinnerNumberModel(withThisCurrentValue, minBound_lowLimit,minBound_highLimit, withThisStep);
	}

	public static AdjustableSliderControls createAndPlaceHere(final Container intoThisComponent,
	                                                          final int initialValue,
	                                                          final int initialMin,
	                                                          final int initialMax) {
		if (initialValue < initialMin || initialValue > initialMax)
			throw new IllegalArgumentException("Refuse to create slider showing value that's outside the slider's min and max range.");
		if (initialMin < minBound_lowLimit || initialMin > minBound_highLimit)
			throw new IllegalArgumentException("Required MIN bound is outside the interval assumed by this governing class.");
		if (initialMax < maxBound_lowLimit || initialMax > maxBound_highLimit)
			throw new IllegalArgumentException("Required MAX bound is outside the interval assumed by this governing class.");

		final GridBagLayout gridBagLayout = new GridBagLayout();
		intoThisComponent.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		//set to the current wanted range
		JSlider slider = new JSlider(JSlider.HORIZONTAL, initialMin, initialMax, initialValue);
		JSpinner spinner = new JSpinner( AdjustableSliderControls.createAppropriateSpinnerModel(initialValue) );
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
		intoThisComponent.add(slider, c);
		c.gridheight = 1;
		c.weightx = 0.05;
		c.gridx = 2;
		intoThisComponent.add(highBoundInformer, c);
		c.gridy = 1;
		intoThisComponent.add(lowBoundInformer, c);

		return new AdjustableSliderControls(slider,spinner,lowBoundInformer,highBoundInformer);
	}

	public static AdjustableSliderControls createAndPlaceHere(final Container intoThisComponent,
	                                                          final int initialLowValue,
	                                                          final int initialHighValue,
	                                                          final int initialMin,
	                                                          final int initialMax) {
		final GridBagLayout gridBagLayout = new GridBagLayout();
		intoThisComponent.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		//set to the current wanted range
		RangeSlider slider = new RangeSlider(initialMin, initialMax);
		slider.setRange(initialLowValue, initialHighValue);

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

		//return new AdjustableSliderControls(slider,minSpinner,maxSpinner);
		return null;
	}

	// ================================= stuff for the initialization =================================
	private final JSlider slider;
	private final JLabel lowBoundInfo;
	private final JLabel highBoundInfo;

	//internal shortcuts
	private int currentMinBound;
	private int currentMaxBound;
	private static final int minBound_lowLimit = 0;
	private static final int minBound_highLimit = 65535;
	private static final int maxBound_lowLimit = 0;
	private static final int maxBound_highLimit = 65535;
	//TODO: is there any reason to have different intervals for what min and max bounds can be?

	public AdjustableSliderControls(final JSlider manageThisSlider,
	                                final JSpinner associatedValueSpinner,
	                                final JLabel associatedLowBound,
	                                final JLabel associatedHighBound) {
		slider = manageThisSlider;
		currentMinBound = slider.getMinimum();
		currentMaxBound = slider.getMaximum();
		lowBoundInfo = associatedLowBound;
		highBoundInfo = associatedHighBound;

		//listeners setup: setting the slider from the associated spinner
		final SpinnerModel m = associatedValueSpinner.getModel();
		if (! (m instanceof SpinnerNumberModel))
			throw new IllegalArgumentException("The provided spinner is expected to be of the type SpinnerNumberModel.");
		final SpinnerNumberModel nm = (SpinnerNumberModel)m; //NB: safe to cast...
		nm.addChangeListener(l -> {
			int value = (int)nm.getValue();
			value = Math.max(currentMinBound, Math.min(value, currentMaxBound));
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

	// ================================= stuff for the execution =================================
	private boolean isControlKeyPressed = false;
	private boolean isMouseLBpressed = false;
	private boolean isInControllingMode = false;
	private int initialMousePosition = 0;
	private int initialBoundaryValue = 0;
	private int originalSliderValue = -1; //aka before-dragging-value
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
					initialBoundaryValue = isMinBoundaryControlled ? currentMinBound : currentMaxBound;
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
					//make sure the value is within the min model limits,
					newSliderValue = Math.max(minBound_lowLimit, Math.min(newSliderValue, minBound_highLimit));
					//and set min only if it is not beyond (greater than) the max boundary
					if (newSliderValue < currentMaxBound) {
						currentMinBound = newSliderValue;
						slider.setMinimum(currentMinBound);
						lowBoundInfo.setText(String.valueOf(currentMinBound));
					}
				} else {
					//right part
					//make sure the value is within the max model limits,
					newSliderValue = Math.max(maxBound_lowLimit, Math.min(newSliderValue, maxBound_highLimit));
					//and set max only if it is not beyond (lesser than) the min boundary
					if (newSliderValue > currentMinBound) {
						currentMaxBound = newSliderValue;
						slider.setMaximum(currentMaxBound);
						highBoundInfo.setText(String.valueOf(currentMaxBound));
					}
				}
				//make sure that the slider is not changing while adjusting its boundary,
				//which may not be always possible (as the boundary is allowed to move
				//irrespective of what the slider value was)
				if (originalSliderValue < currentMinBound) slider.setValue(currentMinBound);
				else if (originalSliderValue > currentMaxBound) slider.setValue(currentMaxBound);
				else slider.setValue(originalSliderValue);
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
