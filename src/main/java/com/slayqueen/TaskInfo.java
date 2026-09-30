package com.slayqueen;

import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * One slayer task's advice, loaded from tasks.json.
 */
@Data
public class TaskInfo
{
	private String name;
	/** PRAYER_POTIONS, FOOD or BOTH: which of these to bring under supplies. */
	private String supplyType;
	private List<String> styles;
	private String weakness;
	private List<String> tags;
	private List<ItemNeed> requiredItems;
	private List<ItemNeed> recommendedItems;
	private List<ItemNeed> lootItems;
	/** BURST, BARRAGE or CHINCHOMPA when the task is usually stacked and hit with AoE, else null. */
	private String aoe;
	/** YES when AoE is the standard method for the task, OPTIONAL when it's just viable. */
	private String aoeLevel;
	private String aoeWhere;
	private String aoeNote;
	private String cannon;
	private String cannonNote;
	private List<String> notes;

	/** Minimum Slayer level to damage the monster (0 if none). */
	private int slayerLevel;
	/** Quests, skills and diaries needed to reach the task. */
	private List<Requirement> requirements;
	/** Required items that a diary or quest removes the need for. */
	private List<Waiver> waivers;
	/** Konar location name -> extra needs for that location. */
	private java.util.Map<String, AreaInfo> konar;
	/** Advice when the task is from Krystilia (Wilderness only). */
	private WildyInfo wilderness;
	private List<Teleport> teleports;
	/** NONE, POISON or VENOM. */
	private String poison;
	private String poisonNote;
	/** Computed per player by TaskResolver, not loaded from data. */
	private transient List<ItemNeed> supplies;

	@Data
	public static class Requirement
	{
		/** QUEST, SKILL or DIARY. */
		private String type;
		private String quest;
		private String skill;
		private int level;
		private String diary;
		private String tier;
		private String reason;
		private boolean optional;
		/** The quest only needs to be started/partly done (e.g. Troll Stronghold for God Wars). */
		private boolean started;

		String label()
		{
			switch (type == null ? "" : type)
			{
				case "QUEST":
					return pretty(quest);
				case "SKILL":
					return level + " " + pretty(skill);
				case "DIARY":
					return pretty(tier) + " " + pretty(diary) + " diary";
				default:
					return reason == null ? "?" : reason;
			}
		}
	}

	@Data
	public static class Waiver
	{
		private String item;
		private String diary;
		private String tier;
		private String quest;
		private String skill;
		private int level;
		private String reason;
	}

	@Data
	public static class AreaInfo
	{
		private List<ItemNeed> requiredItems;
		private List<ItemNeed> recommendedItems;
		private String cannon;
		private String cannonNote;
		private List<String> notes;
	}

	@Data
	public static class WildyInfo
	{
		private String where;
		private boolean multicombat;
		private String cannon;
		private String cannonNote;
		private String aoe;
		private String aoeNote;
		private List<String> notes;
	}

	@Data
	public static class Teleport
	{
		private String item;
		private List<String> alternatives;
		private String code;
		private String destination;
		private String note;
	}

	/** "CABIN_FEVER" -> "Cabin fever". */
	static String pretty(String constant)
	{
		if (constant == null || constant.isEmpty())
		{
			return "";
		}
		String s = constant.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	@Data
	public static class ItemNeed
	{
		private String name;
		private List<String> alternatives;
		private String reason;

		List<String> allNames()
		{
			if (alternatives == null || alternatives.isEmpty())
			{
				return Collections.singletonList(name);
			}
			List<String> all = new java.util.ArrayList<>();
			all.add(name);
			all.addAll(alternatives);
			return all;
		}
	}

	/**
	 * The weakness only when it matters for the chosen style: stab/slash/crush for melee,
	 * elemental spells for magic. Null otherwise.
	 */
	String weaknessFor(String style)
	{
		if (weakness == null || style == null)
		{
			return null;
		}
		String w = weakness.toLowerCase(java.util.Locale.ROOT);
		boolean melee = w.equals("stab") || w.equals("slash") || w.equals("crush");
		boolean elemental = w.equals("air") || w.equals("water") || w.equals("earth") || w.equals("fire");
		if ("MELEE".equals(style) && melee)
		{
			return "weak to " + w;
		}
		if ("MAGIC".equals(style) && elemental)
		{
			return "weak to " + w + " spells";
		}
		return null;
	}

	/**
	 * Readable weakness: "Air spells" for elemental weaknesses, "Crush" etc. otherwise.
	 */
	String weaknessText()
	{
		if (weakness == null || weakness.isEmpty())
		{
			return null;
		}
		String w = weakness.toLowerCase(java.util.Locale.ROOT);
		String cap = Character.toUpperCase(w.charAt(0)) + w.substring(1);
		boolean elemental = w.equals("air") || w.equals("water") || w.equals("earth") || w.equals("fire");
		return elemental ? cap + " spells" : cap;
	}

	boolean aoeStandard()
	{
		return aoe != null && "YES".equals(aoeLevel);
	}

	/** "Barrage", "Optional (Barrage)" or "No". */
	String aoeText()
	{
		if (aoe == null)
		{
			return "No";
		}
		String method = aoe.charAt(0) + aoe.substring(1).toLowerCase(java.util.Locale.ROOT);
		return aoeStandard() ? method : "Optional (" + method + ")";
	}

	List<String> tagsOrEmpty()
	{
		return tags == null ? Collections.emptyList() : tags;
	}

	static <T> List<T> orEmpty(List<T> list)
	{
		return list == null ? Collections.emptyList() : list;
	}
}
