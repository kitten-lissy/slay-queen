package com.slayqueen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Adapts a task's general advice to the player's situation: Konar's assigned area, a Wilderness
 * (Krystilia) task, diaries that waive items, and requirements the account hasn't met yet.
 * Pure logic so it can be unit tested.
 */
public class TaskResolver
{
	/** What the resolver needs to know about the account. */
	public interface Account
	{
		boolean met(TaskInfo.Requirement requirement);

		int slayerLevel();
	}

	@Value
	public static class Resolved
	{
		TaskInfo task;
		/** Non-optional requirements the account hasn't met, including the Slayer level. */
		List<TaskInfo.Requirement> unmet;
		/** Items the account doesn't need thanks to a diary/quest, with the reason. */
		List<String> waived;
	}

	private static final List<String> ANTIPOISON = Arrays.asList("Antidote++", "Antidote+", "Superantipoison", "Anti-venom+",
		"Anti-venom", "Extended anti-venom+", "Serpentine helm");
	private static final List<String> ANTIVENOM = Arrays.asList("Extended anti-venom+", "Anti-venom", "Serpentine helm",
		"Tanzanite helm", "Magma helm");
	private static final List<String> FOOD = Arrays.asList("Manta ray", "Anglerfish", "Cooked karambwan", "Dark crab",
		"Sea turtle", "Monkfish", "Swordfish", "Lobster", "Tuna", "Salmon");
	private static final List<String> PRAYER = Arrays.asList("Super restore", "Prayer regeneration potion", "Sanfew serum");
	private static final List<String> ANTIFIRE = Arrays.asList("Extended super antifire", "Super antifire potion",
		"Extended antifire", "Antifire potion");

	public Resolved resolve(TaskInfo base, String area, boolean wilderness, Account account)
	{
		TaskInfo t = copy(base);
		List<String> waived = new ArrayList<>();

		if (area != null && base.getKonar() != null)
		{
			TaskInfo.AreaInfo a = findArea(base.getKonar(), area);
			if (a != null)
			{
				addAll(t.getRequiredItems(), a.getRequiredItems());
				addAll(t.getRecommendedItems(), a.getRecommendedItems());
				if (a.getCannon() != null)
				{
					t.setCannon(a.getCannon());
					t.setCannonNote(a.getCannonNote());
				}
				prepend(t.getNotes(), a.getNotes());
			}
		}

		if (wilderness)
		{
			applyWilderness(t, base);
		}

		for (TaskInfo.Waiver w : TaskInfo.orEmpty(base.getWaivers()))
		{
			if (waiverMet(w, account) && removeRequired(t, w.getItem()))
			{
				waived.add(w.getItem() + (w.getReason() == null ? "" : " (" + w.getReason() + ")"));
			}
		}

		addPoisonCure(t);
		t.setSupplies(supplies(t));

		List<TaskInfo.Requirement> unmet = new ArrayList<>();
		if (base.getSlayerLevel() > 0 && account.slayerLevel() < base.getSlayerLevel())
		{
			TaskInfo.Requirement r = new TaskInfo.Requirement();
			r.setType("SKILL");
			r.setSkill("SLAYER");
			r.setLevel(base.getSlayerLevel());
			r.setReason("to damage them");
			unmet.add(r);
		}
		for (TaskInfo.Requirement r : TaskInfo.orEmpty(base.getRequirements()))
		{
			if (!r.isOptional() && !account.met(r))
			{
				unmet.add(r);
			}
		}
		return new Resolved(t, unmet, waived);
	}

