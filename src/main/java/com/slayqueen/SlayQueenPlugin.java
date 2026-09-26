package com.slayqueen;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import javax.swing.SwingUtilities;

@Slf4j
@PluginDescriptor(
	name = "Slay Queen",
	description = "Shows the best gear you own for your slayer task, required items, prayer, and cannon rules",
	tags = {"slayer", "task", "gear", "loadout", "bis", "cannon", "prayer"}
)
@PluginDependency(BankTagsPlugin.class)
public class SlayQueenPlugin extends Plugin
{
	private static final String BANK_KEY = "bankItems";
	private static final int NEW_TASK_SECONDS = 90;
	private static final int BOSS_TASK_ID = 98;
	static final String BANK_TAG = "slayqueen";
	private static final String LAST_TASK_KEY = "lastTask";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Gson gson;

	@Inject
	private SlayQueenConfig config;

	@Inject
	private TagManager tagManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Icons icons;

	@Inject
	private AccountChecks accountChecks;

	private final TaskResolver resolver = new TaskResolver();

	private SlayQueenPanel panel;
	private NavigationButton navButton;

	@Inject
	private SlayQueenOverlay overlay;

	@Inject
	private ItemHighlightOverlay highlightOverlay;

	private Map<String, TaskInfo> tasksByKey = Collections.emptyMap();
	private GearData gearData;

	private int[] bankItemIds = new int[0];
	/** Owned item ids for every owned item name (lower case), so all doses/variants get highlighted. */
	private Map<String, Set<Integer>> ownedIdsByName = Collections.emptyMap();
	private boolean dirty;
	private boolean taskDirty;
	private boolean savingBank;

	/** The task's general advice, as loaded. */
	private volatile TaskInfo currentTask;
	/** The advice adapted to this player (Konar area, Wilderness, diaries); what gets displayed. */
	@Getter
	private volatile TaskInfo displayTask;
	@Getter
	private volatile List<TaskInfo.Requirement> unmetRequirements = Collections.emptyList();
	@Getter
	private volatile List<String> waivedItems = Collections.emptyList();
	@Getter
	private volatile String currentTaskName;
	@Getter
	private volatile String currentLocation;
	@Getter
	private volatile int remaining;
	@Getter
	private volatile LoadoutCalculator.Loadout loadout;
	@Getter
	private volatile Set<Integer> highlightIds = Collections.emptySet();

	@Getter
	private volatile boolean bankOpen;
	private volatile Instant showUntil = Instant.EPOCH;

