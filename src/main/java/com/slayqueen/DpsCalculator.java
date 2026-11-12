package com.slayqueen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.Value;

/**
 * Expected damage per second of a full loadout against a task's monster, using the standard
 * formulas from the OSRS Wiki ("Damage per second/Melee", "/Ranged", "/Magic", "Maximum magic hit").
 *
 * Assumptions, to keep it simple and deterministic:
 * - the player's real (unboosted) levels, no potions and no prayers
 * - the best attack style the weapon offers for each attack type
 * - magic weapons that are not powered staves autocast the best Fire spell from the standard spellbook
 * - a blowpipe holds the best darts the player owns, or adamant darts if none are found
 * - elemental weaknesses, set effects other than crystal armour, and special attacks are ignored
 */
final class DpsCalculator
{
	private static final double TICK_SECONDS = 0.6;

	/** Adamant dart ranged strength (OSRS Wiki), assumed in a blowpipe when the player owns no darts. */
	static final int DEFAULT_DART_STRENGTH = 17;

	private DpsCalculator()
	{
	}

	@Value
	static class Levels
	{
		int attack;
		int strength;
		int ranged;
		int magic;
	}

	@Value
	static class Result
	{
		/** Expected damage per second, 0 when the weapon can't be used or isn't known. */
		double dps;
		/** stab, slash or crush for melee, otherwise null. */
		String attackType;
	}

	private static final Result NONE = new Result(0, null);

	/**
	 * Melee attack types a weapon offers: those with a positive attack bonus, or the best one
	 * if none is positive.
	 */
	static List<String> meleeTypes(EquipStats w)
	{
		List<String> out = new ArrayList<>();
		if (w.getAstab() > 0)
		{
			out.add("stab");
		}
		if (w.getAslash() > 0)
		{
			out.add("slash");
		}
		if (w.getAcrush() > 0)
		{
			out.add("crush");
		}
		if (out.isEmpty())
		{
			int best = Math.max(w.getAstab(), Math.max(w.getAslash(), w.getAcrush()));
			out.add(best == w.getAstab() ? "stab" : best == w.getAslash() ? "slash" : "crush");
		}
		return out;
	}

	/**
	 * @param meleeType only evaluate this melee attack type, or null for the best one
	 * @param dartStrength ranged strength of the darts loaded in a blowpipe
	 */
	static Result evaluate(String style, Map<String, LoadoutCalculator.Pick> gear, IntFunction<EquipStats> stats,
		TaskInfo task, Levels levels, String meleeType, int dartStrength)
	{
		TaskInfo.Defence def = task.getDefence();
		LoadoutCalculator.Pick weaponPick = gear.get("WEAPON");
		if (def == null || levels == null || weaponPick == null)
		{
			return NONE;
		}
		EquipStats weapon = stats.apply(weaponPick.getItemId());
		if (weapon == null)
		{
			return NONE;
		}
		Worn worn = new Worn(gear, stats, task);
		switch (style)
		{
			case "MELEE":
				return melee(worn, weapon, def, levels, meleeType);
			case "RANGED":
				return new Result(ranged(worn, weapon, def, levels, dartStrength), null);
			case "MAGIC":
				return new Result(magic(worn, weapon, def, levels, task), null);
			default:
				return NONE;
		}
	}

	/**
	 * Totals and names of the worn gear.
	 */
	private static class Worn
	{
		int astab;
		int aslash;
		int acrush;
		int amagic;
		int arange;
		int str;
		int rstr;
		double mdmg;
		final String weapon;
		final String head;
		final String amulet;
		final String body;
		final String legs;
		final String shield;
		final boolean undead;
		final List<String> tags;

