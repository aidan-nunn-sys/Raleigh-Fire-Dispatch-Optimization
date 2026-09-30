package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.ui.ControlPanel;
import edu.ncsu.csc411.ps03.dispatch.ui.DispatchVisualizer;

public class ControlPanelTest {

	private static Scenario named(String name) {
		return new Scenario(name, "", Fixtures.stationsAlongLine(3), new ArrayList<Incident>());
	}

	private static final List<Scenario> SNAPSHOTS = Arrays.asList(named("Florence peak"), named("Helene peak"),
			named("Typical weekday"));

	/** Counts scenarioChanged() calls and ignores everything else. */
	private static class Counter implements ControlPanel.Listener {
		int scenarioChanges;
		@Override public void scenarioChanged() { this.scenarioChanges++; }
		@Override public void fetchRequested() {}
		@Override public void playToggled(boolean playing) {}
		@Override public void stepRequested() {}
		@Override public void resetRequested() {}
		@Override public void speedChanged(int delayMs) {}
		@Override public void overlaysChanged(boolean optimal, boolean actual) {}
		@Override public void exportRequested() {}
	}

	@Test
	public void startsWithSnapshotsOnly() {
		ControlPanel panel = new ControlPanel(SNAPSHOTS, 50);
		assertEquals(3, panel.getScenarioItemCount());
		assertSame(SNAPSHOTS.get(0), panel.selectedScenario());
		assertFalse(panel.isWindowSelectable());
	}

	@Test
	public void liveWindowsAddLatestAndSelectTheBusiest() {
		ControlPanel panel = new ControlPanel(SNAPSHOTS, 50);
		Counter counter = new Counter();
		panel.setListener(counter);
		List<Scenario> windows = Arrays.asList(named("Latest A"), named("Latest B"));
		panel.setLiveWindows(windows, "fetched just now");
		assertEquals(4, panel.getScenarioItemCount());
		assertSame(windows.get(0), panel.selectedScenario());
		assertTrue(panel.isWindowSelectable());
		assertEquals(1, counter.scenarioChanges);
	}

	@Test
	public void emptyLiveWindowsKeepTheSnapshots() {
		ControlPanel panel = new ControlPanel(SNAPSHOTS, 50);
		panel.setLiveWindows(new ArrayList<Scenario>(), "fetched just now");
		assertEquals(3, panel.getScenarioItemCount());
		assertSame(SNAPSHOTS.get(0), panel.selectedScenario());
	}

	@Test
	public void playingSwapsTheButtonText() {
		ControlPanel panel = new ControlPanel(SNAPSHOTS, 50);
		assertEquals("Play", panel.getPlayText());
		panel.setPlaying(true);
		assertEquals("Pause", panel.getPlayText());
	}

	@Test
	public void settingsFallBackToDefaults() {
		Properties props = new Properties();
		props.setProperty("DELAY", " 75 ");
		props.setProperty("ITERATIONS", "lots");
		assertEquals(75, DispatchVisualizer.intSetting(props, "DELAY", 50));
		assertEquals(1000, DispatchVisualizer.intSetting(props, "ITERATIONS", 1000));
		assertEquals(60, DispatchVisualizer.intSetting(props, "CACHE_TTL_MINUTES", 60));
	}
}
