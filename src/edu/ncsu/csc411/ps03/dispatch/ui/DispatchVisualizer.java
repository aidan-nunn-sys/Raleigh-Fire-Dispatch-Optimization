package edu.ncsu.csc411.ps03.dispatch.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import edu.ncsu.csc411.ps03.dispatch.data.ArcGisClient;
import edu.ncsu.csc411.ps03.dispatch.data.RaleighIncidentClient;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.data.ScenarioIO;
import edu.ncsu.csc411.ps03.dispatch.data.Station;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;
import edu.ncsu.csc411.ps03.utils.ConfigurationLoader;

/**
 * The +X desktop tool. It replays a Raleigh fire-incident window and animates the student's
 * simulated annealing (one iteration per timer tick) next to the Hungarian optimum,
 * greedy nearest-station, and what Raleigh actually sent. Settings come from
 * config/configDispatch.txt; data comes from the committed files in inputs/raleigh/.
 */
@SuppressWarnings("serial")
public class DispatchVisualizer extends JFrame implements ControlPanel.Listener {
	static final String CONFIG = "config/configDispatch.txt";
	static final String DIR = "inputs/raleigh/";
	static final String[] SNAPSHOTS = {"florence_peak", "helene_peak", "typical_weekday"};
	static final String ATTRIBUTION = "Data: City of Raleigh Open Data (fire incidents, city limits); Wake County Open Data "
			+ "(fire stations). Teaching model, not a dispatch tool.";

	private final int iterations;
	private final long windowMillis;
	private final List<Station> stations;
	private final TravelModel model;
	private final RaleighIncidentClient liveClient;
	private final MapPanel mapPanel;
	private final ControlPanel controlPanel;
	private final StatsPanel statsPanel = new StatsPanel();
	private final ConvergenceChart chart = new ConvergenceChart(1080, 150);
	private final JLabel statusLabel = new JLabel(" ");
	private final Timer timer;
	private DispatchSession session;

