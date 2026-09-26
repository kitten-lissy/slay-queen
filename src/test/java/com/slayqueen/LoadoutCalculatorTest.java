package com.slayqueen;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LoadoutCalculatorTest
{
	private static Map<String, LoadoutCalculator.Pick> owned(String... names)
	{
		Map<String, LoadoutCalculator.Pick> map = new HashMap<>();
		int id = 1;
		for (String n : names)
		{
			map.put(n.toLowerCase(Locale.ROOT), new LoadoutCalculator.Pick(n, id++));
		}
		return map;
	}

	private static GearData gear()
	{
		GearData g = new GearData();
		Map<String, List<String>> melee = new HashMap<>();
		melee.put("HEAD", Arrays.asList("Slayer helmet (i)", "Slayer helmet", "Neitiznot faceguard", "Helm of neitiznot"));
		melee.put("WEAPON", Arrays.asList("Abyssal whip", "Dragon scimitar"));
		melee.put("SHIELD", Arrays.asList("Dragon defender"));
		melee.put("AMULET", Arrays.asList("Amulet of torture", "Amulet of glory"));
		Map<String, Map<String, List<String>>> styles = new HashMap<>();
		styles.put("MELEE", melee);
		g.setStyles(styles);

		Map<String, List<String>> undead = new HashMap<>();
		undead.put("AMULET", Arrays.asList("Salve amulet(ei)", "Salve amulet"));
		Map<String, List<String>> leafy = new HashMap<>();
		leafy.put("WEAPON_MELEE", Arrays.asList("Leaf-bladed battleaxe", "Leaf-bladed sword"));
		Map<String, Map<String, List<String>>> special = new HashMap<>();
		special.put("undead", undead);
		special.put("leafy", leafy);
		g.setSpecial(special);
		return g;
	}

	private static TaskInfo task(String... tags)
	{
		TaskInfo t = new TaskInfo();
		t.setName("Test");
		t.setStyles(Collections.singletonList("MELEE"));
		t.setTags(Arrays.asList(tags));
		return t;
	}

	@Test
	public void picksBestOwnedPerSlot()
	{
		LoadoutCalculator calc = new LoadoutCalculator(owned("Helm of neitiznot", "Dragon scimitar", "Abyssal whip"));
		LoadoutCalculator.Loadout l = calc.calculate(task(), "MELEE", gear(), id -> null);
		assertEquals("Helm of neitiznot", l.getGear().get("HEAD").getName());
		assertEquals("Abyssal whip", l.getGear().get("WEAPON").getName());
		assertNull("Nothing owned for amulet", l.getGear().get("AMULET"));
	}

	@Test
	public void matchesVariantsOfAName()
	{
		LoadoutCalculator calc = new LoadoutCalculator(owned("Slayer helmet (i)"));
		// Candidate "Slayer helmet" should also accept the imbued variant
		assertNotNull(calc.findOwned(Collections.singletonList("Slayer helmet")));
		// But a different item that merely starts with the same letters must not match
		assertFalse(LoadoutCalculator.isVariant("abyssal whipper", "abyssal whip"));
		assertTrue(LoadoutCalculator.isVariant("salve amulet(ei)", "salve amulet"));
	}

	@Test
	public void taskTagsPreferSpecialGear()
	{
		LoadoutCalculator calc = new LoadoutCalculator(owned("Amulet of torture", "Salve amulet(ei)", "Abyssal whip", "Leaf-bladed sword"));
		LoadoutCalculator.Loadout undead = calc.calculate(task("undead"), "MELEE", gear(), id -> null);
		assertEquals("Salve amulet(ei)", undead.getGear().get("AMULET").getName());

		LoadoutCalculator.Loadout leafy = calc.calculate(task("leafy"), "MELEE", gear(), id -> null);
		assertEquals("Leaf-bladed sword", leafy.getGear().get("WEAPON").getName());

		LoadoutCalculator.Loadout plain = calc.calculate(task(), "MELEE", gear(), id -> null);
		assertEquals("Amulet of torture", plain.getGear().get("AMULET").getName());
	}

	@Test
	public void twoHandedWeaponDropsShield()
	{
		LoadoutCalculator calc = new LoadoutCalculator(owned("Abyssal whip", "Dragon defender"));
		LoadoutCalculator.Loadout l = calc.calculate(task(), "MELEE", gear(),
			id -> id == 1 ? EquipStats.builder().slot(EquipStats.SLOT_WEAPON).twoHanded(true).build() : null);
		assertNull(l.getGear().get("SHIELD"));
	}

	@Test
	public void requiredItemsReportOwnedOrMissing()
	{
		TaskInfo t = task();
		TaskInfo.ItemNeed hammer = new TaskInfo.ItemNeed();
		hammer.setName("Rock hammer");
		hammer.setAlternatives(Arrays.asList("Rock thrownhammer", "Granite hammer"));
		TaskInfo.ItemNeed earmuffs = new TaskInfo.ItemNeed();
		earmuffs.setName("Earmuffs");
		t.setRequiredItems(Arrays.asList(hammer, earmuffs));

		LoadoutCalculator calc = new LoadoutCalculator(owned("Granite hammer"));
		LoadoutCalculator.Loadout l = calc.calculate(t, "MELEE", gear(), id -> null);
		assertEquals("Granite hammer", l.getRequired().get(0).getOwned().getName());
		assertNull(l.getRequired().get(1).getOwned());
	}

	@Test
	public void armourChosenByRealStatsAndWeakness()
	{
		// 1 = crush-heavy body, 2 = slash-heavy body with the same strength
		LoadoutCalculator calc = new LoadoutCalculator(owned("Crushy platebody", "Slashy platebody"));
		java.util.function.IntFunction<EquipStats> stats = id -> id == 1
			? EquipStats.builder().slot(EquipStats.SLOT_BODY).acrush(20).str(5).build()
			: EquipStats.builder().slot(EquipStats.SLOT_BODY).aslash(20).str(5).build();
		TaskInfo crushWeak = task();
		crushWeak.setWeakness("crush");
		assertEquals("Crushy platebody", calc.calculate(crushWeak, "MELEE", gear(), stats).getGear().get("BODY").getName());
		TaskInfo slashWeak = task();
		slashWeak.setWeakness("slash");
		assertEquals("Slashy platebody", calc.calculate(slashWeak, "MELEE", gear(), stats).getGear().get("BODY").getName());
	}

	@Test
	public void ammoMatchesTheWeapon()
	{
		assertEquals("bolt", LoadoutCalculator.ammoTypeFor("Rune crossbow"));
		assertEquals("arrow", LoadoutCalculator.ammoTypeFor("Magic shortbow"));
		assertEquals("javelin", LoadoutCalculator.ammoTypeFor("Heavy ballista"));
		assertNull(LoadoutCalculator.ammoTypeFor("Bow of faerdhinen (c)"));
		assertNull(LoadoutCalculator.ammoTypeFor("Toxic blowpipe"));
	}

	@Test
	public void unchargedVersionsAreSkipped()
	{
		assertFalse(LoadoutCalculator.usable("Scythe of vitur (uncharged)"));
		assertFalse(LoadoutCalculator.usable("Uncharged trident"));
		assertTrue(LoadoutCalculator.usable("Scythe of vitur"));
	}

	@Test
	public void aoeLoadoutOnlyUsesAncientsWeapons()
	{
		GearData g = gear();
		Map<String, List<String>> aoe = new HashMap<>();
		aoe.put("WEAPON", Arrays.asList("Kodai wand", "Ancient staff"));
		aoe.put("CHINCHOMPA", Arrays.asList("Black chinchompa", "Red chinchompa"));
		g.setAoe(aoe);
		// Owns a powered staff (can't cast Barrage) and an Ancient staff
		LoadoutCalculator calc = new LoadoutCalculator(owned("Trident of the swamp", "Ancient staff", "Red chinchompa"));
		assertEquals("Ancient staff", calc.calculate(task(), LoadoutCalculator.AOE_MAGIC, g, id -> null).getGear().get("WEAPON").getName());
		assertEquals("Red chinchompa", calc.calculate(task(), LoadoutCalculator.CHINCHOMPA, g, id -> null).getGear().get("WEAPON").getName());
	}

	@Test
	public void leafyTaskNeverFallsBackToUselessWeapon()
	{
		LoadoutCalculator calc = new LoadoutCalculator(owned("Abyssal whip"));
		assertNull(calc.calculate(task("leafy"), "MELEE", gear(), id -> null).getGear().get("WEAPON"));
	}

	@Test
	public void salveReplacesSlayerHelmOnUndeadAndOnlyImbuedHelpsRanged()
	{
		GearData g = gear();
		Map<String, List<String>> ranged = new HashMap<>();
		ranged.put("HEAD", Arrays.asList("Slayer helmet (i)", "Black mask", "Masori mask"));
		g.getStyles().put("RANGED", ranged);
		// Undead melee task: salve in the amulet slot, and still the slayer helmet on the head
		LoadoutCalculator melee = new LoadoutCalculator(owned("Salve amulet(e)", "Slayer helmet", "Helm of neitiznot"));
		LoadoutCalculator.Loadout undead = melee.calculate(task("undead"), "MELEE", g, id -> null);
		assertEquals("Salve amulet(e)", undead.getGear().get("AMULET").getName());
		assertEquals("Slayer helmet", undead.getGear().get("HEAD").getName());
		// Ranged: an unimbued black mask gives nothing, so the Masori mask wins
		LoadoutCalculator range = new LoadoutCalculator(owned("Black mask", "Masori mask"));
		assertEquals("Masori mask", range.calculate(task(), "RANGED", g, id -> null).getGear().get("HEAD").getName());
	}

	@Test
	public void aoeWithoutAncientsWeaponFallsBackToNormalStyle()
	{
		GearData g = gear();
		Map<String, List<String>> aoe = new HashMap<>();
		aoe.put("WEAPON", Arrays.asList("Kodai wand"));
		g.setAoe(aoe);
		LoadoutCalculator calc = new LoadoutCalculator(owned("Abyssal whip"));
		LoadoutCalculator.Loadout l = calc.calculate(task(), LoadoutCalculator.AOE_MAGIC, g, id -> null);
		assertEquals("MELEE", l.getStyle());
		assertEquals("Abyssal whip", l.getGear().get("WEAPON").getName());
	}

	@Test
	public void lowTierLaunchersCantFireDragonAmmo()
	{
		assertFalse(LoadoutCalculator.canFire("rune crossbow", "dragon bolts"));
		assertTrue(LoadoutCalculator.canFire("armadyl crossbow", "dragon bolts"));
		assertFalse(LoadoutCalculator.canFire("magic shortbow", "dragon arrow"));
		assertNull(LoadoutCalculator.ammoTypeFor("Craw's bow"));
		assertFalse(LoadoutCalculator.usable("Accursed sceptre (u)"));
	}

	@Test
	public void taskKeyIgnoresCaseAndThe()
	{
		assertEquals(SlayQueenPlugin.key("The Alchemical Hydra"), SlayQueenPlugin.key("ALCHEMICAL HYDRA"));
	}
}