		Worn(Map<String, LoadoutCalculator.Pick> gear, IntFunction<EquipStats> stats, TaskInfo task)
		{
			for (LoadoutCalculator.Pick p : gear.values())
			{
				EquipStats s = stats.apply(p.getItemId());
				if (s == null)
				{
					continue;
				}
				astab += s.getAstab();
				aslash += s.getAslash();
				acrush += s.getAcrush();
				amagic += s.getAmagic();
				arange += s.getArange();
				str += s.getStr();
				rstr += s.getRstr();
				mdmg += s.getMdmg();
			}
			weapon = name(gear, "WEAPON");
			head = name(gear, "HEAD");
			amulet = name(gear, "AMULET");
			body = name(gear, "BODY");
			legs = name(gear, "LEGS");
			shield = name(gear, "SHIELD");
			tags = task.tagsOrEmpty();
			undead = tags.contains("undead");
		}

		int meleeAttack(String type)
		{
			switch (type)
			{
				case "stab":
					return astab;
				case "slash":
					return aslash;
				default:
					return acrush;
			}
		}

		boolean slayerHead()
		{
			return head.contains("slayer helmet") || head.contains("black mask");
		}

		boolean imbuedSlayerHead()
		{
			return slayerHead() && head.contains("(i)");
		}

		boolean salve()
		{
			return undead && amulet.startsWith("salve amulet");
		}

		boolean salveImbued()
		{
			return salve() && amulet.contains("i)");
		}

		boolean salveEnchanted()
		{
			return salve() && (amulet.contains("(e)") || amulet.contains("(ei)"));
		}

		boolean tagged(String tag)
		{
			return tags.contains(tag);
		}

		private static String name(Map<String, LoadoutCalculator.Pick> gear, String slot)
		{
			LoadoutCalculator.Pick p = gear.get(slot);
			return p == null ? "" : p.getName().toLowerCase(Locale.ROOT);
		}
	}

	// ---- Melee ----

	private static Result melee(Worn worn, EquipStats weapon, TaskInfo.Defence def, Levels lv, String onlyType)
	{
		String wn = worn.weapon;
		boolean fang = wn.startsWith("osmumten's fang");
		boolean scythe = wn.contains("scythe of vitur");
		int speed = weapon.getAspeed() > 0 ? weapon.getAspeed() : 4;
		List<String> types = onlyType != null ? Collections.singletonList(onlyType) : meleeTypes(weapon);

		double bestDps = 0;
		String bestType = null;
		for (String type : types)
		{
			for (int[] style : meleeStyles(wn, type))
			{
				int effAtt = lv.getAttack() + style[0] + 8;
				int effStr = lv.getStrength() + style[1] + 8;
				int max = (effStr * (worn.str + 64) + 320) / 640;
				double att = (double) effAtt * Math.max(0, worn.meleeAttack(type) + 64);

				// Salve and the slayer helmet don't stack; the salve wins on undead
				if (worn.salve())
				{
					double m = worn.salveEnchanted() ? 1.2 : 7.0 / 6.0;
					att = Math.floor(att * m);
					max = (int) Math.floor(max * m);
				}
				else if (worn.slayerHead())
				{
					att = Math.floor(att * 7.0 / 6.0);
					max = max * 7 / 6;
				}

				double accMult = 1;
				double dmgMult = 1;
				if (worn.tagged("demon") && (wn.startsWith("arclight") || wn.startsWith("emberlight")))
				{
					accMult = dmgMult = 1.7;
				}
				else if (worn.tagged("demon") && wn.startsWith("darklight"))
				{
					accMult = dmgMult = 1.6;
				}
				else if (worn.tagged("dragon") && wn.startsWith("dragon hunter lance"))
				{
					accMult = dmgMult = 1.2;
				}
				else if (worn.tagged("leafy") && wn.startsWith("leaf-bladed battleaxe"))
				{
					dmgMult = 1.175;
				}
				else if (worn.tagged("kalphite") && wn.startsWith("keris"))
				{
					dmgMult = 1.33;
					if (wn.contains("breaching"))
					{
						accMult = 1.33;
					}
				}
				att = Math.floor(att * accMult);
				max = (int) Math.floor(max * dmgMult);

				double defRoll = (double) (def.getLevel() + 9) * Math.max(0, def.melee(type) + 64);
				// The fang rolls accuracy twice on its stab styles (outside the Tombs of Amascut)
				double hit = fang && "stab".equals(type) ? fangHitChance(att, defRoll) : hitChance(att, defRoll);

				double perAttack;
				if (scythe)
				{
					int hits = def.getSize() >= 3 ? 3 : def.getSize() == 2 ? 2 : 1;
					perAttack = 0;
					for (int i = 0; i < hits; i++)
					{
						perAttack += hit * averageHit(max >> i);
					}
				}
				else
				{
					// The fang's hits land between 15% and 85% of its max hit, which averages to half of it
					perAttack = hit * (fang ? max / 2.0 : averageHit(max));
				}
				double dps = perAttack / (speed * TICK_SECONDS);
				if (dps > bestDps)
				{
					bestDps = dps;
					bestType = type;
				}
			}
		}
		return new Result(bestDps, bestType);
	}

