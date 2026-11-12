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
		ALWAYS("Always"),
		BANK_AND_NEW_TASK("Bank & new task"),
		BANK_ONLY("Bank only"),
		NEW_TASK_ONLY("New task only"),
		NEW_TASK_UNTIL_BANK_CLOSE("New task to bank close");

		private final String label;

		OverlayMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
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
		description = "<html>When to show the task overlay (the side panel always has everything):<br><b>Always</b>: whenever you have a task<br><b>Bank &amp; new task</b>: while your bank is open, plus 90 seconds after getting a task<br><b>Bank only</b>: only while your bank is open<br><b>New task only</b>: for 90 seconds after getting a task<br><b>New task to bank close</b>: from getting a task until you first close your bank</html>",
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
		description = "Style to pick gear for. Auto picks the task's style that deals the most damage with your gear.",
		position = 3
	)
	default StyleChoice style()
	{
		return StyleChoice.AUTO;
	}

	@ConfigItem(
		keyName = "useAoe",
		name = "Use AoE when viable",
		description = "<html>On tasks where monsters can be stacked and hit with burst/barrage or chinchompas,<br>pick an AoE loadout when Combat style is Auto. When off, the plugin picks the single-target<br>style that deals the most damage with your gear.</html>",
		position = 4
	)
	default boolean useAoe()
	{
		return false;
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
		keyName = "highlightEquipment",
		name = "Highlight worn items",
		description = "Highlight recommended gear you are already wearing, in the worn equipment tab",
		position = 9
	)
	default boolean highlightEquipment()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Color used to highlight items",
		position = 10
	)
	default Color highlightColor()
	{
		return new Color(255, 105, 180);
	}
}
