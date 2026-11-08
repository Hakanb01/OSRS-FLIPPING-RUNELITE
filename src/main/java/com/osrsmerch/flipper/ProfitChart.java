package com.osrsmerch.flipper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** A small line chart of the running profit over the last days. One series, one colour: green when it ends up, red when down. */
final class ProfitChart extends JComponent
{
	private final List<Long> cumulative = new ArrayList<>();
	private final List<String> days = new ArrayList<>();

	ProfitChart(List<Summary.ChartPoint> points)
	{
		long sum = 0;
		for (Summary.ChartPoint p : points)
		{
			sum += Summary.gp(p.profit);
			cumulative.add(sum);
			days.add(p.day);
		}
		setFont(FontManager.getRunescapeSmallFont());
		setPreferredSize(new Dimension(10, 78));
		setMinimumSize(new Dimension(10, 78));
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRoundRect(0, 0, w, h, 8, 8);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		if (cumulative.size() < 2)
		{
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			String t = "No sales yet";
			g.drawString(t, (w - fm.stringWidth(t)) / 2, h / 2 + 4);
			g.dispose();
			return;
		}
		long min = 0;
		long max = 0;
		for (long v : cumulative)
		{
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		if (max == min)
		{
			max = min + 1;
		}
		int padX = 8;
		int top = 14;
		int bottom = h - 8;
		double stepX = (w - 2.0 * padX) / (cumulative.size() - 1);
		long last = cumulative.get(cumulative.size() - 1);
		Color line = last >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
		double zeroY = y(0, min, max, top, bottom);
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 3f}, 0f));
		g.drawLine(padX, (int) zeroY, w - padX, (int) zeroY);
		Path2D path = new Path2D.Double();
		for (int i = 0; i < cumulative.size(); i++)
		{
			double x = padX + i * stepX;
			double y = y(cumulative.get(i), min, max, top, bottom);
			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, y);
			}
		}
		g.setColor(line);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(path);
		double lx = padX + (cumulative.size() - 1) * stepX;
		double ly = y(last, min, max, top, bottom);
		g.fillOval((int) lx - 3, (int) ly - 3, 6, 6);
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		String label = Money.signed(last) + " in " + cumulative.size() + " days";
		g.drawString(label, padX, 11);
		g.dispose();
	}

	private static double y(long v, long min, long max, int top, int bottom)
	{
		return bottom - (double) (v - min) / (max - min) * (bottom - top);
	}

	@Override
	protected void paintBorder(Graphics g)
	{
	}
}
