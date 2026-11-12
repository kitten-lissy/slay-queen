package com.slayqueen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.Value;

/**
 * Picks the best gear a player owns for a task. Pure logic, no client access,
 * so it can be unit tested.
 *
 * Order of preference for each slot:
 * 1. task-specific gear (salve amulet for undead, leaf-bladed weapons, dragonhunter, ...)
 * 2. slayer helmet / black mask for the head slot
 * 3. for the weapon slot, the owned weapon with the highest expected damage against the task's
 *    monster (DpsCalculator), or the curated weapon list when the task has no monster data
 * 4. the owned item with the best real equipment bonuses for the style and the monster's weakness
 */
public class LoadoutCalculator
{
	/** Magic with Ancient Magicks (burst/barrage). Weapon must be able to cast Ancients. */
	static final String AOE_MAGIC = "AOE_MAGIC";
	/** Ranged with chinchompas. */
	static final String CHINCHOMPA = "CHINCHOMPA";

	static final String[] SLOTS = {
		"HEAD", "CAPE", "AMULET", "AMMO", "WEAPON", "BODY", "SHIELD", "LEGS", "GLOVES", "BOOTS", "RING"
	};

	private static final String[] UNUSABLE_SUFFIXES = {"(uncharged)", "(inactive)", "(broken)", "(empty)", "(deadman)", " (u)"};

	@Value
	public static class Pick
	{
		String name;
		int itemId;
	}

	@Value
	public static class NeedStatus
	{
		TaskInfo.ItemNeed need;
		/** The owned item that satisfies this need, or null if missing. */
		Pick owned;
	}

	@Value
	public static class Loadout
	{
		String style;
		Map<String, Pick> gear;
		List<NeedStatus> required;
		List<NeedStatus> recommended;
		List<NeedStatus> loot;
		List<NeedStatus> supplies;
		/** Teleport items to reach the task (fairy rings etc. are shown as text, not here). */
		List<NeedStatus> travel;
		/** A few words on why this style was picked, for the panel. */
		String reason;
		/** Expected damage per second of the loadout, or 0 when it couldn't be worked out. */
		double dps;
	}

	static final String REASON_DPS = "Best DPS with your gear";
	static final String REASON_USUAL = "Usual style for this task";

	/**
	 * Owned items keyed by lower-case item name.
	 */
	private final Map<String, Pick> owned;
	/** The player's levels, or null to skip the damage comparison and use the ranked lists. */
	private final DpsCalculator.Levels levels;

	public LoadoutCalculator(Map<String, Pick> ownedByLowerName)
	{
		this(ownedByLowerName, null);
	}

	public LoadoutCalculator(Map<String, Pick> ownedByLowerName, DpsCalculator.Levels levels)
	{
		this.owned = ownedByLowerName;
		this.levels = levels;
	}

	/**
	 * Tags where only special gear can damage the monster, so there is no generic fallback
	 * (e.g. kurask/turoth need leaf-bladed melee, broad ammo or Magic Dart).
	 */
	private static final String RESTRICTIVE_TAG = "leafy";

	/**
	 * The task's allowed style whose best owned loadout deals the most damage. Without monster
	 * data or levels this is the task's first listed style, as before.
	 */
	public Loadout best(TaskInfo task, GearData gear, IntFunction<EquipStats> stats)
	{
		List<String> styles = task.getStyles() == null || task.getStyles().isEmpty()
			? Collections.singletonList("MELEE") : task.getStyles();
		if (levels == null || task.getDefence() == null)
		{
			return calculate(task, styles.get(0), gear, stats, REASON_USUAL);
		}
		Loadout best = null;
		for (String style : styles)
		{
			Loadout l = calculate(task, style, gear, stats, REASON_DPS);
			if (best == null || l.getDps() > best.getDps())
			{
				best = l;
			}
		}
		if (best.getDps() <= 0)
		{
			return calculate(task, styles.get(0), gear, stats, REASON_USUAL);
		}
		return best;
	}