	/**
	 * Style bonuses {attack, strength} available for an attack type: accurate (+3 attack) or
	 * aggressive (+3 strength) unless the weapon is known to lack one of them.
	 */
	static List<int[]> meleeStyles(String wn, String type)
	{
		int[] accurate = {3, 0};
		int[] aggressive = {0, 3};
		int[] controlled = {1, 1};
		if (wn.contains("whip") || wn.contains("tentacle"))
		{
			return Arrays.asList(accurate, controlled);
		}
		if (wn.contains("hasta") || wn.contains("spear"))
		{
			return Collections.singletonList(controlled);
		}
		if (wn.contains("halberd"))
		{
			return Collections.singletonList("stab".equals(type) ? controlled : aggressive);
		}
		if (wn.startsWith("osmumten's fang") && "slash".equals(type))
		{
			return Collections.singletonList(aggressive);
		}
		boolean slashSword = wn.contains("scimitar") || wn.contains("longsword") || wn.contains("blade of saeldor");
		if (slashSword && "stab".equals(type))
		{
			return Collections.singletonList(controlled);
		}
		return Arrays.asList(accurate, aggressive);
	}

	// ---- Ranged ----

	/**
	 * light (thrown weapons and blowpipes), standard (bows) or heavy (crossbows, ballistae, chinchompas).
	 */
	static String rangedWeight(String weaponName)
	{
		String w = weaponName.toLowerCase(Locale.ROOT);
		if (w.contains("crossbow") || w.contains("ballista") || w.contains("chinchompa") || w.contains("karil"))
		{
			return "heavy";
		}
		if (w.contains("bow") && !w.contains("blowpipe"))
		{
			return "standard";
		}
		return "light";
	}

	private static double ranged(Worn worn, EquipStats weapon, TaskInfo.Defence def, Levels lv, int dartStrength)
	{
		String wn = worn.weapon;
		int baseSpeed = weapon.getAspeed() > 0 ? weapon.getAspeed() : 4;
		int rstr = worn.rstr + (wn.contains("blowpipe") ? dartStrength : 0);
		double defRoll = (double) (def.getLevel() + 9) * Math.max(0, def.ranged(rangedWeight(wn)) + 64);

		double best = 0;
		// Accurate: +3 levels. Rapid: one tick faster.
		int[][] styles = {{3, baseSpeed}, {0, Math.max(1, baseSpeed - 1)}};
		for (int[] style : styles)
		{
			int eff = lv.getRanged() + style[0] + 8;
			int max = (int) Math.floor(0.5 + eff * (rstr + 64) / 640.0);
			double att = (double) eff * Math.max(0, worn.arange + 64);

			double gearMult = 1;
			if (worn.salveImbued())
			{
				gearMult = worn.amulet.contains("(ei)") ? 1.2 : 7.0 / 6.0;
			}
			else if (worn.imbuedSlayerHead())
			{
				gearMult = 1.15;
			}
			att = Math.floor(att * gearMult);
			max = (int) Math.floor(max * gearMult);

			double accMult = 1;
			double dmgMult = 1;
			if (wn.startsWith("twisted bow"))
			{
				int magic = Math.min(250, Math.max(def.getMagicLevel(), def.getMagicAttack()));
				accMult = twistedBowAccuracy(magic) / 100.0;
				dmgMult = twistedBowDamage(magic) / 100.0;
			}
			else if (wn.startsWith("bow of faerdhinen") || wn.startsWith("crystal bow"))
			{
				// Crystal armour: helm 5%/2.5%, body 15%/7.5%, legs 10%/5% accuracy/damage
				double acc = 0;
				double dmg = 0;
				if (worn.head.startsWith("crystal helm"))
				{
					acc += 5;
					dmg += 2.5;
				}
				if (worn.body.startsWith("crystal body"))
				{
					acc += 15;
					dmg += 7.5;
				}
				if (worn.legs.startsWith("crystal legs"))
				{
					acc += 10;
					dmg += 5;
				}
				accMult = 1 + acc / 100;
				dmgMult = 1 + dmg / 100;
			}
			else if (worn.tagged("dragon") && wn.startsWith("dragon hunter crossbow"))
			{
				accMult = 1.3;
				dmgMult = 1.25;
			}
			else if (worn.tagged("demon") && wn.startsWith("scorching bow"))
			{
				accMult = dmgMult = 1.3;
			}
			att = Math.floor(att * accMult);
			max = (int) Math.floor(max * dmgMult);

			double dps = hitChance(att, defRoll) * averageHit(max) / (style[1] * TICK_SECONDS);
			best = Math.max(best, dps);
		}
		return best;
	}

