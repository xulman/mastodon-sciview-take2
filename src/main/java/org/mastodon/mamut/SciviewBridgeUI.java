//TODO add license text
package org.mastodon.mamut;

import org.mastodon.mamut.util.AdjustableBoundsRangeSlider;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.function.Consumer;

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
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridy = 0;
		c.gridwidth = 2;
		c.weightx = 0.1;
		c.insets = new Insets(sideSpace,sideSpace,2,sideSpace);
		contentPane.add( new JLabel("Volume pixel values 'v' are processed linearly, normalized, gamma, scaled back:"), c);
		c.insets = new Insets(2,sideSpace,2,sideSpace);
		c.gridwidth = 1;

		c.gridy++;
		insertNote("   pow( min(contrast*v +shift, not_above)/not_above, gamma ) *not_above", c);

		c.gridy++;
		c.gridx = 0;
		insertLabel("Apply on Volume this contrast factor:", c);
		//
		c.gridx = 1;
		INTENSITY_CONTRAST = new SpinnerNumberModel(1.0, -100.0, 100.0, 0.5);
		insertSpinner(INTENSITY_CONTRAST, (f) -> controlledBridge.INTENSITY_CONTRAST = f, c);

		c.gridy++;
		c.gridx = 0;
		insertLabel("Apply on Volume this shifting bias:", c);
		//
		c.gridx = 1;
		INTENSITY_SHIFT = new SpinnerNumberModel(0.0, -65535.0, 65535.0, 50);
		insertSpinner(INTENSITY_SHIFT, (f) -> controlledBridge.INTENSITY_SHIFT = f, c);

		c.gridy++;
		c.gridx = 0;
		insertLabel("Apply on Volume this gamma level:", c);
		//
		c.gridx = 1;
		INTENSITY_GAMMA = new SpinnerNumberModel(1.0, 0.1, 3.0, 0.1);
		insertSpinner(INTENSITY_GAMMA, (f) -> controlledBridge.INTENSITY_GAMMA = f, c);

		c.gridy++;
		c.gridx = 0;
		insertLabel("Set to zero any voxel whose value would have been above:", c);
		//
		c.gridx = 1;
		INTENSITY_CLAMP_AT_TOP = new SpinnerNumberModel(700.0, 0.0, 65535.0, 50.0);
		insertSpinner(INTENSITY_CLAMP_AT_TOP, (f) -> controlledBridge.INTENSITY_CLAMP_AT_TOP = f, c);

		// -------------- separator --------------
		c.gridy++;
		insertSeparator(c);

		c.gridy++;
		insertNote("Shortcuts to the standard sciview view controls, incl. Volume intensity mapping", c);

		c.gridy++;
		JPanel sliderBarPlaceHolder = new JPanel();
		c.gridx = 0;
		c.gridwidth = 2;
		contentPane.add(sliderBarPlaceHolder, c);
		//
		INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM = AdjustableBoundsRangeSlider.createAndPlaceHere(
				sliderBarPlaceHolder,
				(int)controlledBridge.INTENSITY_RANGE_MIN, (int)controlledBridge.INTENSITY_RANGE_MAX,
				0, 10000);
		INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.addChangeListener(rangeSliderListener);

		c.gridy++;
		visToggleSpots = new JButton("Toggle visibility of SPOTS");
		visToggleSpots.addActionListener(toggleSpotsVisibility);
		visToggleVols = new JButton("Toggle visibility of VOLUME");
		visToggleVols.addActionListener(toggleVolumeVisibility);
		//
		JPanel twoCenteredButtonsPlaceHolder = new JPanel();
		contentPane.add(twoCenteredButtonsPlaceHolder, c);
		//
		twoCenteredButtonsPlaceHolder.setLayout( new GridBagLayout() );
		GridBagConstraints bc = new GridBagConstraints();
		bc.fill = GridBagConstraints.HORIZONTAL;
		bc.weightx = 0.4;
		bc.gridx = 0;
		bc.insets = new Insets(0,0,0,20);
		twoCenteredButtonsPlaceHolder.add(visToggleSpots, bc);
		bc.gridx = 1;
		bc.insets = new Insets(0,20,0,0);
		twoCenteredButtonsPlaceHolder.add(visToggleVols, bc);

		// -------------- separator --------------
		c.gridy++;
		insertSeparator(c);

		c.gridy++;
		insertNote("Parameters of the spots in-painting into the Volume", c);

		c.gridy++;
		INTENSITY_OF_COLORS_APPLY = new JCheckBox("Enable spots in-painting into the Volume (visible only on next repainting of Volume)");
		insertCheckBox(INTENSITY_OF_COLORS_APPLY, c);

		c.gridy++;
		c.gridx = 0;
		insertLabel("Next repainting of Volume will be triggered:", c);
		//
		c.gridx = 1;
		UPDATE_VOLUME_AUTOMATICALLY = new JComboBox<>(new String[] {updVolMsgA, updVolMsgM});
		UPDATE_VOLUME_AUTOMATICALLY.addActionListener( updVolAutoListener );
		c.anchor = GridBagConstraints.LINE_END;
		insertRColumnItem(UPDATE_VOLUME_AUTOMATICALLY, c);
		c.anchor = GridBagConstraints.LINE_START;

		c.gridy++;
		c.gridx = 0;
		insertLabel("When repainting, draw colors at this nominal intensity:", c);
		//
		c.gridx = 1;
		INTENSITY_OF_COLORS = new SpinnerNumberModel(2400.0, 0.0, 65535.0, 50.0);
		insertSpinner(INTENSITY_OF_COLORS, (f) -> controlledBridge.INTENSITY_OF_COLORS = f, c);

		c.gridy++;
		c.gridx = 0;
		insertLabel("When repainting, multiply spots radii with:", c);
		//
		c.gridx = 1;
		SPOT_RADIUS_SCALE = new SpinnerNumberModel(3.0, 0.0, 50.0, 1.0);
		insertSpinner(SPOT_RADIUS_SCALE, (f) -> controlledBridge.SPOT_RADIUS_SCALE = f, c);

		c.gridy++;
		INTENSITY_OF_COLORS_BOOST = new JCheckBox("Enable enhancing of spot colors when repainting them into the Volume");
		insertCheckBox(INTENSITY_OF_COLORS_BOOST, c);

		c.gridy++;
		UPDATE_VOLUME_VERBOSE_REPORTS = new JCheckBox("Verbose/debug reporting during Volume repainting");
		insertCheckBox(UPDATE_VOLUME_VERBOSE_REPORTS, c);

		// -------------- button row --------------
		c.gridy++;
		c.gridx = 0;
		c.gridwidth = 1;
		c.insets = new Insets(10,sideSpace,sideSpace,sideSpace);
		JButton redrawBtn = new JButton("  Repaint now (the recently painted timepoint)  ");
		redrawBtn.addActionListener( (e) -> controlledBridge.updateSciviewColoringNow() );
		contentPane.add(redrawBtn, c);

		c.gridx = 1;
		c.anchor = GridBagConstraints.LINE_END;
		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener( (e) -> controlledBridge.detachControllingUI() );
		insertRColumnItem(closeBtn, c);
	}

	final int sideSpace = 15;
	final Insets noteSpace = new Insets(2,sideSpace,8,2*sideSpace);
	void insertNote(final String noteText, final GridBagConstraints c) {
		final int prevGridW = c.gridwidth;
		final Insets prevInsets = c.insets;

		c.gridwidth = 2;
		c.insets = noteSpace;
		c.weightx = 0.1;

		c.gridx = 0;
		contentPane.add( new JLabel(noteText), c);

		c.gridwidth = prevGridW;
		c.insets = prevInsets;
	}

	void insertLabel(final String labelText, final GridBagConstraints c) {
		c.weightx = 0.5;
		contentPane.add(new JLabel(labelText), c);
	}

	final Dimension spinnerMinDim = new Dimension(200,20);
	final Dimension spinnerMaxDim = new Dimension(1000,20);
	void insertSpinner(final SpinnerModel model,
	                   final Consumer<Float> updaterOnEvents,
	                   final GridBagConstraints c) {
		c.anchor = GridBagConstraints.LINE_END;
		insertRColumnItem(new JSpinner(model), c);
		c.anchor = GridBagConstraints.LINE_START;

		OwnerAwareSpinnerChangeListener l = new OwnerAwareSpinnerChangeListener(updaterOnEvents, model);
		model.addChangeListener(l);
		spinnerModelsWithListeners.add(l);
	}
	void insertRColumnItem(final JComponent item, final GridBagConstraints c) {
		item.setMinimumSize(spinnerMinDim);
		item.setPreferredSize(spinnerMinDim);
		item.setMaximumSize(spinnerMaxDim);
		c.weightx = 0.3;
		contentPane.add(item, c);
	}

	void insertCheckBox(final JCheckBox cbox, final GridBagConstraints c) {
		final int prevFill = c.fill;
		final int prevGridW = c.gridwidth;

		cbox.addItemListener( checkboxChangeListener );
		checkBoxesWithListeners.add(cbox);

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 2;

		c.gridx = 0;
		c.weightx = 0.1;
		contentPane.add(cbox, c);

		c.fill = prevFill;
		c.gridwidth = prevGridW;
	}

	final Insets sepSpace = new Insets(8,sideSpace,8,sideSpace);
	void insertSeparator(final GridBagConstraints c) {
		final int prevFill = c.fill;
		final int prevGridW = c.gridwidth;
		final Insets prevInsets = c.insets;

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 2;
		c.weightx = 0.1;
		c.insets = sepSpace;

		c.gridx = 0;
		contentPane.add( new JSeparator(JSeparator.HORIZONTAL), c);

		c.fill = prevFill;
		c.gridwidth = prevGridW;
		c.insets = prevInsets;
	}

	class OwnerAwareSpinnerChangeListener implements ChangeListener {
		final Consumer<Float> pushChangeToHere;
		final SpinnerModel observedSource;
		//
		OwnerAwareSpinnerChangeListener(Consumer<Float> pushChangeToHere,
		                                SpinnerModel fromThisItem) {
			this.pushChangeToHere = pushChangeToHere;
			this.observedSource = fromThisItem;
		}
		//
		@Override
		public void stateChanged(ChangeEvent changeEvent) {
			SpinnerNumberModel s = (SpinnerNumberModel)changeEvent.getSource();
			pushChangeToHere.accept( s.getNumber().floatValue() );
			if (controlledBridge.UPDATE_VOLUME_AUTOMATICALLY) controlledBridge.updateSciviewColoringNow();
		}
	}
	ItemListener checkboxChangeListener = new ItemListener() {
		@Override
		public void itemStateChanged(ItemEvent itemEvent) {
			JCheckBox cb = (JCheckBox) itemEvent.getSource();
			if (cb == INTENSITY_OF_COLORS_APPLY) {
				controlledBridge.INTENSITY_OF_COLORS_APPLY = cb.isSelected();
				if (controlledBridge.UPDATE_VOLUME_AUTOMATICALLY) controlledBridge.updateSciviewColoringNow();
			} else if (cb == INTENSITY_OF_COLORS_BOOST) {
				controlledBridge.INTENSITY_OF_COLORS_BOOST = cb.isSelected();
				if (controlledBridge.UPDATE_VOLUME_AUTOMATICALLY) controlledBridge.updateSciviewColoringNow();
			} else if (cb == UPDATE_VOLUME_VERBOSE_REPORTS) {
				controlledBridge.UPDATE_VOLUME_VERBOSE_REPORTS = cb.isSelected();
			}
		}
	};
	final java.util.List<OwnerAwareSpinnerChangeListener> spinnerModelsWithListeners = new ArrayList<>(10);
	final java.util.List<JCheckBox> checkBoxesWithListeners = new ArrayList<>(10);
	//
	//below is also defined: rangeSliderListener
	//
	final ActionListener updVolAutoListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent actionEvent) {
			boolean prevBridgeState = controlledBridge.UPDATE_VOLUME_AUTOMATICALLY;
			controlledBridge.UPDATE_VOLUME_AUTOMATICALLY
					= UPDATE_VOLUME_AUTOMATICALLY.getSelectedIndex() == 0;
			if (controlledBridge.UPDATE_VOLUME_AUTOMATICALLY && !prevBridgeState) {
				//NB: a protection "if" here because this listener can be triggered even
				//when one re-chooses (re-clicks) the already chosen element
				controlledBridge.updateSciviewColoringNow();
			}
		}
	};
	final ActionListener toggleSpotsVisibility = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent actionEvent) {
			boolean newState = !controlledBridge.sphereParent.getVisible();
			controlledBridge.setVisibilityOfSpots(newState);
		}
	};
	final ActionListener toggleVolumeVisibility = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent actionEvent) {
			boolean newState = !controlledBridge.redVolChannelNode.getVisible();
			controlledBridge.setVisibilityOfVolume(newState);
		}
	};

	/**
	 * Disable all listeners to make sure that, even if this UI window would ever
	 * be re-displayed, its controls could not control anything (and would throw
	 * NPEs if the controls would actually be used).
	 */
	public void deactivateAndForget() {
		//listeners tear-down here
		spinnerModelsWithListeners.forEach(c -> c.observedSource.removeChangeListener(c));
		checkBoxesWithListeners.forEach(c -> c.removeItemListener( checkboxChangeListener ));
		INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.removeChangeListener(rangeSliderListener);
		UPDATE_VOLUME_AUTOMATICALLY.removeActionListener( updVolAutoListener );
		visToggleSpots.removeActionListener(toggleSpotsVisibility);
		visToggleVols.removeActionListener(toggleVolumeVisibility);
		this.controlledBridge = null;
	}

	public void updatePaneValues() {
		final boolean updVolAutoBackup = controlledBridge.UPDATE_VOLUME_AUTOMATICALLY;
		//temporarily disable because setting the controls trigger their listeners
		//that trigger (not all of them) the expensive volume updating
		controlledBridge.UPDATE_VOLUME_AUTOMATICALLY = false;

		INTENSITY_CONTRAST.setValue( controlledBridge.INTENSITY_CONTRAST );
		INTENSITY_SHIFT.setValue( controlledBridge.INTENSITY_SHIFT );
		INTENSITY_CLAMP_AT_TOP.setValue( controlledBridge.INTENSITY_CLAMP_AT_TOP );
		INTENSITY_GAMMA.setValue( controlledBridge.INTENSITY_GAMMA );

		INTENSITY_OF_COLORS.setValue( controlledBridge.INTENSITY_OF_COLORS );

		INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM
				.getSlider()
				.setValue( (int)controlledBridge.INTENSITY_RANGE_MIN );
		INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM
				.getRangeSlider()
				.setUpperValue( (int)controlledBridge.INTENSITY_RANGE_MAX );

		INTENSITY_OF_COLORS_APPLY.setSelected( controlledBridge.INTENSITY_OF_COLORS_APPLY );
		INTENSITY_OF_COLORS_BOOST.setSelected( controlledBridge.INTENSITY_OF_COLORS_BOOST );
		SPOT_RADIUS_SCALE.setValue( controlledBridge.SPOT_RADIUS_SCALE );

		UPDATE_VOLUME_AUTOMATICALLY.setSelectedItem(updVolAutoBackup ? updVolMsgA : updVolMsgM);
		UPDATE_VOLUME_VERBOSE_REPORTS.setSelected( controlledBridge.UPDATE_VOLUME_VERBOSE_REPORTS );

		controlledBridge.UPDATE_VOLUME_AUTOMATICALLY = updVolAutoBackup;
	}

	//int SOURCE_ID = 0;
	//int SOURCE_USED_RES_LEVEL = 0;

	SpinnerModel INTENSITY_CONTRAST;
	SpinnerModel INTENSITY_SHIFT;
	SpinnerModel INTENSITY_CLAMP_AT_TOP;
	SpinnerModel INTENSITY_GAMMA;

	SpinnerModel INTENSITY_OF_COLORS;

	AdjustableBoundsRangeSlider INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM;
	final ChangeListener rangeSliderListener = (l) -> {
		controlledBridge.INTENSITY_RANGE_MIN = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.getValue();
		controlledBridge.INTENSITY_RANGE_MAX = INTENSITY_RANGE_MINMAX_CTRL_GUI_ELEM.getUpperValue();
		controlledBridge.redVolChannelNode.setMinDisplayRange( controlledBridge.INTENSITY_RANGE_MIN );
		controlledBridge.redVolChannelNode.setMaxDisplayRange( controlledBridge.INTENSITY_RANGE_MAX );
	};
	//
	JButton visToggleSpots, visToggleVols;

	JCheckBox INTENSITY_OF_COLORS_APPLY;
	JCheckBox INTENSITY_OF_COLORS_BOOST;
	SpinnerModel SPOT_RADIUS_SCALE;

	static final String updVolMsgA = "Automatically";
	static final String updVolMsgM = "Manually";
	private JComboBox<String> UPDATE_VOLUME_AUTOMATICALLY;
	private JCheckBox UPDATE_VOLUME_VERBOSE_REPORTS;
}