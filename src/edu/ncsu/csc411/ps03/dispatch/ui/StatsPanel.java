package edu.ncsu.csc411.ps03.dispatch.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import edu.ncsu.csc411.ps03.dispatch.model.ActualDispatch;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMetrics;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;

/**
 * Total, average, and worst estimated minutes for SA (now and best), Optimal, Greedy, and
 * Actual. Below the table are the gap-to-optimal line, any scenario warnings, and the caveats.
 */
@SuppressWarnings("serial")
public class StatsPanel extends JPanel {
	public static final int SA_NOW = 0, SA_BEST = 1, OPTIMAL = 2, GREEDY = 3, ACTUAL = 4;
	private static final String[] COLUMNS = {"Method", "Total min", "Avg min", "Worst min"};
	private static final String CAVEATS = "<html><i>Incidents in a window are treated as simultaneous, and minutes "
			+ "are straight-line estimates. Real dispatch also weighs unit availability, apparatus type, coverage, "
			+ "and mutual aid, so Actual is context, not a score.</i></html>";

	private final DefaultTableModel model;
	private final JLabel gapLabel = new JLabel(" ");
	private final JLabel warningLabel = new JLabel(" ");

	public StatsPanel() {
		super(new BorderLayout(0, 6));
		this.model = new DefaultTableModel(COLUMNS, 5) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		JTable table = new JTable(this.model);
		table.setRowSelectionAllowed(false);
		table.setFillsViewportHeight(true);
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(440, 105));
		add(scroll, BorderLayout.NORTH);
		JPanel notes = new JPanel(new GridLayout(0, 1, 0, 2));
		notes.add(this.gapLabel);
		notes.add(this.warningLabel);
		JLabel caveats = new JLabel(CAVEATS);
		caveats.setPreferredSize(new Dimension(440, 60));
		notes.add(caveats);
		add(notes, BorderLayout.CENTER);
	}

	public TableModel getTableModel() { return this.model; }
	public String getGapText() { return this.gapLabel.getText(); }
	public String getWarningText() { return this.warningLabel.getText(); }

	public void update(DispatchSession s) {
		row(SA_NOW, "SA now", s.getCurrentMetrics());
		row(SA_BEST, "SA best", s.getBestMetrics());
		row(OPTIMAL, "Optimal", s.getOptimalMetrics());
		row(GREEDY, "Greedy nearest", s.getGreedyMetrics());
		ActualDispatch actual = s.getActual();
		if (!actual.isAvailable()) {
			this.model.setValueAt("Actual", ACTUAL, 0);
			this.model.setValueAt("not published", ACTUAL, 1);
			this.model.setValueAt("", ACTUAL, 2);
			this.model.setValueAt("", ACTUAL, 3);
		} else {
			row(ACTUAL, actual.unmatchedCount() == 0 ? "Actual" : "Actual (" + actual.unmatchedCount() + " unmatched)",
					s.getActualMetrics());
		}
		String sa = s.bestIsOptimal() ? "optimal"
				: String.format(Locale.US, "+%.1f%% vs optimal", 100 * s.getBestMetrics().gapTo(s.getOptimalMetrics()));
		this.gapLabel.setText(String.format(Locale.US, "SA best: %s, found at iteration %d.   Greedy: +%.1f%%.",
				sa, s.getBestIteration(), 100 * s.getGreedyMetrics().gapTo(s.getOptimalMetrics())));
		StringBuilder warnings = new StringBuilder();
		for (String warning : s.getScenario().getWarnings()) {
			warnings.append(warnings.length() == 0 ? "" : " ").append(warning);
		}
		this.warningLabel.setText(warnings.length() == 0 ? " " : warnings.toString());
	}

	private void row(int row, String name, DispatchMetrics metrics) {
		this.model.setValueAt(name, row, 0);
		this.model.setValueAt(String.format(Locale.US, "%.1f", metrics.getTotalMinutes()), row, 1);
		this.model.setValueAt(String.format(Locale.US, "%.1f", metrics.getAverageMinutes()), row, 2);
		this.model.setValueAt(String.format(Locale.US, "%.1f", metrics.getWorstMinutes()), row, 3);
	}
}
