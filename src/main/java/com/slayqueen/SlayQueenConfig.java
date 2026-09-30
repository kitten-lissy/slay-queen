package com.slayqueen;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayQueenConfig.GROUP)
public interface SlayQueenConfig extends Config
{
	String GROUP = "slayqueen";

	enum OverlayMode
	{
		ALWAYS,
		BANK_AND_NEW_TASK,
		BANK_ONLY
	}

	enum OverlayDetail
	{
		COMPACT,
		FULL
	}

	enum StyleChoice
	{
		AUTO,
		MELEE,
		RANGED,
		MAGIC
	}

	@ConfigItem(
		keyName = "theme",
		name = "Theme",
		description = "Colours for the overlay and side panel",
		position = 0
	)
	default Theme theme()
	{
		return Theme.PINK;
	}

	@ConfigItem(
		keyName = "overlayMode",
		name = "Show overlay",
		description = "When to show the task overlay. 'Bank and new task' shows it only while the bank is open and for a short time after getting a task.",
		position = 1
	)
	default OverlayMode overlayMode()
	{
		return OverlayMode.ALWAYS;
	}

	@ConfigItem(
		keyName = "overlayDetail",
		name = "Overlay detail",
		description = "Compact shows cannon, AoE, requirements and missing items. Full also lists gear and notes. Everything is always in the side panel.",
		position = 2
	)
	default OverlayDetail overlayDetail()
	{
		return OverlayDetail.COMPACT;
	}

	@ConfigItem(
		keyName = "style",
		name = "Combat style",
		description = "Style to pick gear for. Auto uses the recommended style for the task.",
		position = 3
	)
	default StyleChoice style()
	{
		return StyleChoice.AUTO;
	}

	@ConfigItem(
		keyName = "preferAoe",
		name = "Use burst/barrage setups",
		description = "On tasks that are usually stacked and burst/barraged (or chinned), pick an AoE loadout when style is Auto",
		position = 4
	)
	default boolean preferAoe()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGear",
		name = "Show gear",
		description = "List the best gear you own for each slot in the full overlay",
		position = 5
	)
	default boolean showGear()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNotes",
		name = "Show notes",
		description = "Show short tips for the task",
		position = 6
	)
	default boolean showNotes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightBank",
		name = "Highlight in bank",
		description = "Highlight the recommended gear and items in your bank",
		position = 7
	)
	default boolean highlightBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightInventory",
		name = "Highlight in inventory",
		description = "Highlight the recommended gear and items in your inventory",
		position = 8
	)
	default boolean highlightInventory()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Color used to highlight items",
		position = 9
	)
	default Color highlightColor()
	{
		return new Color(255, 105, 180);
	}
}
