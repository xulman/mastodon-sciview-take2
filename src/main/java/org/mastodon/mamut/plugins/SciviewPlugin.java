package org.mastodon.mamut.plugins;

import org.mastodon.mamut.SciviewBridge;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Menu;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import static sc.iview.commands.MenuWeights.HELP;
import static sc.iview.commands.MenuWeights.HELP_HELP;

@Plugin(type = Command.class, menuRoot = "SciView", //
		menu = { @Menu(label = "Help", weight = HELP), //
				@Menu(label = "Mastodon Bridge", weight = HELP_HELP) })
public class SciviewPlugin implements Command
{
	@Override
	public void run()
	{
		final JFrame panel = new JFrame("Mastodon-sciview Bridge Keys Overview");
		panel.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		final GridBagLayout gridBagLayout = new GridBagLayout();
		panel.setLayout( gridBagLayout );

		final GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.insets = new Insets(5,8,0,8);
		c.weighty = 0.1;
		c.gridy = 0;
		JLabel spacer = new JLabel("   ");
		collectRowsOfText().forEach(r -> {
			c.anchor = GridBagConstraints.EAST;
			c.weightx = 0.3;
			c.gridx = 0;
			panel.add(new JLabel(r[0]), c);

			c.weightx = 0.1;
			c.gridx = 1;
			panel.add(spacer, c);

			c.anchor = GridBagConstraints.LINE_START;
			c.weightx = 0.3;
			c.gridx = 2;
			panel.add(new JLabel(r[1]), c);
			c.gridy++;
		});

		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener(l -> panel.dispose());
		c.gridx = 0;
		c.gridwidth = 3;
		c.weightx = 0;
		c.weighty = 0;
		c.insets = new Insets(15,30,5,30);
		panel.add(closeBtn, c);

		panel.pack();
		panel.setVisible(true);
	}

	private Collection<String[]> collectRowsOfText() {
		Collection<String[]> rows = new ArrayList<>(15);
		rows.add(new String[] {SciviewBridge.key_DEC_SPH, SciviewBridge.desc_DEC_SPH});
		rows.add(new String[] {SciviewBridge.key_INC_SPH, SciviewBridge.desc_INC_SPH});
		rows.add(new String[] {SciviewBridge.key_PREV_TP, SciviewBridge.desc_PREV_TP});
		rows.add(new String[] {SciviewBridge.key_NEXT_TP, SciviewBridge.desc_NEXT_TP});
		rows.add(new String[] {SciviewBridge.key_COLORING, SciviewBridge.desc_COLORING});
		rows.add(new String[] {SciviewBridge.key_CLRNG_AUTO, SciviewBridge.desc_CLRNG_AUTO});
		rows.add(new String[] {SciviewBridge.key_CLRNG_ONOFF, SciviewBridge.desc_CLRNG_ONOFF});
		rows.add(new String[] {SciviewBridge.key_CTRL_WIN, SciviewBridge.desc_CTRL_WIN});
		rows.add(new String[] {SciviewBridge.key_CTRL_INFO, SciviewBridge.desc_CTRL_INFO});
		return rows;
	}
}