	@Provides
	SlayQueenConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayQueenConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadData();
		overlayManager.add(overlay);
		overlayManager.add(highlightOverlay);
		panel = new SlayQueenPanel();
		navButton = NavigationButton.builder()
			.tooltip("Slay Queen")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		// Typing "tag:slayqueen" in the bank search shows only the loadout items
		tagManager.registerTag(BANK_TAG, itemId -> highlightIds.contains(itemId));
		clientThread.invokeLater(() ->
		{
			loadBankFromProfile();
			updateTask();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(highlightOverlay);
		tagManager.unregisterTag(BANK_TAG);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		bankItemIds = new int[0];
		ownedIdsByName = Collections.emptyMap();
		dirty = false;
		taskDirty = false;
		currentTask = null;
		currentTaskName = null;
		loadout = null;
		highlightIds = Collections.emptySet();
	}

	private void loadData()
	{
		TaskInfo[] tasks = readJson("tasks.json", TaskInfo[].class);
		Map<String, TaskInfo> byKey = new HashMap<>();
		if (tasks != null)
		{
			for (TaskInfo t : tasks)
			{
				byKey.put(key(t.getName()), t);
			}
		}
		tasksByKey = byKey;
		gearData = readJson("gear.json", GearData.class);
	}

	private <T> T readJson(String file, Class<T> type)
	{
		try (InputStream in = SlayQueenPlugin.class.getResourceAsStream(file))
		{
			if (in == null)
			{
				log.warn("Missing resource {}", file);
				return null;
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return gson.fromJson(reader, type);
			}
		}
		catch (Exception e)
		{
			log.warn("Unable to read {}", file, e);
			return null;
		}
	}

	/**
	 * Normalises a task name so "The Alchemical Hydra" and "ALCHEMICAL HYDRA" match.
	 */
	static String key(String name)
	{
		if (name == null)
		{
			return "";
		}
		String k = name.trim().toLowerCase(Locale.ROOT);
		return k.startsWith("the ") ? k.substring(4) : k;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			taskDirty = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			currentTask = null;
			currentTaskName = null;
			currentLocation = null;
			remaining = 0;
			loadout = null;
			highlightIds = Collections.emptySet();
			refreshPanel();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Saving the bank for a new profile fires this event before the value is written;
		// the bank we just read is already correct, so don't reload it.
		if (savingBank)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			loadBankFromProfile();
			dirty = true;
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Batch all changes into at most one recalculation per tick
		if (taskDirty)
		{
			taskDirty = false;
			updateTask();
		}
		else if (dirty)
		{
			dirty = false;
			recalculate();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varp = event.getVarpId();
		if (varp == VarPlayerID.SLAYER_COUNT || varp == VarPlayerID.SLAYER_TARGET || varp == VarPlayerID.SLAYER_AREA
			|| event.getVarbitId() == VarbitID.SLAYER_TARGET_BOSSID)
		{
			taskDirty = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			int[] ids = itemIds(event.getItemContainer());
			if (!java.util.Arrays.equals(ids, bankItemIds))
			{
				bankItemIds = ids;
				saveBankToProfile();
				dirty = true;
			}
		}
		else if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = false;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SlayQueenConfig.GROUP.equals(event.getGroup())
			&& !BANK_KEY.equals(event.getKey()) && !LAST_TASK_KEY.equals(event.getKey()))
		{
			clientThread.invokeLater(this::recalculate);
		}
	}

	boolean shouldShowOverlay()
	{
		if (currentTaskName == null)
		{
			return false;
		}
		switch (config.overlayMode())
		{
			case ALWAYS:
				return true;
			case BANK_ONLY:
				return bankOpen;
			default:
				return bankOpen || Instant.now().isBefore(showUntil);
		}
	}

	private void updateTask()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int amount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		String name = amount > 0 ? lookupTaskName() : null;
		String location = amount > 0 ? lookupArea() : null;

		// Only pop the overlay up for a genuinely new task, not on every login/world hop
		boolean changed = false;
		if (name != null)
		{
			String taskKey = name + ":" + client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
			String last = configManager.getRSProfileConfiguration(SlayQueenConfig.GROUP, LAST_TASK_KEY);
			if (!taskKey.equals(last))
			{
				changed = true;
				configManager.setRSProfileConfiguration(SlayQueenConfig.GROUP, LAST_TASK_KEY, taskKey);
			}
		}
		remaining = amount;
		currentTaskName = name;
		currentLocation = location;
		currentTask = name == null ? null : tasksByKey.get(key(name));
		if (changed)
		{
			showUntil = Instant.now().plusSeconds(NEW_TASK_SECONDS);
		}
		recalculate();
	}

