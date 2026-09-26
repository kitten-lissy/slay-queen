package com.slayqueen;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the recommended gear and items in the bank (and optionally the inventory).
 */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private final SlayQueenPlugin plugin;
	private final SlayQueenConfig config;
	private final ItemManager itemManager;

	@Inject
	ItemHighlightOverlay(SlayQueenPlugin plugin, SlayQueenConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnBank();
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		boolean inBank = widgetItem.getWidget() != null
			&& (widgetItem.getWidget().getId() >>> 16) == InterfaceID.BANKMAIN;
		if (inBank ? !config.highlightBank() : !config.highlightInventory())
		{
			return;
		}
		if (!plugin.getHighlightIds().contains(itemId))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.highlightColor());
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
	}
}
