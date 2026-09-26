package com.slayqueen;

import java.util.ArrayList;
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
 * 3. curated weapon list for the weapon slot
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
	}

	/**
	 * Owned items keyed by lower-case item name.
	 */
	private final Map<String, Pick> owned;

	public LoadoutCalculator(Map<String, Pick> ownedByLowerName)
	{
		this.owned = ownedByLowerName;
	}

	/**
	 * Tags where only special gear can damage the monster, so there is no generic fallback
	 * (e.g. kurask/turoth need leaf-bladed melee, broad ammo or Magic Dart).
	 */
	private static final String RESTRICTIVE_TAG = "leafy";

	/**
	 * @param stats equipment stats for an item id, or null when it isn't equipable/unknown
	 */
	public Loadout calculate(TaskInfo task, String style, GearData gear, IntFunction<EquipStats> stats)
	{
		// An AoE setup is useless without a weapon that can cast Ancients (or chinchompas),
		// so fall back to the task's normal style.
		if (AOE_MAGIC.equals(style) && findOwned(gear.aoe("WEAPON")) == null
			|| CHINCHOMPA.equals(style) && findOwned(gear.aoe("CHINCHOMPA")) == null)
		{
			List<String> styles = task.getStyles();
			String fallback = styles == null || styles.isEmpty() ? "MELEE" : styles.get(0);
			return calculate(task, fallback, gear, stats);
		}

		String weakness = task.getWeakness();
		Map<String, Pick> picks = new LinkedHashMap<>();
		String gearStyle = baseStyle(style);

		// Weapon first, because ammo and the shield depend on it
		Pick weapon;
		if (AOE_MAGIC.equals(style))
		{
			weapon = findOwned(gear.aoe("WEAPON"));
		}
		else if (CHINCHOMPA.equals(style))
		{
			weapon = findOwned(gear.aoe("CHINCHOMPA"));
		}
		else
		{
			weapon = pickWeapon(task, style, weakness, gear, stats);
		}
		if (weapon != null)
		{
			picks.put("WEAPON", weapon);
		}

		// Salve amulet for undead tasks
		Pick salve = task.tagsOrEmpty().contains("undead") ? pickSalve(gear, gearStyle) : null;

		for (String slot : SLOTS)
		{
			if ("WEAPON".equals(slot))
			{
				continue;
			}
			Pick pick;
			if ("AMMO".equals(slot))
			{
				pick = "RANGED".equals(style) ? pickAmmo(task, weapon, gear, stats) : null;
			}
			else if ("AMULET".equals(slot) && salve != null)
			{
				pick = salve;
			}
			else
			{
				// Always suggest the best slayer helmet/black mask owned, even alongside a salve
				pick = pickArmour(task, slot, style, gearStyle, weakness, gear, stats, true);
			}
			if (pick != null)
			{
				picks.put(slot, pick);
			}
		}

		if (weapon != null)
		{
			EquipStats ws = stats.apply(weapon.getItemId());
			if (ws != null && ws.isTwoHanded())
			{
				picks.remove("SHIELD");
			}
		}

		// Keep the display in equipment order
		Map<String, Pick> ordered = new LinkedHashMap<>();
		for (String slot : SLOTS)
		{
			if (picks.containsKey(slot))
			{
				ordered.put(slot, picks.get(slot));
			}
		}

		return new Loadout(style, ordered,
			statuses(task.getRequiredItems()),
			statuses(task.getRecommendedItems()),
			statuses(task.getLootItems()),
			statuses(task.getSupplies()),
			statuses(travelItems(task)));
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