	/**
	 * @param stats equipment stats for an item id, or null when it isn't equipable/unknown
	 */
	public Loadout calculate(TaskInfo task, String style, GearData gear, IntFunction<EquipStats> stats)
	{
		return calculate(task, style, gear, stats, null);
	}

	public Loadout calculate(TaskInfo task, String style, GearData gear, IntFunction<EquipStats> stats, String reason)
	{
		// An AoE setup is useless without a weapon that can cast Ancients (or chinchompas),
		// so fall back to the best single-target style.
		if (AOE_MAGIC.equals(style) && findOwned(gear.aoe("WEAPON")) == null
			|| CHINCHOMPA.equals(style) && findOwned(gear.aoe("CHINCHOMPA")) == null)
		{
			Loadout fallback = best(task, gear, stats);
			String why = AOE_MAGIC.equals(style) ? "No Ancients weapon owned" : "No chinchompas owned";
			return withReason(fallback, why + ". " + fallback.getReason());
		}

		String weakness = task.getWeakness();
		String gearStyle = baseStyle(style);
		Map<String, Map<String, Pick>> armourCache = new HashMap<>();

		// Weapon first, because ammo and the shield depend on it
		Pick weapon;
		String armourWeakness = weakness;
		double dps = 0;
		if (AOE_MAGIC.equals(style))
		{
			weapon = findOwned(gear.aoe("WEAPON"));
		}
		else if (CHINCHOMPA.equals(style))
		{
			weapon = findOwned(gear.aoe("CHINCHOMPA"));
		}
		else if (usesDps(task))
		{
			WeaponChoice choice = bestWeapon(task, style, gear, stats, armourCache);
			if (choice != null)
			{
				weapon = choice.pick;
				dps = choice.dps;
				if (choice.attackType != null)
				{
					armourWeakness = choice.attackType;
				}
			}
			else
			{
				weapon = task.tagsOrEmpty().contains(RESTRICTIVE_TAG) ? null : pickWeapon(task, style, weakness, gear, stats);
			}
		}
		else
		{
			weapon = pickWeapon(task, style, weakness, gear, stats);
		}

		Map<String, Pick> ordered = assemble(task, style, weapon, armourWeakness, gear, stats, armourCache);

		return new Loadout(style, ordered,
			statuses(task.getRequiredItems()),
			statuses(task.getRecommendedItems()),
			statuses(task.getLootItems()),
			statuses(task.getSupplies()),
			statuses(travelItems(task)),
			reason,
			dps);
	}

	private static Loadout withReason(Loadout l, String reason)
	{
		return new Loadout(l.getStyle(), l.getGear(), l.getRequired(), l.getRecommended(), l.getLoot(),
			l.getSupplies(), l.getTravel(), reason, l.getDps());
	}

	private boolean usesDps(TaskInfo task)
	{
		return levels != null && task.getDefence() != null;
	}

	/**
	 * The full loadout around a weapon: armour (cached per style and attack type), salve,
	 * ammo, and no shield with a two-handed weapon. In equipment order.
	 */
	private Map<String, Pick> assemble(TaskInfo task, String style, Pick weapon, String weakness, GearData gear,
		IntFunction<EquipStats> stats, Map<String, Map<String, Pick>> armourCache)
	{
		String gearStyle = baseStyle(style);
		Map<String, Pick> armour = armourCache.computeIfAbsent(style + "|" + weakness, k ->
		{
			Map<String, Pick> out = new HashMap<>();
			// Salve amulet for undead tasks
			Pick salve = task.tagsOrEmpty().contains("undead") ? pickSalve(gear, gearStyle) : null;
			for (String slot : SLOTS)
			{
				if ("WEAPON".equals(slot) || "AMMO".equals(slot))
				{
					continue;
				}
				Pick pick = "AMULET".equals(slot) && salve != null
					? salve
					// Always suggest the best slayer helmet/black mask owned, even alongside a salve
					: pickArmour(task, slot, style, gearStyle, weakness, gear, stats, true);
				if (pick != null)
				{
					out.put(slot, pick);
				}
			}
			return out;
		});

		Map<String, Pick> picks = new HashMap<>(armour);
		if (weapon != null)
		{
			picks.put("WEAPON", weapon);
			EquipStats ws = stats.apply(weapon.getItemId());
			if (ws != null && ws.isTwoHanded())
			{
				picks.remove("SHIELD");
			}
		}
		Pick ammo = "RANGED".equals(style) ? pickAmmo(task, weapon, gear, stats) : null;
		if (ammo != null)
		{
			picks.put("AMMO", ammo);
		}

		Map<String, Pick> ordered = new LinkedHashMap<>();
		for (String slot : SLOTS)
		{
			if (picks.containsKey(slot))
			{
				ordered.put(slot, picks.get(slot));
			}
		}
		return ordered;
	}

