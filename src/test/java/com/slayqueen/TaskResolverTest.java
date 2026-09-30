package com.slayqueen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskResolverTest
{
	private static TaskResolver.Account account(int slayer, Predicate<TaskInfo.Requirement> met)
	{
		return new TaskResolver.Account()
		{
			@Override
			public boolean met(TaskInfo.Requirement r)
			{
				return met.test(r);
			}

			@Override
			public int slayerLevel()
			{
				return slayer;
			}
		};
	}

	private static TaskInfo.ItemNeed need(String name)
	{
		TaskInfo.ItemNeed n = new TaskInfo.ItemNeed();
		n.setName(name);
		n.setAlternatives(new ArrayList<>());
		return n;
	}

	private static TaskInfo wyrms()
	{
		TaskInfo t = new TaskInfo();
		t.setName("Wyrms");
		t.setStyles(Collections.singletonList("MELEE"));
		t.setTags(new ArrayList<>());
		t.setSupplyType("PRAYER_POTIONS");
		t.setCannon("SOME");
		t.setRequiredItems(new ArrayList<>(Collections.singletonList(need("Boots of stone"))));
		t.setSlayerLevel(62);
		TaskInfo.Waiver w = new TaskInfo.Waiver();
		w.setItem("Boots of stone");
		w.setDiary("KOUREND");
		w.setTier("ELITE");
		t.setWaivers(Collections.singletonList(w));
		return t;
	}

	@Test
	public void diaryWaivesRequiredItem()
	{
		TaskResolver.Resolved withDiary = new TaskResolver().resolve(wyrms(), null, false, account(99, r -> true));
		assertTrue(withDiary.getTask().getRequiredItems().isEmpty());
		assertEquals(1, withDiary.getWaived().size());

		TaskResolver.Resolved without = new TaskResolver().resolve(wyrms(), null, false, account(99, r -> false));
		assertEquals("Boots of stone", without.getTask().getRequiredItems().get(0).getName());
	}

	@Test
	public void lowSlayerLevelIsReported()
	{
		TaskResolver.Resolved r = new TaskResolver().resolve(wyrms(), null, false, account(50, x -> true));
		assertEquals("SLAYER", r.getUnmet().get(0).getSkill());
		assertTrue(new TaskResolver().resolve(wyrms(), null, false, account(62, x -> true)).getUnmet().isEmpty());
	}

	@Test
	public void konarAreaAddsItsNeedsAndCannonRule()
	{
		TaskInfo t = wyrms();
		TaskInfo.AreaInfo karuulm = new TaskInfo.AreaInfo();
		karuulm.setRequiredItems(Collections.singletonList(need("Rope")));
		karuulm.setCannon("NO");
		Map<String, TaskInfo.AreaInfo> konar = new HashMap<>();
		konar.put("Karuulm Slayer Dungeon", karuulm);
		t.setKonar(konar);

		TaskResolver.Resolved r = new TaskResolver().resolve(t, "Karuulm Slayer Dungeon", false, account(99, x -> false));
		assertEquals("NO", r.getTask().getCannon());
		assertTrue(r.getTask().getRequiredItems().stream().anyMatch(n -> n.getName().equals("Rope")));
		// Resolving never changes the loaded data
		assertEquals("SOME", t.getCannon());
	}

	@Test
	public void wildernessTaskDropsNonWildyAoeAndAddsLootingBag()
	{
		TaskInfo t = wyrms();
		t.setAoe("BARRAGE");
		t.setAoeLevel("YES");
		t.setAoeWhere("Catacombs of Kourend");
		TaskResolver.Resolved r = new TaskResolver().resolve(t, null, true, account(99, x -> true));
		assertNull(r.getTask().getAoe());
		assertTrue(r.getTask().getTags().contains("wilderness"));
		assertEquals("Looting bag", r.getTask().getLootItems().get(0).getName());
		assertTrue(r.getTask().getNotes().get(0).contains("Wilderness"));
	}

	@Test
	public void poisonAddsCureAndSuppliesFollowSustain()
	{
		TaskInfo t = wyrms();
		t.setPoison("VENOM");
		t.setRecommendedItems(new ArrayList<>());
		TaskResolver.Resolved r = new TaskResolver().resolve(t, null, false, account(99, x -> true));
		assertEquals("Anti-venom+", r.getTask().getRecommendedItems().get(0).getName());
		assertEquals("Prayer potion", r.getTask().getSupplies().get(0).getName());
		assertTrue(r.getTask().getSupplies().stream().anyMatch(n -> n.getName().equals("Cannonball")));
		assertFalse(r.getTask().getSupplies().stream().anyMatch(n -> n.getName().equals("Shark")));
	}

	@Test
	public void konarAreaNamesMatchLoosely()
	{
		Map<String, TaskInfo.AreaInfo> areas = new HashMap<>();
		TaskInfo.AreaInfo stronghold = new TaskInfo.AreaInfo();
		areas.put("Stronghold Slayer Cave", stronghold);
		areas.put("Karuulm Slayer Dungeon", new TaskInfo.AreaInfo());
		assertTrue(TaskResolver.findArea(areas, "Stronghold Slayer Dungeon") == stronghold);
		assertNull(TaskResolver.findArea(areas, "Brimhaven Dungeon"));
	}

	@Test
	public void unknownDiaryCountsAsMet()
	{
		assertEquals(-1, AccountChecks.diaryVarbit("NOPE", "ELITE"));
		assertTrue(AccountChecks.diaryVarbit("KOUREND", "ELITE") > 0);
		assertTrue(Arrays.asList("CABIN_FEVER").contains("CABIN_FEVER"));
	}
}
