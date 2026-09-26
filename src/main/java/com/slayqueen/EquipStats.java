package com.slayqueen;

import lombok.Builder;
import lombok.Value;

/**
 * The equipment bonuses the loadout scoring needs, copied from RuneLite's item stats
 * so the calculator can be tested without a client.
 */
@Value
@Builder
public class EquipStats
{
	/** EquipmentInventorySlot index. */
	int slot;
	boolean twoHanded;
	int astab;
	int aslash;
	int acrush;
	int amagic;
	int arange;
	int str;
	int rstr;
	float mdmg;
	int prayer;
	int aspeed;

	static final int SLOT_HEAD = 0;
	static final int SLOT_CAPE = 1;
	static final int SLOT_AMULET = 2;
	static final int SLOT_WEAPON = 3;
	static final int SLOT_BODY = 4;
	static final int SLOT_SHIELD = 5;
	static final int SLOT_LEGS = 7;
	static final int SLOT_GLOVES = 9;
	static final int SLOT_BOOTS = 10;
	static final int SLOT_RING = 12;
	static final int SLOT_AMMO = 13;

	static int slotIndex(String slot)
	{
		switch (slot)
		{
			case "HEAD":
				return SLOT_HEAD;
			case "CAPE":
				return SLOT_CAPE;
			case "AMULET":
				return SLOT_AMULET;
			case "WEAPON":
				return SLOT_WEAPON;
			case "BODY":
				return SLOT_BODY;
			case "SHIELD":
				return SLOT_SHIELD;
			case "LEGS":
				return SLOT_LEGS;
			case "GLOVES":
				return SLOT_GLOVES;
			case "BOOTS":
				return SLOT_BOOTS;
			case "RING":
				return SLOT_RING;
			case "AMMO":
				return SLOT_AMMO;
			default:
				return -1;
		}
	}

	/**
	 * Melee attack bonus that matters against a monster: its weakness if it is
	 * stab/slash/crush, otherwise the best of the three.
	 */
	int meleeAttack(String weakness)
	{
		if ("stab".equalsIgnoreCase(weakness))
		{
			return astab;
		}
		if ("slash".equalsIgnoreCase(weakness))
		{
			return aslash;
		}
		if ("crush".equalsIgnoreCase(weakness))
		{
			return acrush;
		}
		return Math.max(astab, Math.max(aslash, acrush));
	}

	/**
	 * How much this piece adds for a style. Damage bonuses are weighted above accuracy,
	 * the same way gear guides rank them. Prayer breaks ties.
	 */
	double armourScore(String style, String weakness)
	{
		switch (style)
		{
			case "RANGED":
				return rstr * 4.0 + arange + prayer * 0.1;
			case "MAGIC":
				return mdmg * 6.0 + amagic + prayer * 0.1;
			default:
				return str * 4.0 + meleeAttack(weakness) + prayer * 0.1;
		}
	}

	/**
	 * Rough damage-per-tick estimate for a weapon, used when no curated weapon is owned.
	 */
	double weaponScore(String style, String weakness)
	{
		int speed = aspeed > 0 ? aspeed : 4;
		switch (style)
		{
			case "RANGED":
				return arange <= 0 ? 0 : (arange + 64.0) * (rstr + 64.0) / speed;
			case "MAGIC":
				return amagic <= 0 ? 0 : (amagic + 64.0) * (1 + mdmg / 100.0) / speed;
			default:
				int att = meleeAttack(weakness);
				return (att <= 0 && str <= 0) ? 0 : (att + 64.0) * (str + 64.0) / speed;
		}
	}
}