	/**
	 * Reads the current task name from the game's slayer task table, the same way
	 * RuneLite's Slayer plugin does.
	 */
	private String lookupTaskName()
	{
		int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		int taskRow;
		if (taskId == BOSS_TASK_ID)
		{
			List<Integer> bossRows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (bossRows.isEmpty())
			{
				return null;
			}
			taskRow = (Integer) client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
		}
		else
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
			if (rows.isEmpty())
			{
				return null;
			}
			taskRow = rows.get(0);
		}
		return (String) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
	}

	private String lookupArea()
	{
		int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		if (areaId <= 0)
		{
			return null;
		}
		List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
		if (rows.isEmpty())
		{
			return null;
		}
		return (String) client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
	}

	/**
	 * Rebuilds the loadout from everything the player owns. Must run on the client thread
	 * because it reads item definitions.
	 */
	private void recalculate()
	{
		if (panel == null)
		{
			// Plugin is shut down
			return;
		}
		TaskInfo base = currentTask;
		if (base == null || gearData == null)
		{
			displayTask = null;
			unmetRequirements = Collections.emptyList();
			waivedItems = Collections.emptyList();
			loadout = null;
			highlightIds = Collections.emptySet();
			refreshPanel();
			return;
		}

		TaskResolver.Resolved resolved = resolver.resolve(base, currentLocation, isWildernessTask(), accountChecks);
		TaskInfo task = resolved.getTask();
		displayTask = task;
		unmetRequirements = resolved.getUnmet();
		waivedItems = resolved.getWaived();

		Map<String, LoadoutCalculator.Pick> owned = new HashMap<>();
		Map<String, Set<Integer>> allIds = new HashMap<>();
		addOwned(owned, allIds, bankItemIds);
		addOwned(owned, allIds, itemIds(client.getItemContainer(InventoryID.INV)));
		addOwned(owned, allIds, itemIds(client.getItemContainer(InventoryID.WORN)));
		ownedIdsByName = allIds;

		LoadoutCalculator.Loadout result = new LoadoutCalculator(owned)
			.calculate(task, chooseStyle(task), gearData, this::equipStats);

		// Highlight every id with a picked name, so all potion doses and duplicates light up
		Set<Integer> ids = new HashSet<>();
		result.getGear().values().forEach(p -> addIds(ids, allIds, p));
		for (List<LoadoutCalculator.NeedStatus> list : List.of(result.getRequired(), result.getRecommended(), result.getLoot(),
			result.getSupplies(), result.getTravel()))
		{
			for (LoadoutCalculator.NeedStatus s : list)
			{
				if (s.getOwned() != null)
				{
					addIds(ids, allIds, s.getOwned());
				}
			}
		}

		loadout = result;
		highlightIds = ids;
		refreshPanel();
	}

	private java.awt.image.BufferedImage itemImageForWeakness(String weakness)
	{
		if (weakness == null)
		{
			return null;
		}
		switch (weakness.toLowerCase(Locale.ROOT))
		{
			case "air":
				return itemManager.getImage(net.runelite.api.gameval.ItemID.AIRRUNE);
			case "water":
				return itemManager.getImage(net.runelite.api.gameval.ItemID.WATERRUNE);
			case "earth":
				return itemManager.getImage(net.runelite.api.gameval.ItemID.EARTHRUNE);
			case "fire":
				return itemManager.getImage(net.runelite.api.gameval.ItemID.FIRERUNE);
			default:
				return null;
		}
	}

	private void refreshPanel()
	{
		SlayQueenPanel p = panel;
		if (p == null)
		{
			return;
		}
		TaskInfo task = displayTask;
		LoadoutCalculator.Loadout l = loadout;
		SlayQueenPanel.View view = SlayQueenPanel.View.builder()
			.unmet(unmetRequirements)
			.waived(waivedItems)
			.taskName(currentTaskName)
			.location(currentLocation)
			.remaining(remaining)
			.task(task)
			.loadout(l)
			.theme(config.theme())
			.prayerIcon(task == null ? null : icons.prayer(task.getPrayer()))
			.sustainIcon(task == null ? null : icons.sustain(task.getSustain()))
			.cannonIcon(itemManager.getImage(net.runelite.api.gameval.ItemID.MCANNONBALL))
			.aoeIcon(task == null ? null : icons.aoe(task.getAoe() == null ? "BARRAGE" : task.getAoe()))
			.styleIcon(icons.style(l == null ? null : l.getStyle()))
			.weaknessIcon(task == null ? null : itemImageForWeakness(task.getWeakness()))
			.itemImage(itemManager::getImage)
			.itemImageByName(name ->
			{
				int id = icons.idForName(name);
				return id < 0 ? null : itemManager.getImage(id);
			})
			.build();
		SwingUtilities.invokeLater(() -> p.display(view));
	}


	/**
	 * Slayer master varbit value for Krystilia (same constant as RuneLite's Slayer plugin).
	 */
	static final int KRYSTILIA = 7;

	private boolean isWildernessTask()
	{
		return client.getVarbitValue(VarbitID.SLAYER_MASTER) == KRYSTILIA;
	}

	private String chooseStyle(TaskInfo task)
	{
		SlayQueenConfig.StyleChoice choice = config.style();
		if (choice != SlayQueenConfig.StyleChoice.AUTO)
		{
			return choice.name();
		}
		if (config.preferAoe() && task.aoeStandard())
		{
			return "CHINCHOMPA".equals(task.getAoe()) ? LoadoutCalculator.CHINCHOMPA : LoadoutCalculator.AOE_MAGIC;
		}
		List<String> styles = task.getStyles();
		return styles == null || styles.isEmpty() ? "MELEE" : styles.get(0);
	}

	/**
	 * Equipment bonuses from RuneLite's item stats, or null if the item can't be equipped.
	 */
	private EquipStats equipStats(int itemId)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null || !stats.isEquipable() || stats.getEquipment() == null)
		{
			return null;
		}
		ItemEquipmentStats e = stats.getEquipment();
		return EquipStats.builder()
			.slot(e.getSlot())
			.twoHanded(e.isTwoHanded())
			.astab(e.getAstab())
			.aslash(e.getAslash())
			.acrush(e.getAcrush())
			.amagic(e.getAmagic())
			.arange(e.getArange())
			.str(e.getStr())
			.rstr(e.getRstr())
			.mdmg(e.getMdmg())
			.prayer(e.getPrayer())
			.aspeed(e.getAspeed())
			.build();
	}

	private static void addIds(Set<Integer> out, Map<String, Set<Integer>> allIds, LoadoutCalculator.Pick pick)
	{
		out.add(pick.getItemId());
		Set<Integer> same = allIds.get(pick.getName().toLowerCase(Locale.ROOT));
		if (same != null)
		{
			out.addAll(same);
		}
		// Other doses of a potion, e.g. "Prayer potion(4)" and "Prayer potion(3)"
		String base = pick.getName().toLowerCase(Locale.ROOT).replaceAll("\\(\\d\\)$", "");
		if (!base.equals(pick.getName().toLowerCase(Locale.ROOT)))
		{
			for (Map.Entry<String, Set<Integer>> e : allIds.entrySet())
			{
				if (e.getKey().startsWith(base + "(") && e.getKey().matches(".*\\(\\d\\)$"))
				{
					out.addAll(e.getValue());
				}
			}
		}
	}

	private void addOwned(Map<String, LoadoutCalculator.Pick> owned, Map<String, Set<Integer>> allIds, int[] ids)
	{
		for (int id : ids)
		{
			if (id <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(id);
			if (comp.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			int realId = comp.getNote() != -1 ? comp.getLinkedNoteId() : id;
			String name = comp.getName();
			if (name != null)
			{
				String lower = name.toLowerCase(Locale.ROOT);
				owned.putIfAbsent(lower, new LoadoutCalculator.Pick(name, realId));
				allIds.computeIfAbsent(lower, k -> new HashSet<>()).add(realId);
			}
		}
	}

	private static int[] itemIds(ItemContainer container)
	{
		if (container == null)
		{
			return new int[0];
		}
		Item[] items = container.getItems();
		int[] ids = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i].getId();
		}
		return ids;
	}

	private void saveBankToProfile()
	{
		StringBuilder sb = new StringBuilder();
		for (int id : bankItemIds)
		{
			if (id > 0)
			{
				if (sb.length() > 0)
				{
					sb.append(',');
				}
				sb.append(id);
			}
		}
		savingBank = true;
		try
		{
			configManager.setRSProfileConfiguration(SlayQueenConfig.GROUP, BANK_KEY, sb.toString());
		}
		finally
		{
			savingBank = false;
		}
	}

	private void loadBankFromProfile()
	{
		String saved = configManager.getRSProfileConfiguration(SlayQueenConfig.GROUP, BANK_KEY);
		if (saved == null || saved.isEmpty())
		{
			bankItemIds = new int[0];
			return;
		}
		String[] parts = saved.split(",");
		int[] ids = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			try
			{
				ids[i] = Integer.parseInt(parts[i].trim());
			}
			catch (NumberFormatException e)
			{
				ids[i] = -1;
			}
		}
		bankItemIds = ids;
	}
}
