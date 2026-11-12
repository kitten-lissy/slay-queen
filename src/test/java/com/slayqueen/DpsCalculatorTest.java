package com.slayqueen;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Weapon and style choice by expected damage. Item bonuses and monster stats are the OSRS Wiki's.
 */
public class DpsCalculatorTest
{
	private static final DpsCalculator.Levels MAXED = new DpsCalculator.Levels(99, 99, 99, 99);

	// Item bonuses from the OSRS Wiki (Infobox Bonuses)
	private static final EquipStats WHIP = EquipStats.builder().slot(EquipStats.SLOT_WEAPON)
		.aslash(82).str(82).aspeed(4).build();
	private static final EquipStats TENTACLE = EquipStats.builder().slot(EquipStats.SLOT_WEAPON)
		.aslash(90).str(86).aspeed(4).build();
	private static final EquipStats FANG = EquipStats.builder().slot(EquipStats.SLOT_WEAPON)
		.astab(105).aslash(75).str(103).aspeed(5).build();
	private static final EquipStats TWISTED_BOW = EquipStats.builder().slot(EquipStats.SLOT_WEAPON).twoHanded(true)
		.arange(70).rstr(20).aspeed(6).build();
	private static final EquipStats DRAGON_ARROW = EquipStats.builder().slot(EquipStats.SLOT_AMMO)
		.rstr(60).build();
	private static final EquipStats AMULET_OF_FURY = EquipStats.builder().slot(EquipStats.SLOT_AMULET)
		.astab(10).aslash(10).acrush(10).amagic(10).arange(10).str(8).prayer(5).build();

	/** Duke Sucellus (post-quest, awake): stab 255, slash 45, crush 190, Defence 275. */
	private static TaskInfo.Defence duke()
	{
		TaskInfo.Defence d = new TaskInfo.Defence();
		d.setLevel(275);
		d.setMagicLevel(230);
		d.setStab(255);
		d.setSlash(45);
		d.setCrush(190);
		d.setMagic(400);
		d.setRangedLight(300);
		d.setRangedStandard(300);
		d.setRangedHeavy(300);
		d.setSize(7);
		return d;
	}

	/** Lizardman shaman: stab -20, slash 40, crush 30, Defence 140. */
	private static TaskInfo.Defence lizardmanShaman()
	{
		TaskInfo.Defence d = new TaskInfo.Defence();
		d.setLevel(140);
		d.setMagicLevel(130);
		d.setStab(-20);
		d.setSlash(40);
		d.setCrush(30);
		d.setMagic(50);
		d.setRangedLight(-10);
		d.setRangedStandard(0);
		d.setRangedHeavy(0);
		d.setSize(3);
		return d;
	}

	private static TaskInfo task(TaskInfo.Defence d, String... styles)
	{
		TaskInfo t = new TaskInfo();
		t.setName("Test");
		t.setStyles(Arrays.asList(styles));
		t.setTags(Collections.emptyList());
		t.setDefence(d);
		return t;
	}