	public DispatchVisualizer(Properties config, List<Station> stations, TravelModel model,
			List<double[][]> outline, List<Scenario> snapshots) {
		super("CSC 411 - Raleigh Fire Dispatch (PS03 +X)");
		this.iterations = intSetting(config, "ITERATIONS", 1000);
		this.windowMillis = intSetting(config, "WINDOW_MINUTES", 120) * 60000L;
		int delay = intSetting(config, "DELAY", 50);
		this.stations = stations;
		this.model = model;
		Set<String> known = new HashSet<String>();
		for (Station s : stations) {
			known.add(s.getId());
		}
		this.liveClient = new RaleighIncidentClient(new ArcGisClient(), RaleighIncidentClient.DEFAULT_CACHE, known,
				intSetting(config, "CACHE_TTL_MINUTES", 60));
		this.mapPanel = new MapPanel(outline, 620, 540);
		this.controlPanel = new ControlPanel(snapshots, delay);
		this.timer = new Timer(delay, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				tick();
			}
		});

		JPanel right = new JPanel(new BorderLayout(0, 8));
		right.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		right.add(this.controlPanel, BorderLayout.NORTH);
		right.add(this.statsPanel, BorderLayout.CENTER);
		right.setPreferredSize(new Dimension(470, 540));
		JPanel footer = new JPanel(new GridLayout(2, 1));
		footer.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
		footer.add(this.statusLabel);
		footer.add(new JLabel(ATTRIBUTION));
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(this.chart, BorderLayout.CENTER);
		bottom.add(footer, BorderLayout.SOUTH);
		getContentPane().add(this.mapPanel, BorderLayout.WEST);
		getContentPane().add(right, BorderLayout.CENTER);
		getContentPane().add(bottom, BorderLayout.SOUTH);

		this.controlPanel.setListener(this);
		scenarioChanged();
		setResizable(false);
	}

	/** An integer setting, or fallback when it is missing or not a number. */
	public static int intSetting(Properties config, String key, int fallback) {
		String value = config.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException nfe) {
			return fallback;
		}
	}

	private void tick() {
		if (this.session.getIteration() >= this.iterations) {
			this.timer.stop();
			this.controlPanel.setPlaying(false);
			this.statusLabel.setText("Finished " + this.iterations + " iterations. Press Reset or Play to run SA again.");
			return;
		}
		this.session.step();
		refresh();
	}

	private void refresh() {
		this.mapPanel.repaint();
		this.statsPanel.update(this.session);
		this.chart.repaint();
		this.controlPanel.setIteration(this.session.getIteration(), this.iterations);
	}

	private void stop() {
		this.timer.stop();
		this.controlPanel.setPlaying(false);
	}

	@Override
	public void scenarioChanged() {
		stop();
		Scenario scenario = this.controlPanel.selectedScenario();
		this.session = new DispatchSession(scenario, this.model);
		this.mapPanel.setSession(this.session);
		this.chart.setSession(this.session, this.iterations);
		refresh();
		this.statusLabel.setText("Loaded " + scenario.getName() + ": " + scenario.getIncidents().size() + " incidents.");
	}

	@Override
	public void fetchRequested() {
		this.controlPanel.setFetching(true);
		this.statusLabel.setText("Fetching the past-month feed from City of Raleigh Open Data...");
		new SwingWorker<RaleighIncidentClient.Result, Void>() {
			@Override
			protected RaleighIncidentClient.Result doInBackground() throws IOException {
				return DispatchVisualizer.this.liveClient.load();
			}

			@Override
			protected void done() {
				DispatchVisualizer.this.controlPanel.setFetching(false);
				try {
					RaleighIncidentClient.Result result = get();
					List<Scenario> windows = RaleighIncidentClient.busiestScenarios(result.incidents,
							DispatchVisualizer.this.stations, DispatchVisualizer.this.windowMillis, 5);
					String age = (result.fromCache ? "cached, fetched " : "fetched ")
							+ RaleighIncidentClient.describeAge(System.currentTimeMillis() - result.fetchedAtMillis);
					DispatchVisualizer.this.controlPanel.setLiveWindows(windows, age);
					String status = result.message != null ? result.message
							: "Fetched " + result.incidents.size() + " incidents from the past month.";
					DispatchVisualizer.this.statusLabel.setText(windows.isEmpty() ? status + " No windows to show." : status);
				} catch (InterruptedException | ExecutionException e) {
					Throwable cause = e.getCause() != null ? e.getCause() : e;
					DispatchVisualizer.this.statusLabel.setText("Fetch failed (" + cause.getMessage()
							+ "); still showing the committed snapshots.");
				}
			}
		}.execute();
	}

	@Override
	public void playToggled(boolean playing) {
		if (!playing) {
			this.timer.stop();
			return;
		}
		if (this.session.getIteration() >= this.iterations) {
			this.session.reset();
			refresh();
		}
		this.timer.start();
	}

	@Override
	public void stepRequested() {
		if (this.session.getIteration() < this.iterations) {
			this.session.step();
			refresh();
		}
	}

	@Override
	public void resetRequested() {
		stop();
		this.session.reset();
		refresh();
		this.statusLabel.setText("Reset: a new SA run on " + this.session.getScenario().getName() + ".");
	}

	@Override
	public void speedChanged(int delayMs) {
		this.timer.setDelay(delayMs);
	}

	@Override
	public void overlaysChanged(boolean optimal, boolean actual) {
		this.mapPanel.setShowOptimal(optimal);
		this.mapPanel.setShowActual(actual);
	}

	@Override
	public void exportRequested() {
		String path = DispatchExport.fileName(this.session.getScenario().getName());
		try {
			DispatchExport.write(path, this.session);
			this.statusLabel.setText("Wrote " + path + ".");
		} catch (IOException e) {
			this.statusLabel.setText("Export failed: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		final Properties config = ConfigurationLoader.loadConfiguration(CONFIG);
		final List<Station> stations;
		final TravelModel model;
		final List<double[][]> outline;
		final List<Scenario> snapshots = new ArrayList<Scenario>();
		try {
			stations = ScenarioIO.readStations(DIR + "stations.csv");
			model = TravelModel.load(DIR + "travel_model.properties");
			outline = ScenarioIO.readOutline(DIR + "raleigh_outline.csv");
			for (String name : SNAPSHOTS) {
				snapshots.add(ScenarioIO.readScenario(DIR + "scenarios/" + name + ".csv", stations));
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Could not read " + e.getMessage()
					+ "\n\nThe committed Raleigh data in inputs/raleigh/ is missing or damaged. Restore it from git,"
					+ "\nor run edu.ncsu.csc411.ps03.dispatch.data.FetchStaticData to regenerate it.",
					"Missing data", JOptionPane.ERROR_MESSAGE);
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				JFrame frame = new DispatchVisualizer(config, stations, model, outline, snapshots);
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.pack();
				frame.setLocationRelativeTo(null);
				frame.setVisible(true);
			}
		});
	}
}