	@Value
	private static class WeaponChoice
	{
		Pick pick;
		String attackType;
		double dps;
	}

	/**
	 * The owned weapon (with the gear around it) that deals the most expected damage to the
	 * task's monster. Task-specific weapons (leaf-bladed, dragonhunter, ...) still win outright.
	 * Null when nothing usable is owned.
	 */
	private WeaponChoice bestWeapon(TaskInfo task, String style, GearData gear, IntFunction<EquipStats> stats,
		Map<String, Map<String, Pick>> armourCache)
	{
		List<String> special = specialFor(task, gear, "WEAPON_" + style);
		special.addAll(specialFor(task, gear, "WEAPON"));
		Pick specialPick = findOwned(special);
		List<Pick> candidates;
		if (specialPick != null)
		{
			candidates = Collections.singletonList(specialPick);
		}
		else if (task.tagsOrEmpty().contains(RESTRICTIVE_TAG))
		{
			return null;
		}
		else
		{
			candidates = weaponCandidates(style, gear, stats);
		}

		int darts = bestDartStrength(stats);
		WeaponChoice best = null;
		for (Pick w : candidates)
		{
			EquipStats ws = stats.apply(w.getItemId());
			List<String> types = "MELEE".equals(style) && ws != null
				? DpsCalculator.meleeTypes(ws) : Collections.singletonList(null);
			for (String type : types)
			{
				Map<String, Pick> g = assemble(task, style, w, type != null ? type : task.getWeakness(), gear, stats, armourCache);
				// A bow or crossbow without ammo you own can't attack
				boolean noAmmo = "RANGED".equals(style) && ammoTypeFor(w.getName()) != null && !g.containsKey("AMMO");
				DpsCalculator.Result r = noAmmo
					? new DpsCalculator.Result(0, null)
					: DpsCalculator.evaluate(style, g, stats, task, levels, type, darts);
				// Strictly better only, so ties keep the curated order
				if (best == null || r.getDps() > best.getDps() + 1e-9)
				{
					best = new WeaponChoice(w, type, r.getDps());
				}
			}
		}
		if (best != null && best.getDps() <= 0 && specialPick == null)
		{
			// Nothing could be scored (e.g. missing stats); let the ranked list decide
			return null;
		}
		return best;
	}

	/**
	 * Owned weapons for a style: the curated list first (in its order), then anything else
	 * owned whose stats fit the style, sorted by name so the result is deterministic.
	 */
	private List<Pick> weaponCandidates(String style, GearData gear, IntFunction<EquipStats> stats)
	{
		List<Pick> out = new ArrayList<>();
		for (String name : gear.generic(style, "WEAPON"))
		{
			Pick p = findOwned(Collections.singletonList(name));
			if (p != null && !out.contains(p))
			{
				out.add(p);
			}
		}
		List<Pick> extra = new ArrayList<>();
		for (Pick p : owned.values())
		{
			if (out.contains(p) || !usable(p.getName()))
			{
				continue;
			}
			EquipStats s = stats.apply(p.getItemId());
			if (s != null && s.getSlot() == EquipStats.SLOT_WEAPON && style.equals(weaponStyle(p.getName(), s)))
			{
				extra.add(p);
			}
		}
		extra.sort(Comparator.comparing(Pick::getName));
		out.addAll(extra);
		return out;
	}

