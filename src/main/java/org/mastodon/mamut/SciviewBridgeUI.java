//TODO add license text
package org.mastodon.mamut;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SciviewBridgeUI {
	SciviewBridge controlledBridge;
	final Container contentPane;

	public SciviewBridgeUI(final SciviewBridge controlledBridge, final Container populateThisContainer) {
		this.controlledBridge = controlledBridge;
		this.contentPane = populateThisContainer;
		populatePane();
		updatePaneValues();
	}

	public Container getControlsWindowPanel() {
		return contentPane;
	}

	public SciviewBridge getControlledBridge() {
		return controlledBridge;
	}

	// -------------------------------------------------------------------------------------------
	void populatePane() {
		final GridBagLayout gridBagLayout = new GridBagLayout();
		contentPane.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridx = 0;
		c.gridy = 0;
		UPDATE_VOLUME_AUTOMATICALLY = new JCheckBox("Hint text");
		contentPane.add(UPDATE_VOLUME_AUTOMATICALLY, c);

		c.gridy++;
		INTENSITY_CONTRAST = new SpinnerNumberModel(1.0, 0.0, 100.0, 0.5);
		new JSpinner(INTENSITY_CONTRAST);
	}

	/**
	 * Disable all listeners to make sure that, even if this UI window would ever
	 * be re-displayed, its controls could not control anything (and would throw
	 * NPEs if the controls would actually be used).
	 */
	public void deactivateAndForget() {
		//listeners tear-down here
		this.controlledBridge = null;
	}

	public void updatePaneValues() {
		UPDATE_VOLUME_AUTOMATICALLY.setEnabled( controlledBridge.UPDATE_VOLUME_AUTOMATICALLY );
	}

	//int SOURCE_ID = 0;
	//int SOURCE_USED_RES_LEVEL = 0;
	SpinnerModel INTENSITY_CONTRAST;
	final float INTENSITY_NOT_ABOVE = 700;   //...then clamped not to be above this value and...
	final float INTENSITY_GAMMA = 1.0f;      //...then gamma-corrected;

	boolean INTENSITY_OF_COLORS_APPLY = true;//flag to enable/disable imprinting, with details just below:
	final float SPOT_RADIUS_SCALE = 3.0f;    //the spreadColor() imprints spot this much larger than what it is in Mastodon
	final float INTENSITY_OF_COLORS = 2100;  //and this max allowed value is used for the imprinting...

	final float INTENSITY_RANGE_MAX = 2110;  //...because it plays nicely with this scaling range
	final float INTENSITY_RANGE_MIN = 0;
	private JCheckBox UPDATE_VOLUME_AUTOMATICALLY;
	private JCheckBox UPDATE_VOLUME_VERBOSE_REPORTS;
}