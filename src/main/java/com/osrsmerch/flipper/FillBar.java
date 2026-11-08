package com.osrsmerch.flipper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** A thin bar that shows how much of an offer is filled, with the numbers in it. */
final class FillBar extends JComponent
{
	private final double fraction;
	private final Color color;
	private final String text;

	FillBar(double fraction, Color color, String text)
	{
		this.fraction = Math.max(0, Math.min(1, fraction));
		this.color = color;
		this.text = text;
		setFont(FontManager.getRunescapeSmallFont());
		setPreferredSize(new Dimension(10, 14));
		setMinimumSize(new Dimension(10, 14));
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRoundRect(0, 0, w, h, 6, 6);
		g.setColor(color);
		int fw = (int) Math.round(w * fraction);
		if (fw > 0)
		{
			g.fillRoundRect(0, 0, Math.max(fw, 6), h, 6, 6);
		}
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		g.setColor(Color.WHITE);
		g.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
		g.dispose();
	}

	@Override
	protected void paintBorder(Graphics g)
	{
	}
}
