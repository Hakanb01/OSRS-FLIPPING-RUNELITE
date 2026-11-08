package com.osrsmerch.flipper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel. It only draws a {@link PanelModel}; nothing here touches the game or the network. Call {@link #update} from
 * any thread: it hops to the Swing thread itself.
 */
final class FlipperPanel extends PluginPanel
{
	static final String DISCLAIMER = "Not affiliated with Jagex. RuneScape and Old School RuneScape are trademarks of Jagex Limited. "
		+ "This plugin only reads and shows; you do every action in the game yourself.";

	private static final Color GREEN = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final Color RED = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color ORANGE = ColorScheme.PROGRESS_INPROGRESS_COLOR;

	private final JPanel content = new JPanel();
	private final Consumer<Long> onBudget;
	private final Runnable onRetry;
	private final Runnable onClearParked;
	private final Runnable onSendHeld;
	private final Runnable onDropHeld;
	private String budgetText = "";

	FlipperPanel(Consumer<Long> onBudget, Runnable onRetry, Runnable onClearParked, Runnable onSendHeld, Runnable onDropHeld)
	{
		super(false);
		this.onBudget = onBudget;
		this.onRetry = onRetry;
		this.onClearParked = onClearParked;
		this.onSendHeld = onSendHeld;
		this.onDropHeld = onDropHeld;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		add(content, BorderLayout.NORTH);
		update(new PanelModel(null, false, false, null, null, null, null, false));
	}

	void update(PanelModel m)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> update(m));
			return;
		}
		content.removeAll();
		content.add(title());
		content.add(linkCard(m));
		if (m.link != null && m.link.status == OfferOutbox.Status.HELD)
		{
			content.add(heldCard(m.link.heldMessages));
		}
		content.add(offersCard(m));
		content.add(adviceCard(m));
		content.add(profitCard(m));
		content.add(flipsCard(m));
		content.add(Box.createVerticalStrut(8));
		content.add(text(DISCLAIMER, ColorScheme.LIGHT_GRAY_COLOR, true));
		content.revalidate();
		content.repaint();
	}

	// ---- cards

	private Component title()
	{
		JLabel l = new JLabel("OSRS Flipping");
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		l.setAlignmentX(LEFT_ALIGNMENT);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		return l;
	}

	private JPanel card(String heading)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		p.setAlignmentX(LEFT_ALIGNMENT);
		JLabel h = new JLabel(heading);
		h.setFont(FontManager.getRunescapeSmallFont());
		h.setForeground(ColorScheme.BRAND_ORANGE);
		h.setAlignmentX(LEFT_ALIGNMENT);
		p.add(h);
		p.add(Box.createVerticalStrut(4));
		JPanel outer = new JPanel(new BorderLayout());
		outer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		outer.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		outer.setAlignmentX(LEFT_ALIGNMENT);
		outer.add(p, BorderLayout.CENTER);
		cards.put(p, outer);
		return p;
	}

	private final java.util.Map<JPanel, JPanel> cards = new java.util.IdentityHashMap<>();

	private Component done(JPanel p)
	{
		JPanel outer = cards.remove(p);
		return outer;
	}

	private static JLabel text(String s, Color c, boolean small)
	{
		JLabel l = new JLabel("<html><body style='width:170px'>" + escape(s) + "</body></html>");
		l.setFont(small ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont());
		l.setForeground(c);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static JPanel pair(String left, String right, Color rightColor)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		JLabel l = new JLabel(left);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JLabel r = new JLabel(right);
		r.setFont(FontManager.getRunescapeSmallFont());
		r.setForeground(rightColor);
		p.add(l, BorderLayout.WEST);
		p.add(r, BorderLayout.EAST);
		return p;
	}

	private static Color profitColor(long v)
	{
		return v > 0 ? GREEN : v < 0 ? RED : Color.WHITE;
	}

	private static JButton button(String label, Runnable action)
	{
		JButton b = new JButton(label);
		b.setFocusable(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	private Component linkCard(PanelModel m)
	{
		JPanel c = card("LINK");
		OfferOutbox.View v = m.link;
		String line;
		Color color;
		if (!m.sendingEnabled || v == null || v.status == OfferOutbox.Status.OFF)
		{
			line = !m.sendingEnabled ? "Sending is off. Turn it on in the plugin settings." : "Fill in the server address and link code in the settings.";
			color = ColorScheme.LIGHT_GRAY_COLOR;
		}
		else
		{
			switch (v.status)
			{
				case LINK_EXPIRED:
					line = OfferOutbox.EXPIRED_TEXT;
					color = RED;
					break;
				case BACKOFF:
					line = "Bot not reachable, trying again" + (v.error == null ? "" : ": " + v.error);
					color = ORANGE;
					break;
				case HELD:
					line = "On hold: the link changed while trades were waiting.";
					color = ORANGE;
					break;
				case WAITING:
					line = "Sending…";
					color = ORANGE;
					break;
				default:
					line = "Linked";
					color = GREEN;
					break;
			}
		}
		c.add(text(line, color, false));
		if (v != null)
		{
			c.add(pair("Waiting to send", Integer.toString(v.queued), v.queued > 0 ? ORANGE : Color.WHITE));
			if (v.parked > 0)
			{
				c.add(pair("Refused by the bot", Integer.toString(v.parked), RED));
				if (v.error != null)
				{
					c.add(text(v.error, RED, true));
				}
				c.add(button("Clear refused", onClearParked));
			}
			if (v.status == OfferOutbox.Status.LINK_EXPIRED || v.status == OfferOutbox.Status.BACKOFF)
			{
				c.add(button("Try again now", onRetry));
			}
		}
		return done(c);
	}

	private Component heldCard(int held)
	{
		JPanel c = card("NEW LINK");
		c.add(text(held + " trade(s) were waiting for the old link. Send them to the new link, or throw them away?", ORANGE, true));
		JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(button("Send", onSendHeld));
		row.add(button("Discard", onDropHeld));
		c.add(row);
		return done(c);
	}

	private Component offersCard(PanelModel m)
	{
		JPanel c = card("MY OFFERS");
		if (m.offers.isEmpty())
		{
			c.add(text("No offers in the Grand Exchange.", ColorScheme.LIGHT_GRAY_COLOR, true));
		}
		for (OfferRow o : m.offers)
		{
			boolean buy = o.side == Side.BUY;
			String head = (buy ? "Buy " : "Sell ") + o.itemName;
			c.add(text(head, Color.WHITE, true));
			Color bar = o.phase == Phase.CANCELLED ? RED : o.phase == Phase.DONE ? GREEN : ORANGE;
			c.add(new FillBar(o.fraction(), bar, o.quantitySold + " / " + o.totalQuantity));
			String sub = Money.exact(o.price) + " each";
			if (!buy)
			{
				sub += o.profitPerItem == null ? " · profit unknown" : " · " + Money.signed(o.profitPerItem) + " each";
			}
			c.add(text(sub, o.profitPerItem == null ? ColorScheme.LIGHT_GRAY_COLOR : profitColor(o.profitPerItem), true));
			c.add(Box.createVerticalStrut(4));
		}
		return done(c);
	}

	private Component adviceCard(PanelModel m)
	{
		JPanel c = card("BEST FLIP FOR YOUR GP");
		JTextField field = new JTextField(budgetText, 8);
		field.setToolTipText("Your budget, for example 25m. Used only for this advice; press Enter.");
		field.setAlignmentX(LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		if (m.manualBudget != null && budgetText.isEmpty())
		{
			field.setText(Money.shortGp(m.manualBudget));
		}
		field.addActionListener(e ->
		{
			budgetText = field.getText().trim();
			onBudget.accept(Money.parse(budgetText));
		});
		c.add(field);
		c.add(Box.createVerticalStrut(4));
		Summary s = m.summary;
		if (s == null)
		{
			c.add(text(m.summaryError != null ? m.summaryError : "Waiting for the bot.", ColorScheme.LIGHT_GRAY_COLOR, true));
			return done(c);
		}
		Summary.Advice a = s.advice;
		if (a == null || a.items.isEmpty())
		{
			String why = a == null ? "none" : String.valueOf(a.status);
			String msg = "no-budget".equals(why)
				? (m.gpOptIn ? "Your gp stack is not known yet." : "Type a budget above, or turn on 'Send gp stack' in the settings.")
				: "vip-only".equals(why) ? "This advice is for VIP members." : "No flip fits right now.";
			c.add(text(msg, ColorScheme.LIGHT_GRAY_COLOR, true));
			return done(c);
		}
		c.add(text("Budget " + Money.shortGp(Summary.gp(a.budget)) + (a.source == null ? "" : " (" + a.source + ")"), ColorScheme.LIGHT_GRAY_COLOR, true));
		for (Summary.AdviceItem i : a.items)
		{
			c.add(text(i.name + " ×" + i.quantity, Color.WHITE, true));
			c.add(text("Buy " + Money.exact(Summary.gp(i.buyPrice)) + " · sell " + Money.exact(Summary.gp(i.sellPrice)), ColorScheme.LIGHT_GRAY_COLOR, true));
			c.add(pair("Profit after tax", Money.signed(Summary.gp(i.netProfit)), profitColor(Summary.gp(i.netProfit))));
		}
		return done(c);
	}

	private Component profitCard(PanelModel m)
	{
		JPanel c = card("PROFIT (AFTER TAX)");
		Summary s = m.summary;
		if (s == null)
		{
			c.add(text("No data yet.", ColorScheme.LIGHT_GRAY_COLOR, true));
			return done(c);
		}
		long today = s.today == null ? 0 : Summary.gp(s.today.profit);
		c.add(pair("Today", Money.signed(today), profitColor(today)));
		long total = Summary.gp(s.totalProfit);
		c.add(pair("Total", Money.signed(total), profitColor(total)));
		if (s.unknownCostSales > 0)
		{
			c.add(text(s.unknownCostSales + " sale(s) with unknown cost are not counted.", ColorScheme.LIGHT_GRAY_COLOR, true));
		}
		c.add(Box.createVerticalStrut(4));
		c.add(new ProfitChart(s.chart));
		if (!s.accounts.isEmpty())
		{
			c.add(Box.createVerticalStrut(4));
			c.add(text("Per account", ORANGE, true));
			for (Summary.AccountLine a : s.accounts)
			{
				String name = a.accountName != null ? a.accountName : a.accountHash != null ? "Account " + a.accountHash : "Unknown account";
				c.add(pair(name, Money.signed(Summary.gp(a.today)), profitColor(Summary.gp(a.today))));
			}
		}
		if (!s.items.isEmpty())
		{
			c.add(Box.createVerticalStrut(4));
			c.add(text("Per item (today)", ORANGE, true));
			int shown = 0;
			for (Summary.ItemLine i : s.items)
			{
				if (shown++ >= 8)
				{
					break;
				}
				c.add(pair(i.name, Money.signed(Summary.gp(i.today)), profitColor(Summary.gp(i.today))));
			}
		}
		return done(c);
	}

	private Component flipsCard(PanelModel m)
	{
		JPanel c = card("LAST 10 FLIPS");
		Summary s = m.summary;
		List<Summary.FlipLine> flips = s == null ? java.util.Collections.emptyList() : s.lastFlips;
		if (flips.isEmpty())
		{
			c.add(text("No flips yet.", ColorScheme.LIGHT_GRAY_COLOR, true));
		}
		int n = 0;
		for (Summary.FlipLine f : flips)
		{
			if (n++ >= 10)
			{
				break;
			}
			String right = f.profit == null ? "unknown" : Money.signed(Summary.gp(f.profit));
			Color color = f.profit == null ? ColorScheme.LIGHT_GRAY_COLOR : profitColor(Summary.gp(f.profit));
			c.add(pair(f.name + " ×" + f.quantity, right, color));
		}
		return done(c);
	}
}