	private void applyWilderness(TaskInfo t, TaskInfo base)
	{
		List<String> tags = new ArrayList<>(t.getTags());
		if (!tags.contains("wilderness"))
		{
			tags.add("wilderness");
		}
		t.setTags(tags);

		TaskInfo.WildyInfo w = base.getWilderness();
		List<String> notes = new ArrayList<>();
		notes.add("Krystilia task: kills only count in the Wilderness");
		if (w != null)
		{
			if (w.getWhere() != null)
			{
				notes.add("Go to " + w.getWhere());
			}
			notes.addAll(TaskInfo.orEmpty(w.getNotes()));
			if (w.getCannon() != null)
			{
				t.setCannon(w.getCannon());
				t.setCannonNote(w.getCannonNote());
			}
			// Only Wilderness AoE spots count
			t.setAoe(w.getAoe());
			t.setAoeWhere(w.getAoe() == null ? null : w.getWhere());
			t.setAoeNote(w.getAoeNote());
			t.setAoeLevel(w.getAoe() == null ? null : (base.aoeStandard() ? "YES" : "OPTIONAL"));
		}
		else
		{
			t.setAoe(null);
			t.setAoeWhere(null);
			t.setAoeNote(null);
			t.setAoeLevel(null);
		}
		notes.addAll(t.getNotes());
		t.setNotes(notes);

		if (t.getLootItems().stream().noneMatch(n -> "Looting bag".equals(n.getName())))
		{
			t.getLootItems().add(0, need("Looting bag", "Wilderness: store loot, and less to lose", null));
		}
	}

	private static final java.util.Set<String> GENERIC_WORDS = new java.util.HashSet<>(Arrays.asList(
		"the", "of", "slayer", "cave", "caves", "cavern", "dungeon", "area", "lair", "south", "north", "east", "west"));

	/**
	 * Konar location names differ slightly between the wiki and the game ("Stronghold Slayer Cave" vs
	 * "Stronghold Slayer Dungeon"), so match on the distinctive words only.
	 */
	static TaskInfo.AreaInfo findArea(Map<String, TaskInfo.AreaInfo> areas, String area)
	{
		java.util.Set<String> want = words(area);
		TaskInfo.AreaInfo best = null;
		for (Map.Entry<String, TaskInfo.AreaInfo> e : areas.entrySet())
		{
			if (e.getKey().equalsIgnoreCase(area))
			{
				return e.getValue();
			}
			java.util.Set<String> have = words(e.getKey());
			if (!want.isEmpty() && !have.isEmpty() && (have.containsAll(want) || want.containsAll(have)))
			{
				best = e.getValue();
			}
		}
		return best;
	}

