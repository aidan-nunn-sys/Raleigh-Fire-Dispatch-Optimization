package edu.ncsu.csc411.ps03.dispatch.ui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import edu.ncsu.csc411.ps03.dispatch.data.Scenario;

/**
 * Scenario and live-window pickers, "Fetch latest", Play/Pause, Step, Reset, the speed
 * slider, the overlay checkboxes, and Export. It holds no model state beyond which scenario
 * is selected; every action goes to the Listener.
 */
@SuppressWarnings("serial")
public class ControlPanel extends JPanel {
	public static final String LIVE = "Latest (live)";

	/** What the window does in response to the controls. */
	public interface Listener {
		void scenarioChanged();
		void fetchRequested();
		void playToggled(boolean playing);
		void stepRequested();
		void resetRequested();
		void speedChanged(int delayMs);
		void overlaysChanged(boolean optimal, boolean actual);
		void exportRequested();
	}

	private final List<Scenario> snapshots;
	private List<Scenario> liveWindows = new ArrayList<Scenario>();
	private final JComboBox<String> scenarioCombo = new JComboBox<String>();
	private final JComboBox<String> windowCombo = new JComboBox<String>();
	private final JButton fetchButton = new JButton("Fetch latest");
	private final JLabel cacheLabel = new JLabel("Not fetched yet");
	private final JToggleButton playButton = new JToggleButton("Play");
	private final JButton stepButton = new JButton("Step");
	private final JButton resetButton = new JButton("Reset");
	private final JSlider speedSlider;
	private final JLabel iterationLabel = new JLabel("Iteration 0");
	private final JCheckBox optimalBox = new JCheckBox("Optimal overlay");
	private final JCheckBox actualBox = new JCheckBox("Actual overlay");
	private final JButton exportButton = new JButton("Export CSV");
	private Listener listener;
	private boolean updating; // true while the combos are refilled, so no events fire

	public ControlPanel(List<Scenario> snapshots, int delayMs) {
		this.snapshots = new ArrayList<Scenario>(snapshots);
		for (Scenario s : this.snapshots) {
			this.scenarioCombo.addItem(s.getName());
		}
		this.windowCombo.setPrototypeDisplayValue("Latest Sep 24 17:05-19:05 (99)");
		this.windowCombo.setEnabled(false);
		this.speedSlider = new JSlider(1, 200, Math.max(1, Math.min(200, delayMs)));

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(row(new JLabel("Scenario"), this.scenarioCombo));
		add(row(this.fetchButton, this.cacheLabel));
		add(row(new JLabel("Window"), this.windowCombo));
		add(row(this.playButton, this.stepButton, this.resetButton, this.iterationLabel));
		add(row(new JLabel("Delay (ms)"), this.speedSlider));
		add(row(this.optimalBox, this.actualBox, this.exportButton));

		ActionListener selection = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!ControlPanel.this.updating) {
					ControlPanel.this.windowCombo.setEnabled(isLiveSelected());
					fireScenarioChanged();
				}
			}
		};
		this.scenarioCombo.addActionListener(selection);
		this.windowCombo.addActionListener(selection);
		this.fetchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ControlPanel.this.listener != null) {
					ControlPanel.this.listener.fetchRequested();
				}
			}
		});
		this.playButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean playing = ControlPanel.this.playButton.isSelected();
				setPlaying(playing);
				if (ControlPanel.this.listener != null) {
					ControlPanel.this.listener.playToggled(playing);
				}
			}
		});
		this.stepButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ControlPanel.this.listener != null) {
					ControlPanel.this.listener.stepRequested();
				}
			}
		});
		this.resetButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ControlPanel.this.listener != null) {
					ControlPanel.this.listener.resetRequested();
				}
			}
		});
		this.speedSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				if (ControlPanel.this.listener != null) {
					ControlPanel.this.listener.speedChanged(ControlPanel.this.speedSlider.getValue());
				}
			}
		});
		ActionListener overlays = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ControlPanel.this.listener != null) {
					ControlPanel.this.listener.overlaysChanged(ControlPanel.this.optimalBox.isSelected(),
							ControlPanel.this.actualBox.isSelected());
				}
			}
		};
		this.optimalBox.addActionListener(overlays);
		this.actualBox.addActionListener(overlays);
		this.exportButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ControlPanel.this.listener != null) {
					ControlPanel.this.listener.exportRequested();
				}
			}
		});
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	/** The chosen snapshot, or the chosen live window when "Latest (live)" is selected. */
	public Scenario selectedScenario() {
		int index = this.scenarioCombo.getSelectedIndex();
		if (index >= 0 && index < this.snapshots.size()) {
			return this.snapshots.get(index);
		}
		int window = this.windowCombo.getSelectedIndex();
		return window >= 0 && window < this.liveWindows.size() ? this.liveWindows.get(window) : this.snapshots.get(0);
	}

	/** Replaces the live windows after a fetch and selects the busiest one. Empty keeps the snapshots. */
	public void setLiveWindows(List<Scenario> windows, String cacheText) {
		this.cacheLabel.setText(cacheText);
		this.updating = true;
		try {
			this.liveWindows = new ArrayList<Scenario>(windows);
			this.windowCombo.removeAllItems();
			for (Scenario s : this.liveWindows) {
				this.windowCombo.addItem(s.getName());
			}
			boolean hasLive = this.scenarioCombo.getItemCount() > this.snapshots.size();
			if (windows.isEmpty()) {
				if (hasLive) {
					this.scenarioCombo.removeItem(LIVE);
				}
			} else {
				if (!hasLive) {
					this.scenarioCombo.addItem(LIVE);
				}
				this.scenarioCombo.setSelectedItem(LIVE);
				this.windowCombo.setSelectedIndex(0);
			}
			this.windowCombo.setEnabled(isLiveSelected());
		} finally {
			this.updating = false;
		}
		if (!windows.isEmpty()) {
			fireScenarioChanged();
		}
	}

	public void setFetching(boolean fetching) {
		this.fetchButton.setEnabled(!fetching);
		this.fetchButton.setText(fetching ? "Fetching..." : "Fetch latest");
	}

	public void setPlaying(boolean playing) {
		this.playButton.setSelected(playing);
		this.playButton.setText(playing ? "Pause" : "Play");
		this.stepButton.setEnabled(!playing);
	}

	public void setIteration(int iteration, int max) {
		this.iterationLabel.setText("Iteration " + iteration + " / " + max);
	}

	public int getScenarioItemCount() { return this.scenarioCombo.getItemCount(); }
	public boolean isWindowSelectable() { return this.windowCombo.isEnabled(); }
	public String getPlayText() { return this.playButton.getText(); }

	private boolean isLiveSelected() {
		return LIVE.equals(this.scenarioCombo.getSelectedItem());
	}

	private void fireScenarioChanged() {
		if (this.listener != null) {
			this.listener.scenarioChanged();
		}
	}

	private static JPanel row(javax.swing.JComponent... components) {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		for (javax.swing.JComponent c : components) {
			row.add(c);
		}
		return row;
	}
}
