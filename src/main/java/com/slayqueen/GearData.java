package com.slayqueen;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Ranked gear lists (best first), loaded from gear.json.
 * styles: style -> slot -> item names.
 * special: task tag -> key -> item names, where key is a slot (e.g. AMULET)
 * or a style-specific weapon key (WEAPON_MELEE, WEAPON_RANGED, WEAPON_MAGIC).
 */
@Data
public class GearData
{
	private Map<String, Map<String, List<String>>> styles;
	private Map<String, Map<String, List<String>>> special;
	/** Ancient Magicks capable weapons and barrage gear, plus CHINCHOMPA. */
	private Map<String, List<String>> aoe;

	List<String> aoe(String key)
	{
		List<String> list = aoe == null ? null : aoe.get(key);
		return list == null ? Collections.emptyList() : list;
	}

	List<String> generic(String style, String slot)
	{
		Map<String, List<String>> slots = styles == null ? null : styles.get(style);
		List<String> list = slots == null ? null : slots.get(slot);
		return list == null ? Collections.emptyList() : list;
	}

	List<String> special(String tag, String key)
	{
		Map<String, List<String>> keys = special == null ? null : special.get(tag);
		List<String> list = keys == null ? null : keys.get(key);
		return list == null ? Collections.emptyList() : list;
	}
}