	static java.util.Set<String> words(String name)
	{
		java.util.Set<String> out = new java.util.HashSet<>();
		for (String w : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
		{
			if (!w.isEmpty() && !GENERIC_WORDS.contains(w))
			{
				out.add(w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w);
			}
		}
		return out;
	}

	private static boolean waiverMet(TaskInfo.Waiver w, Account account)
	{
		TaskInfo.Requirement r = new TaskInfo.Requirement();
		if (w.getDiary() != null)
		{
			r.setType("DIARY");
			r.setDiary(w.getDiary());
			r.setTier(w.getTier());
		}
		else if (w.getQuest() != null)
		{
			r.setType("QUEST");
			r.setQuest(w.getQuest());
		}
		else if (w.getSkill() != null)
		{
			r.setType("SKILL");
			r.setSkill(w.getSkill());
			r.setLevel(w.getLevel());
		}
		else
		{
			return false;
		}
		return account.met(r);
	}

	private static boolean removeRequired(TaskInfo t, String item)
	{
		if (item == null)
		{
			return false;
		}
		int before = t.getRequiredItems().size();
		t.getRequiredItems().removeIf(n -> item.equalsIgnoreCase(n.getName())
			|| TaskInfo.orEmpty(n.getAlternatives()).stream().anyMatch(item::equalsIgnoreCase));
		return t.getRequiredItems().size() < before;
	}

	private static void addPoisonCure(TaskInfo t)
	{
		boolean venom = "VENOM".equals(t.getPoison());
		boolean poison = "POISON".equals(t.getPoison());
		if (!venom && !poison)
		{
			return;
		}
		List<String> cures = venom ? ANTIVENOM : ANTIPOISON;
		boolean already = mentions(t.getRequiredItems(), cures) || mentions(t.getRecommendedItems(), cures)
			|| mentions(t.getRecommendedItems(), Arrays.asList("Antipoison", "Anti-venom+", "Antidote++"));
		if (!already)
		{
			String main = venom ? "Anti-venom+" : "Antipoison";
			t.getRecommendedItems().add(0, need(main, venom ? "they envenom you" : "they poison you", cures));
		}
	}

	/**
	 * General supplies for the task: what to sustain with, antifire for dragons, cannon, and runes for AoE.
	 */
	static List<TaskInfo.ItemNeed> supplies(TaskInfo t)
	{
		List<TaskInfo.ItemNeed> out = new ArrayList<>();
		if ("PRAY".equals(t.getSustain()) || "EITHER".equals(t.getSustain()))
		{
			out.add(need("Prayer potion", "to keep praying", PRAYER));
		}
		if (!"PRAY".equals(t.getSustain()) || "EITHER".equals(t.getSustain()))
		{
			out.add(need("Shark", "food", FOOD));
		}
		boolean dragon = t.getTags().contains("dragon");
		if (dragon && !mentions(t.getRequiredItems(), ANTIFIRE) && !mentions(t.getRecommendedItems(), ANTIFIRE))
		{
			out.add(need("Antifire potion", "dragonfire", ANTIFIRE));
		}
		if ("YES".equals(t.getCannon()) || "SOME".equals(t.getCannon()))
		{
			out.add(need("Cannonball", "if you cannon", Arrays.asList("Granite cannonball")));
		}
		if (t.aoeStandard() && !"CHINCHOMPA".equals(t.getAoe()))
		{
			out.add(need("Rune pouch", "for Ancient Magicks runes", Arrays.asList("Divine rune pouch")));
			out.add(need("Blood rune", "Barrage/Burst runes (with death and water runes)", null));
		}
		return out;
	}

	private static boolean mentions(List<TaskInfo.ItemNeed> needs, List<String> names)
	{
		for (TaskInfo.ItemNeed n : TaskInfo.orEmpty(needs))
		{
			for (String name : n.allNames())
			{
				for (String candidate : names)
				{
					if (name.equalsIgnoreCase(candidate))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	private static TaskInfo.ItemNeed need(String name, String reason, List<String> alternatives)
	{
		TaskInfo.ItemNeed n = new TaskInfo.ItemNeed();
		n.setName(name);
		n.setReason(reason);
		n.setAlternatives(alternatives == null ? new ArrayList<>() : new ArrayList<>(alternatives));
		return n;
	}

	private static void addAll(List<TaskInfo.ItemNeed> into, List<TaskInfo.ItemNeed> extra)
	{
		for (TaskInfo.ItemNeed n : TaskInfo.orEmpty(extra))
		{
			if (into.stream().noneMatch(x -> x.getName().equalsIgnoreCase(n.getName())))
			{
				into.add(n);
			}
		}
	}

	private static void prepend(List<String> into, List<String> extra)
	{
		into.addAll(0, TaskInfo.orEmpty(extra));
	}

	/**
	 * Copy with fresh lists, so resolving never changes the loaded data.
	 */
	static TaskInfo copy(TaskInfo b)
	{
		TaskInfo t = new TaskInfo();
		t.setName(b.getName());
		t.setPrayer(b.getPrayer());
		t.setPrayerNote(b.getPrayerNote());
		t.setSustain(b.getSustain());
		t.setSustainNote(b.getSustainNote());
		t.setStyles(b.getStyles());
		t.setWeakness(b.getWeakness());
		t.setTags(new ArrayList<>(TaskInfo.orEmpty(b.getTags())));
		t.setRequiredItems(new ArrayList<>(TaskInfo.orEmpty(b.getRequiredItems())));
		t.setRecommendedItems(new ArrayList<>(TaskInfo.orEmpty(b.getRecommendedItems())));
		t.setLootItems(new ArrayList<>(TaskInfo.orEmpty(b.getLootItems())));
		t.setAoe(b.getAoe());
		t.setAoeLevel(b.getAoeLevel());
		t.setAoeWhere(b.getAoeWhere());
		t.setAoeNote(b.getAoeNote());
		t.setCannon(b.getCannon());
		t.setCannonNote(b.getCannonNote());
		t.setNotes(new ArrayList<>(TaskInfo.orEmpty(b.getNotes())));
		t.setSlayerLevel(b.getSlayerLevel());
		t.setRequirements(b.getRequirements());
		t.setWaivers(b.getWaivers());
		t.setKonar(b.getKonar());
		t.setWilderness(b.getWilderness());
		t.setTeleports(b.getTeleports());
		t.setPoison(b.getPoison());
		t.setPoisonNote(b.getPoisonNote());
		return t;
	}
}
