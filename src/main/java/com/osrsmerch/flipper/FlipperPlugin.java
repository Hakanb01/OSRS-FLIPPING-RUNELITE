package com.osrsmerch.flipper;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;

/**
 * Flipper: sends your Grand Exchange trades to your own OSRS Flipping bot (only when you turn that on) and shows your profit.
 *
 * It only reads and shows. It does not click, place or change offers, fill in prices or quantities, mark widgets as "click
 * here", swap menu entries, or use reflection or JNI. The network is used only through RuneLite's OkHttpClient, from a worker
 * thread, never the client thread.
 */
@Slf4j
@PluginDescriptor(
	name = "OSRS Flipping",
	description = "Sends your Grand Exchange trades to your own OSRS Flipping Discord bot (opt-in) and shows your flipping profit",
	tags = {"flipping", "grand exchange", "merching", "profit", "discord"}
)
public class FlipperPlugin extends Plugin implements SendSettings
{
	/** Events in this many milliseconds after a login or world hop are the game replaying the slots, not new trades. */
	static final long LOGIN_BURST_MS = 5_000;
	private static final int SLOTS = 8;

	@Inject
	private Client client;
	@Inject
	private FlipperConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ItemManager itemManager;
	@Inject
	private TooltipManager tooltipManager;
	@Inject
	private FlipperApiClient apiClient;
	@Inject
	private ScheduledExecutorService executor;

	private FlipperState state;
	private OfferTracker tracker;
	private OfferOutbox outbox;
	private FlipperPanel panel;
	private NavigationButton navButton;
	private GeProfitOverlay geOverlay;
	private InventoryProfitTooltip inventoryTooltip;
	private ScheduledFuture<?> sendTask;
	private ScheduledFuture<?> summaryTask;

	private volatile long loginAtMs;
	private volatile long accountHash = -1;
	private volatile Long manualBudget;
	private volatile Summary summary;
	private volatile String summaryError;
	private volatile List<OfferRow> offerRows = new ArrayList<>();