	/**
	 * Which style a weapon is for, judged by its name and bonuses: MELEE, RANGED, MAGIC,
	 * CHINCHOMPA, or null if it doesn't look like a weapon.
	 */
	static String weaponStyle(String name, EquipStats s)
	{
		String n = name.toLowerCase(Locale.ROOT);
		if (n.contains("chinchompa"))
		{
			return CHINCHOMPA;
		}
		int melee = Math.max(s.getAstab(), Math.max(s.getAslash(), s.getAcrush()));
		boolean magicName = n.contains("staff") || n.contains("wand") || n.contains("sceptre") || n.contains("trident")
			|| n.startsWith("tumeken's shadow") || n.startsWith("eye of ayak") || n.startsWith("blue moon spear");
		if (s.getAmagic() > 0 && magicName)
		{
			return "MAGIC";
		}
		if (s.getArange() > 0 && melee <= 0)
		{
			return "RANGED";
		}
		if (melee > 0 || s.getStr() > 0)
		{
			return "MELEE";
		}
		return null;
	}

	/**
	 * Ranged strength of the best darts owned, for a blowpipe; adamant darts when none are owned
	 * (the darts loaded in a blowpipe aren't visible to the plugin).
	 */
	private int bestDartStrength(IntFunction<EquipStats> stats)
	{
		int best = 0;
		for (Pick p : owned.values())
		{
			String n = p.getName().toLowerCase(Locale.ROOT);
			if (!n.matches("[a-z]+ dart(\\(.*\\))?") || n.startsWith("atlatl") || !usable(p.getName()))
			{
				continue;
			}
			EquipStats s = stats.apply(p.getItemId());
			if (s != null)
			{
				best = Math.max(best, s.getRstr());
			}
		}
		return best > 0 ? best : DpsCalculator.DEFAULT_DART_STRENGTH;
	}

	/**
	 * The plain style used for armour scoring and curated lists.
	 */
	static String baseStyle(String style)
	{
		if (AOE_MAGIC.equals(style))
		{
			return "MAGIC";
		}
		if (CHINCHOMPA.equals(style))
		{
			return "RANGED";
		}
		return style;
	}

	private List<String> specialFor(TaskInfo task, GearData gear, String key)
	{
		List<String> out = new ArrayList<>();
		for (String tag : task.tagsOrEmpty())
		{
			out.addAll(gear.special(tag, key));
		}
		return out;
	}

	/**
	 * Salve amulet for undead tasks. Only the imbued versions help ranged and magic.
	 */
	private Pick pickSalve(GearData gear, String style)
	{
		List<String> salves = new ArrayList<>();
		for (String name : gear.special("undead", "AMULET"))
		{
			if ("MELEE".equals(style) || name.toLowerCase(Locale.ROOT).contains("i)"))
			{
				salves.add(name);
			}
		}
		return findOwned(salves);
	}

	private Pick pickWeapon(TaskInfo task, String style, String weakness, GearData gear, IntFunction<EquipStats> stats)
	{
		List<String> special = specialFor(task, gear, "WEAPON_" + style);
		special.addAll(specialFor(task, gear, "WEAPON"));
		Pick pick = findOwned(special);
		if (pick != null)
		{
			return pick;
		}
		if (task.tagsOrEmpty().contains(RESTRICTIVE_TAG))
		{
			// Nothing else can damage these; the missing required item is shown instead
			return null;
		}

		pick = findOwned(gear.generic(style, "WEAPON"));
		if (pick != null)
		{
			return pick;
		}
		return bestByStats(EquipStats.SLOT_WEAPON, stats, s -> s.weaponScore(style, weakness));
	}