	private static GearData realGear() throws Exception
	{
		try (Reader r = new InputStreamReader(SlayQueenPlugin.class.getResourceAsStream("gear.json"), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(r, GearData.class);
		}
	}

	/** Owns the named items, with ids 1, 2, 3... in order; stats looked up by name. */
	private static class Bank
	{
		final Map<String, LoadoutCalculator.Pick> owned = new HashMap<>();
		final Map<Integer, EquipStats> stats = new HashMap<>();

		Bank add(String name, EquipStats s)
		{
			int id = owned.size() + 1;
			owned.put(name.toLowerCase(Locale.ROOT), new LoadoutCalculator.Pick(name, id));
			if (s != null)
			{
				stats.put(id, s);
			}
			return this;
		}

		IntFunction<EquipStats> stats()
		{
			return stats::get;
		}

		LoadoutCalculator calc()
		{
			return new LoadoutCalculator(owned, MAXED);
		}
	}

	private static String weapon(LoadoutCalculator.Loadout l)
	{
		LoadoutCalculator.Pick w = l.getGear().get("WEAPON");
		return w == null ? null : w.getName();
	}

	@Test
	public void whipAndTentacleBeatFangWhenSlashDefenceIsLow() throws Exception
	{
		GearData gear = realGear();
		Bank whip = new Bank().add("Osmumten's fang", FANG).add("Abyssal whip", WHIP);
		assertEquals("Abyssal whip", weapon(whip.calc().calculate(task(duke(), "MELEE"), "MELEE", gear, whip.stats())));

		Bank tentacle = new Bank().add("Osmumten's fang", FANG).add("Abyssal tentacle", TENTACLE);
		assertEquals("Abyssal tentacle", weapon(tentacle.calc().calculate(task(duke(), "MELEE"), "MELEE", gear, tentacle.stats())));
	}

	@Test
	public void fangWinsWhenStabDefenceIsLow() throws Exception
	{
		GearData gear = realGear();
		Bank bank = new Bank().add("Abyssal whip", WHIP).add("Abyssal tentacle", TENTACLE).add("Osmumten's fang", FANG);
		assertEquals("Osmumten's fang", weapon(bank.calc().calculate(task(lizardmanShaman(), "MELEE"), "MELEE", gear, bank.stats())));
	}

	@Test
	public void withoutMonsterDataTheRankedListIsUsed() throws Exception
	{
		GearData gear = realGear();
		Bank bank = new Bank().add("Abyssal whip", WHIP).add("Osmumten's fang", FANG);
		LoadoutCalculator.Loadout l = bank.calc().calculate(task(null, "MELEE"), "MELEE", gear, bank.stats());
		assertEquals("Osmumten's fang", weapon(l));
		assertEquals(0, l.getDps(), 0);
	}

	@Test
	public void autoStylePicksTheHighestDamage() throws Exception
	{
		GearData gear = realGear();
		// Melee listed first, but a twisted bow with dragon arrows out-damages a whip on a shaman
		Bank bank = new Bank().add("Abyssal whip", WHIP).add("Twisted bow", TWISTED_BOW).add("Dragon arrow", DRAGON_ARROW);
		LoadoutCalculator.Loadout l = bank.calc().best(task(lizardmanShaman(), "MELEE", "RANGED"), gear, bank.stats());
		assertEquals("RANGED", l.getStyle());
		assertEquals("Twisted bow", weapon(l));
		assertEquals("Dragon arrow", l.getGear().get("AMMO").getName());
		assertEquals(LoadoutCalculator.REASON_DPS, l.getReason());
		assertTrue(l.getDps() > 0);
	}

	@Test
	public void autoStyleKeepsTheFirstStyleWithoutData() throws Exception
	{
		GearData gear = realGear();
		Bank bank = new Bank().add("Abyssal whip", WHIP).add("Twisted bow", TWISTED_BOW).add("Dragon arrow", DRAGON_ARROW);
		LoadoutCalculator.Loadout l = bank.calc().best(task(null, "MELEE", "RANGED"), gear, bank.stats());
		assertEquals("MELEE", l.getStyle());
		assertEquals(LoadoutCalculator.REASON_USUAL, l.getReason());
	}

	@Test
	public void armourCountsTowardsTheComparison() throws Exception
	{
		// Same weapon either way; the amulet adds accuracy and strength, so the loadout deals more
		GearData gear = realGear();
		Bank without = new Bank().add("Abyssal whip", WHIP);
		Bank with = new Bank().add("Abyssal whip", WHIP).add("Amulet of fury", AMULET_OF_FURY);
		double a = without.calc().calculate(task(duke(), "MELEE"), "MELEE", gear, without.stats()).getDps();
		double b = with.calc().calculate(task(duke(), "MELEE"), "MELEE", gear, with.stats()).getDps();
		assertTrue(b > a);
	}

	@Test
	public void fangRollsAccuracyTwiceOnStabOnly()
	{
		double att = 20000;
		double def = 30000;
		assertTrue(DpsCalculator.fangHitChance(att, def) > DpsCalculator.hitChance(att, def));
		List<int[]> slash = DpsCalculator.meleeStyles("osmumten's fang", "slash");
		assertEquals(1, slash.size());
		assertEquals(3, slash.get(0)[1]);
	}

	@Test
	public void whipHasNoAggressiveStyle()
	{
		for (int[] s : DpsCalculator.meleeStyles("abyssal whip", "slash"))
		{
			assertFalse(s[1] == 3);
		}
	}

	@Test
	public void twistedBowAccuracyIsCapped()
	{
		assertEquals(140, DpsCalculator.twistedBowAccuracy(250));
	}

	@Test
	public void rangedWeightsFollowTheWiki()
	{
		assertEquals("standard", DpsCalculator.rangedWeight("Twisted bow"));
		assertEquals("heavy", DpsCalculator.rangedWeight("Armadyl crossbow"));
		assertEquals("heavy", DpsCalculator.rangedWeight("Black chinchompa"));
		assertEquals("light", DpsCalculator.rangedWeight("Toxic blowpipe"));
	}

	@Test
	public void poweredStaffMaxHitsMatchTheWikiCalculator()
	{
		assertEquals(99 / 3 - 1, DpsCalculator.poweredStaff("sanguinesti staff", 99, 4)[0]);
		assertEquals(99 / 3 + 1, DpsCalculator.poweredStaff("tumeken's shadow", 99, 5)[0]);
		assertEquals(null, DpsCalculator.poweredStaff("kodai wand", 99, 4));
	}

	@Test
	public void weaponStyleFromStats()
	{
		assertEquals("MELEE", LoadoutCalculator.weaponStyle("Abyssal whip", WHIP));
		assertEquals("RANGED", LoadoutCalculator.weaponStyle("Twisted bow", TWISTED_BOW));
		assertEquals("MAGIC", LoadoutCalculator.weaponStyle("Kodai wand",
			EquipStats.builder().slot(EquipStats.SLOT_WEAPON).amagic(28).mdmg(15).build()));
		assertNotNull(LoadoutCalculator.weaponStyle("Black chinchompa",
			EquipStats.builder().slot(EquipStats.SLOT_WEAPON).arange(80).rstr(30).build()));
	}
}
