package edu.ncsu.csc411.ps03.dispatch.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;

import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;
import edu.ncsu.csc411.ps03.utils.ColorPalette;

/** SA's current and best total minutes against iteration, with the optimum as a dashed line. */
@SuppressWarnings("serial")
public class ConvergenceChart extends JPanel {
	private static final int LEFT = 60, RIGHT = 150, TOP = 12, BOTTOM = 24;
	private DispatchSession session;
	private int iterations;

	public ConvergenceChart(int width, int height) {
		setPreferredSize(new Dimension(width, height));
		setSize(width, height);
		setBackground(Color.WHITE);
	}

	public void setSession(DispatchSession session, int iterations) {
		this.session = session;
		this.iterations = iterations;
		repaint();
	}

	/** {min, max} total minutes over the history and the optimum, padded so a flat line still has height. */
	public static double[] yRange(List<double[]> history, double optimum) {
		double min = optimum, max = optimum;
		for (double[] point : history) {
			min = Math.min(min, Math.min(point[0], point[1]));
			max = Math.max(max, Math.max(point[0], point[1]));
		}
		double pad = Math.max((max - min) * 0.05, 0.5);
		return new double[] {Math.max(0, min - pad), max + pad};
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (this.session == null) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
			int w = getPreferredSize().width, h = getPreferredSize().height;
			int plotW = w - LEFT - RIGHT, plotH = h - TOP - BOTTOM;
			List<double[]> history = this.session.getHistory();
			double optimum = this.session.getOptimalMetrics().getTotalMinutes();
			double[] range = yRange(history, optimum);
			int xMax = Math.max(1, Math.max(this.iterations, history.size() - 1));

			g2.setColor(ColorPalette.BLACK);
			g2.drawLine(LEFT, TOP, LEFT, TOP + plotH);
			g2.drawLine(LEFT, TOP + plotH, LEFT + plotW, TOP + plotH);
			g2.drawString(String.format(Locale.US, "%.0f min", range[1]), 4, TOP + 10);
			g2.drawString(String.format(Locale.US, "%.0f min", range[0]), 4, TOP + plotH);
			g2.drawString("0", LEFT, h - 6);
			String end = xMax + " iterations";
			g2.drawString(end, LEFT + plotW - g2.getFontMetrics().stringWidth(end), h - 6);

			int optimumY = y(optimum, range, plotH);
			g2.setColor(ColorPalette.GREEN);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 4f}, 0f));
			g2.drawLine(LEFT, optimumY, LEFT + plotW, optimumY);

			g2.setStroke(new BasicStroke(1f));
			polyline(g2, history, 0, ColorPalette.LIGHTBLUE, range, xMax, plotW, plotH);
			g2.setStroke(new BasicStroke(2f));
			polyline(g2, history, 1, ColorPalette.RED, range, xMax, plotW, plotH);

			int lx = LEFT + plotW + 12;
			legend(g2, lx, TOP + 12, ColorPalette.LIGHTBLUE, "SA current");
			legend(g2, lx, TOP + 30, ColorPalette.RED, "SA best");
			legend(g2, lx, TOP + 48, ColorPalette.GREEN, String.format(Locale.US, "Optimal %.1f", optimum));
		} finally {
			g2.dispose();
		}
	}

	private void polyline(Graphics2D g2, List<double[]> history, int series, Color color, double[] range,
			int xMax, int plotW, int plotH) {
		int n = history.size();
		int[] xs = new int[n], ys = new int[n];
		for (int i = 0; i < n; i++) {
			xs[i] = LEFT + (int) Math.round((double) i / xMax * plotW);
			ys[i] = y(history.get(i)[series], range, plotH);
		}
		g2.setColor(color);
		g2.drawPolyline(xs, ys, n);
	}

	private static int y(double minutes, double[] range, int plotH) {
		return TOP + (int) Math.round((range[1] - minutes) / (range[1] - range[0]) * plotH);
	}

	private static void legend(Graphics2D g2, int x, int y, Color color, String text) {
		g2.setColor(color);
		g2.fillRect(x, y - 8, 14, 4);
		g2.setColor(ColorPalette.BLACK);
		g2.drawString(text, x + 20, y - 2);
	}
}