	private Pick pickArmour(TaskInfo task, String slot, String style, String gearStyle, String weakness,
		GearData gear, IntFunction<EquipStats> stats, boolean allowSlayerHead)
	{
		// Task-specific armour (e.g. dragonfire shields, Efaritay's aid) is melee-oriented
		if ("MELEE".equals(gearStyle))
		{
			Pick special = findOwned(specialFor(task, gear, slot));
			if (special != null)
			{
				return special;
			}
		}

		List<String> curated = gear.generic(gearStyle, slot);
		if ("HEAD".equals(slot) && allowSlayerHead)
		{
			// On task, a slayer helmet or black mask beats any other helmet.
			// Only the imbued versions boost ranged and magic.
			List<String> slayerHeads = new ArrayList<>();
			for (String name : curated)
			{
				String lower = name.toLowerCase(Locale.ROOT);
				boolean slayerHead = lower.contains("slayer helmet") || lower.contains("black mask");
				if (slayerHead && ("MELEE".equals(gearStyle) || lower.contains("(i)")))
				{
					slayerHeads.add(name);
				}
			}
			Pick head = findOwned(slayerHeads);
			if (head != null)
			{
				return head;
			}
		}

		if (AOE_MAGIC.equals(style))
		{
			Pick aoe = findOwned(gear.aoe(slot));
			if (aoe != null)
			{
				return aoe;
			}
		}
		else if (CHINCHOMPA.equals(style))
		{
			// Chinchompas live on accuracy: the most Ranged attack bonus, then Ranged strength
			Pick acc = bestByStats(EquipStats.slotIndex(slot), stats, s -> s.getArange() * 100.0 + s.getRstr());
			if (acc != null)
			{
				return acc;
			}
		}

		// Curated rankings first (they account for set effects), then real stats to fill gaps
		Pick pick = findOwned(withoutSlayerHeads(curated));
		if (pick != null)
		{
			return pick;
		}
		return bestByStats(EquipStats.slotIndex(slot), stats, s -> s.armourScore(gearStyle, weakness));
	}

	private static List<String> withoutSlayerHeads(List<String> names)
	{
		List<String> out = new ArrayList<>();
		for (String n : names)
		{
			String lower = n.toLowerCase(Locale.ROOT);
			if (!lower.contains("slayer helmet") && !lower.contains("black mask"))
			{
				out.add(n);
			}
		}
		return out;
	}

