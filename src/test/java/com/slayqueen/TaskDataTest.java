package com.slayqueen;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaskDataTest
{
	private static <T> T load(String file, Class<T> type) throws Exception
	{
		try (Reader r = new InputStreamReader(SlayQueenPlugin.class.getResourceAsStream(file), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(r, type);
		}
	}

	@Test
	public void everyRuneLiteSlayerTaskHasAdvice() throws Exception
	{
		TaskInfo[] tasks = load("tasks.json", TaskInfo[].class);
		Set<String> keys = new HashSet<>();
		for (TaskInfo t : tasks)
		{
			keys.add(SlayQueenPlugin.key(t.getName()));
		}
		// Copy of the task names in RuneLite's slayer Task enum, which matches the game's task table
		try (java.io.BufferedReader r = new java.io.BufferedReader(new InputStreamReader(
			TaskDataTest.class.getResourceAsStream("runelite-tasks.txt"), StandardCharsets.UTF_8)))
		{
			String name;
			int count = 0;
			while ((name = r.readLine()) != null)
			{
				if (!name.isBlank())
				{
					count++;
					assertTrue("No advice for " + name, keys.contains(SlayQueenPlugin.key(name)));
				}
			}
			assertEquals(152, count);
		}
	}

	@Test
	public void fieldsUseKnownValues() throws Exception
	{
		Set<String> prayers = new HashSet<>(Arrays.asList("MELEE", "MISSILES", "MAGIC", "NONE", "VARIES"));
		Set<String> sustain = new HashSet<>(Arrays.asList("PRAY", "EAT", "EITHER"));
		Set<String> cannon = new HashSet<>(Arrays.asList("YES", "NO", "SOME"));
		Set<String> styles = new HashSet<>(Arrays.asList("MELEE", "RANGED", "MAGIC"));
		for (TaskInfo t : load("tasks.json", TaskInfo[].class))
		{
			assertTrue(t.getName(), prayers.contains(t.getPrayer()));
			assertTrue(t.getName(), sustain.contains(t.getSustain()));
			assertTrue(t.getName(), cannon.contains(t.getCannon()));
			assertFalse(t.getName(), t.getStyles().isEmpty());
			assertTrue(t.getName(), styles.containsAll(t.getStyles()));
		}
	}

	@Test
	public void gearDataLoads() throws Exception
	{
		GearData gear = load("gear.json", GearData.class);
		assertNotNull(gear.getStyles());
		for (String style : Arrays.asList("MELEE", "RANGED", "MAGIC"))
		{
			assertFalse(style, gear.generic(style, "WEAPON").isEmpty());
		}
		assertFalse(gear.aoe("WEAPON").isEmpty());
		assertEquals("Salve amulet(ei)", gear.special("undead", "AMULET").get(0));
	}
}