	/** Twisted bow accuracy in percent (OSRS Wiki, Mod Kieren's formula), capped at 140. */
	static int twistedBowAccuracy(int magic)
	{
		int t = 3 * magic / 10;
		int v = 140 + (3 * magic - 10) / 100 - (t - 100) * (t - 100) / 100;
		return Math.max(0, Math.min(140, v));
	}

	/** Twisted bow damage in percent, capped at 250. */
	static int twistedBowDamage(int magic)
	{
		int t = 3 * magic / 10;
		int v = 250 + (3 * magic - 14) / 100 - (t - 140) * (t - 140) / 100;
		return Math.max(0, Math.min(250, v));
	}

	// ---- Magic ----

	/**
	 * Built-in spell of a powered staff: {base max hit, attack speed}, or null if it isn't one.
	 * Max hits from the wiki's powered staff calculator.
	 */
	static int[] poweredStaff(String wn, int magic, int speed)
	{
		int s = speed > 0 ? speed : 4;
		if (wn.startsWith("tumeken's shadow"))
		{
			return new int[]{magic / 3 + 1, s};
		}
		if (wn.contains("sanguinesti staff"))
		{
			return new int[]{magic / 3 - 1, s};
		}
		if (wn.startsWith("trident of the swamp"))
		{
			return new int[]{magic / 3 - 2, s};
		}
		if (wn.startsWith("trident of the seas"))
		{
			return new int[]{magic / 3 - 5, s};
		}
		if (wn.startsWith("eye of ayak"))
		{
			return new int[]{magic / 3 - 6, s};
		}
		if (wn.startsWith("accursed sceptre") && !wn.contains("(a)"))
		{
			return new int[]{magic / 3 - 6, s};
		}
		if (wn.startsWith("thammaron's sceptre") && !wn.contains("(a)"))
		{
			return new int[]{magic / 3 - 8, s};
		}
		if (wn.startsWith("warped sceptre"))
		{
			return new int[]{(8 * magic + 96) / 37, s};
		}
		return null;
	}

	/**
	 * Max hit of the best Fire spell on the standard spellbook at a Magic level (Strike to Surge).
	 */
	static int fireSpell(int magic)
	{
		if (magic >= 95)
		{
			return 24;
		}
		if (magic >= 75)
		{
			return 20;
		}
		if (magic >= 59)
		{
			return 16;
		}
		if (magic >= 35)
		{
			return 12;
		}
		return magic >= 13 ? 8 : 0;
	}

	/** Weapons that can cast Magic Dart, the spell used on kurask and turoth. */
	private static boolean castsMagicDart(String wn)
	{
		return wn.startsWith("slayer's staff") || wn.contains("staff of the dead") || wn.startsWith("staff of light")
			|| wn.startsWith("staff of balance");
	}