	private Pick pickAmmo(TaskInfo task, Pick weapon, GearData gear, IntFunction<EquipStats> stats)
	{
		String ammoType = weapon == null ? null : ammoTypeFor(weapon.getName());
		if (ammoType == null)
		{
			return null;
		}
		String weaponName = weapon.getName().toLowerCase(Locale.ROOT);

		// Task-specific ammo (e.g. broad bolts for kurask) if it fits the weapon
		for (String name : specialFor(task, gear, "AMMO"))
		{
			if (name.toLowerCase(Locale.ROOT).contains(ammoType))
			{
				Pick p = findOwned(List.of(name));
				if (p != null)
				{
					return p;
				}
			}
		}
		if (task.tagsOrEmpty().contains(RESTRICTIVE_TAG))
		{
			return null;
		}

		Pick best = null;
		double bestScore = 0;
		for (Pick p : owned.values())
		{
			String name = p.getName().toLowerCase(Locale.ROOT);
			if (!name.contains(ammoType) || !usable(p.getName()) || !canFire(weaponName, name))
			{
				continue;
			}
			EquipStats s = stats.apply(p.getItemId());
			if (s == null || s.getSlot() != EquipStats.SLOT_AMMO)
			{
				continue;
			}
			double score = s.getRstr() * 4.0 + s.getArange() + 1;
			if (score > bestScore)
			{
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	/**
	 * Dragon arrows and dragon bolts need a high-tier bow or crossbow.
	 */
	static boolean canFire(String weapon, String ammo)
	{
		if (ammo.startsWith("dragon arrow"))
		{
			return weapon.contains("twisted bow") || weapon.contains("dark bow") || weapon.contains("venator bow");
		}
		if (ammo.startsWith("dragon bolts") || ammo.startsWith("dragonstone") || ammo.startsWith("onyx") || ammo.startsWith("ruby dragon")
			|| ammo.startsWith("diamond dragon") || ammo.startsWith("opal dragon") || ammo.startsWith("emerald dragon"))
		{
			return weapon.contains("dragon") || weapon.contains("armadyl") || weapon.contains("zaryte");
		}
		return true;
	}

	/**
	 * Which ammo a ranged weapon fires, as a word found in the ammo's name, or null if it needs none
	 * (or uses something the plugin can't match, like ether or special bolts).
	 */
	static String ammoTypeFor(String weaponName)
	{
		String w = weaponName.toLowerCase(Locale.ROOT);
		if (w.contains("craw's") || w.contains("webweaver") || w.contains("sunlight") || w.contains("hunters'"))
		{
			return null;
		}
		if (w.contains("ballista"))
		{
			return "javelin";
		}
		if (w.contains("karil"))
		{
			return "bolt rack";
		}
		if (w.contains("crossbow"))
		{
			return "bolt";
		}
		if (w.contains("atlatl"))
		{
			return "atlatl dart";
		}
		if (w.contains("faerdhinen") || w.contains("crystal bow") || w.contains("blowpipe"))
		{
			return null;
		}
		if (w.contains("bow"))
		{
			return "arrow";
		}
		return null;
	}

	private interface Scorer
	{
		double score(EquipStats s);
	}

	private Pick bestByStats(int slotIndex, IntFunction<EquipStats> stats, Scorer scorer)
	{
		if (slotIndex < 0)
		{
			return null;
		}
		Pick best = null;
		double bestScore = 0;
		for (Pick p : owned.values())
		{
			if (!usable(p.getName()))
			{
				continue;
			}
			EquipStats s = stats.apply(p.getItemId());
			if (s == null || s.getSlot() != slotIndex)
			{
				continue;
			}
			double score = scorer.score(s);
			if (score > bestScore)
			{
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	/**
	 * Teleports that are items you carry (not fairy rings, spirit trees or spells).
	 */
	static List<TaskInfo.ItemNeed> travelItems(TaskInfo task)
	{
		List<TaskInfo.ItemNeed> out = new ArrayList<>();
		for (TaskInfo.Teleport t : TaskInfo.orEmpty(task.getTeleports()))
		{
			String item = t.getItem();
			if (item == null || item.equals("Fairy ring") || item.equals("Spirit tree") || item.equals("Spellbook") || item.equals("Minigame teleport"))
			{
				continue;
			}
			TaskInfo.ItemNeed n = new TaskInfo.ItemNeed();
			n.setName(item);
			n.setAlternatives(t.getAlternatives());
			n.setReason(t.getDestination());
			out.add(n);
		}
		return out;
	}

	private List<NeedStatus> statuses(List<TaskInfo.ItemNeed> needs)
	{
		List<NeedStatus> out = new ArrayList<>();
		for (TaskInfo.ItemNeed need : TaskInfo.orEmpty(needs))
		{
			out.add(new NeedStatus(need, findOwned(need.allNames())));
		}
		return out;
	}

	/**
	 * First candidate (in order) that the player owns. A candidate matches an owned item with
	 * the same name, or a variant of it such as "Slayer helmet (i)" or "Salve amulet(ei)".
	 * Uncharged/inactive versions are skipped.
	 */
	Pick findOwned(List<String> candidates)
	{
		for (String candidate : candidates)
		{
			if (candidate == null || candidate.isEmpty())
			{
				continue;
			}
			String c = candidate.toLowerCase(Locale.ROOT);
			Pick exact = owned.get(c);
			if (exact != null && usable(exact.getName()))
			{
				return exact;
			}
			for (Map.Entry<String, Pick> e : owned.entrySet())
			{
				if (isVariant(e.getKey(), c) && usable(e.getKey()))
				{
					return e.getValue();
				}
			}
		}
		return null;
	}

	static boolean isVariant(String ownedName, String candidate)
	{
		if (!ownedName.startsWith(candidate) || ownedName.length() == candidate.length())
		{
			return false;
		}
		char next = ownedName.charAt(candidate.length());
		return next == ' ' || next == '(';
	}

	static boolean usable(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		for (String suffix : UNUSABLE_SUFFIXES)
		{
			if (lower.contains(suffix))
			{
				return false;
			}
		}
		return !lower.startsWith("uncharged ");
	}
}