	@Provides
	FlipperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlipperConfig.class);
	}

	FlipperConfig config()
	{
		return config;
	}

	@Override
	protected void startUp()
	{
		state = new FlipperState(new ConfigTrackingStore(configManager));
		state.load();
		tracker = new OfferTracker(state, () -> System.currentTimeMillis() / 1000);
		outbox = new OfferOutbox(state, apiClient, this, System::currentTimeMillis);

		panel = new FlipperPanel(this::setBudget, () -> run(outbox::retryNow), this::clearParked, () -> resolveHeld(true), () -> resolveHeld(false));
		navButton = NavigationButton.builder()
			.tooltip("OSRS Flipping")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		geOverlay = new GeProfitOverlay(client, this);
		inventoryTooltip = new InventoryProfitTooltip(client, this, itemManager, tooltipManager);
		overlayManager.add(geOverlay);
		overlayManager.add(inventoryTooltip);

		sendTask = executor.scheduleWithFixedDelay(this::sendTick, 1, 1, TimeUnit.SECONDS);
		summaryTask = executor.scheduleWithFixedDelay(this::summaryTick, 2, 60, TimeUnit.SECONDS);
		refreshPanel();
	}

	@Override
	protected void shutDown()
	{
		if (sendTask != null)
		{
			sendTask.cancel(false);
		}
		if (summaryTask != null)
		{
			summaryTask.cancel(false);
		}
		overlayManager.remove(geOverlay);
		overlayManager.remove(inventoryTooltip);
		clientToolbar.removeNavigation(navButton);
		if (state != null)
		{
			state.save();
		}
	}

	// ---- game events (client thread): read only

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			loginAtMs = System.currentTimeMillis();
			accountHash = client.getAccountHash();
		}
		else if (e.getGameState() == GameState.HOPPING)
		{
			loginAtMs = System.currentTimeMillis();
		}
		else if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			accountHash = -1;
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged e)
	{
		long hash = client.getAccountHash();
		if (hash == -1 || e.getSlot() < 0 || e.getSlot() >= SLOTS)
		{
			return;
		}
		accountHash = hash;
		// Right after login or a hop the game replays every slot (first as empty); while not logged in the slots read empty.
		boolean burst = System.currentTimeMillis() - loginAtMs < LOGIN_BURST_MS || client.getGameState() != GameState.LOGGED_IN;
		SavedOffer offer = SavedOffer.from(e.getOffer());
		String name = config.sendAccount() && client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		tracker.onOffer(hash, name, config.sendGp() ? coins() : null, e.getSlot(), offer, burst);
		offerRows = readRows();
		refreshPanel();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		// The saved tracking state lives in the same config group; its own writes are not a settings change. The refresh runs on
		// the worker, never inside the save that fired this event (that save holds the state lock).
		if (FlipperConfig.GROUP.equals(e.getGroup()) && !ConfigTrackingStore.KEY.equals(e.getKey()))
		{
			run(this::refreshPanel);
		}
	}

	/** Coins in the inventory, read once per offer event. Null when the inventory is not loaded. */
	private Long coins()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return null;
		}
		long total = 0;
		for (Item i : inv.getItems())
		{
			if (i.getId() == ItemID.COINS_995)
			{
				total += i.getQuantity();
			}
		}
		return total;
	}

	private List<OfferRow> readRows()
	{
		List<OfferRow> rows = new ArrayList<>();
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return rows;
		}
		for (int slot = 0; slot < offers.length; slot++)
		{
			SavedOffer o = SavedOffer.from(offers[slot]);
			if (o.getPhase() == Phase.EMPTY)
			{
				continue;
			}
			String name = itemManager.getItemComposition(o.getItemId()).getName();
			rows.add(new OfferRow(slot, o.getSide(), o.getPhase(), o.getItemId(), name, o.getQuantitySold(), o.getTotalQuantity(), o.getPrice(), profitPerItem(o)));
		}
		return rows;
	}

	// ---- helpers for the in-game displays

	/** Known average buy price per piece for the logged-in account. */
	OptionalLong knownCost(int itemId)
	{
		AccountState a = state == null || accountHash == -1 ? null : state.accountOrNull(Long.toString(accountHash));
		return a == null ? OptionalLong.empty() : a.getCostBasis().averageCost(itemId);
	}

	/** Profit per piece after tax for a sell offer; null for anything else or when the buy price is unknown. */
	Long profitPerItem(SavedOffer o)
	{
		if (o.getSide() != Side.SELL || o.getPhase() == Phase.EMPTY || o.getPrice() <= 0)
		{
			return null;
		}
		OptionalLong cost = knownCost(o.getItemId());
		return cost.isPresent() ? Tax.afterTax(o.getItemId(), o.getPrice()) - cost.getAsLong() : null;
	}

	// ---- worker thread: network

	private void sendTick()
	{
		try
		{
			outbox.tick();
			refreshPanel();
		}
		catch (RuntimeException e)
		{
			log.debug("OSRS Flipping: send tick failed: {}", e.getClass().getSimpleName());
		}
	}

	private void summaryTick()
	{
		try
		{
			if (!enabled() || baseUrl().isEmpty() || code().isEmpty())
			{
				return;
			}
			FlipperApiClient.SummaryResult r = apiClient.summary(this, effectiveBudget());
			if (r.summary != null)
			{
				summary = r.summary;
				summaryError = null;
			}
			else
			{
				summaryError = r.error;
			}
			refreshPanel();
		}
		catch (RuntimeException e)
		{
			log.debug("OSRS Flipping: summary failed: {}", e.getClass().getSimpleName());
		}
	}

	/** The budget typed in the panel; without it the bot uses the gp it was sent, if the player opted in. */
	private Long effectiveBudget()
	{
		return manualBudget;
	}

	private void setBudget(Long gp)
	{
		manualBudget = gp;
		run(this::summaryTick);
	}

	private void clearParked()
	{
		run(state::clearParked);
		run(this::refreshPanel);
	}

	private void resolveHeld(boolean send)
	{
		run(() -> outbox.resolveHeld(send));
		run(this::refreshPanel);
	}

	private void run(Runnable r)
	{
		executor.execute(r);
	}

	private void refreshPanel()
	{
		if (panel != null && outbox != null)
		{
			panel.update(new PanelModel(outbox.view(), enabled(), !baseUrl().isEmpty() && !code().isEmpty(), offerRows, summary, summaryError, manualBudget, config.sendGp()));
		}
	}

	// ---- SendSettings (read at every attempt; the code is never logged)

	@Override
	public boolean enabled()
	{
		return config.sendTrades();
	}

	@Override
	public String baseUrl()
	{
		String u = config.serverUrl();
		return u == null ? "" : u.trim();
	}

	@Override
	public String code()
	{
		String c = config.linkCode();
		return c == null ? "" : c.trim();
	}

	@Override
	public boolean includeAccount()
	{
		return config.sendAccount();
	}

	@Override
	public boolean includeGp()
	{
		return config.sendGp();
	}
}