	private static boolean autocasts(String wn)
	{
		if (wn.startsWith("bone staff") || wn.startsWith("dawnbringer") || wn.startsWith("crystal staff") || wn.startsWith("starter staff"))
		{
			return false;
		}
		return wn.contains("staff") || wn.contains("wand") || wn.contains("sceptre") || wn.startsWith("blue moon spear");
	}

	private static double magic(Worn worn, EquipStats weapon, TaskInfo.Defence def, Levels lv, TaskInfo task)
	{
		String wn = worn.weapon;
		int m = lv.getMagic();
		int base;
		int speed;
		boolean powered = false;
		boolean standard = false;
		int[] staff = poweredStaff(wn, m, weapon.getAspeed());
		if (task.tagsOrEmpty().contains("leafy"))
		{
			if (!castsMagicDart(wn))
			{
				return 0;
			}
			// Magic Dart; the slayer's staff (e) hits harder on task
			base = wn.startsWith("slayer's staff (e)") ? m / 6 + 13 : m / 10 + 10;
			speed = 5;
		}
		else if (staff != null)
		{
			base = staff[0];
			speed = staff[1];
			powered = true;
		}
		else if (autocasts(wn))
		{
			base = fireSpell(m);
			speed = wn.startsWith("harmonised nightmare staff") ? 4 : 5;
			standard = true;
		}
		else
		{
			return 0;
		}
		if (base <= 0)
		{
			return 0;
		}

		// Accurate on a powered staff gives +2 effective levels
		int eff = m + (powered ? 2 : 0) + 9;
		int bonus = worn.amagic;
		double mdmg = worn.mdmg;
		if (wn.startsWith("tumeken's shadow"))
		{
			// Triples worn magic attack and damage outside the Tombs of Amascut, damage capped at 100%
			bonus *= 3;
			mdmg = Math.min(100, mdmg * 3);
		}
		double att = (double) eff * Math.max(0, bonus + 64);

		double dmgPct = mdmg / 100.0;
		boolean salve = worn.salveImbued();
		boolean slayer = !salve && worn.imbuedSlayerHead();
		if (salve)
		{
			boolean ei = worn.amulet.contains("(ei)");
			att = Math.floor(att * (ei ? 1.2 : 1.15));
			dmgPct += ei ? 0.20 : 0.15;
		}
		else if (slayer)
		{
			att = Math.floor(att * 1.15);
		}
		if (standard && (wn.contains("smoke battlestaff") || wn.contains("smoke staff") || wn.startsWith("twinflame staff")))
		{
			dmgPct += 0.10;
		}

		int max = (int) Math.floor(base * (1 + dmgPct));
		if (slayer)
		{
			max = (int) Math.floor(max * 1.15);
		}
		if (standard && worn.shield.startsWith("tome of fire"))
		{
			max = (int) Math.floor(max * 1.1);
		}
		if (worn.tagged("dragon") && wn.startsWith("dragon hunter wand"))
		{
			att = Math.floor(att * 1.75);
			max = (int) Math.floor(max * 1.4);
		}

		double defRoll = (double) (def.getMagicLevel() + 9) * Math.max(0, def.getMagic() + 64);
		return hitChance(att, defRoll) * averageHit(max) / (speed * TICK_SECONDS);
	}

	// ---- Shared ----

	static double hitChance(double att, double def)
	{
		if (att > def)
		{
			return 1 - (def + 2) / (2 * (att + 1));
		}
		return att / (2 * (def + 1));
	}

	/**
	 * Osmumten's fang outside the Tombs of Amascut: two accuracy rolls against one defence roll.
	 */
	static double fangHitChance(double a, double d)
	{
		if (a >= d)
		{
			return 1 - (d + 2) * (2 * d + 3) / (6 * (a + 1) * (a + 1));
		}
		return a * (4 * a + 5) / (6 * (a + 1) * (d + 1));
	}

	/**
	 * Average damage of a successful hit; a rolled 0 becomes a 1.
	 */
	static double averageHit(int max)
	{
		return max <= 0 ? 0 : max / 2.0 + 1.0 / (max + 1);
	}
}
