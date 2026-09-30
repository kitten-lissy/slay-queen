package com.slayqueen;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemPrice;

/**
 * Game sprites and item icons for the overlay and panel. Sprite lookups must happen on the client thread.
 */
@Singleton
public class Icons
{
	static final int SIZE = 16;
	private static final int NO_ITEM = -1;

	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final Map<Integer, BufferedImage> sprites = new HashMap<>();
	private final Map<Integer, BufferedImage> items = new ConcurrentHashMap<>();
	private final Set<Integer> requested = ConcurrentHashMap.newKeySet();
	private final Map<String, Integer> idsByName = new ConcurrentHashMap<>();

	@Inject
	Icons(SpriteManager spriteManager, ItemManager itemManager)
	{
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
	}

	BufferedImage sprite(int spriteId)
	{
		BufferedImage cached = sprites.get(spriteId);
		if (cached != null)
		{
			return cached;
		}
		BufferedImage img = spriteManager.getSprite(spriteId, 0);
		if (img == null)
		{
			return null;
		}
		BufferedImage scaled = ImageUtil.resizeImage(img, SIZE, SIZE, true);
		sprites.put(spriteId, scaled);
		return scaled;
	}

	/**
	 * Small item icon for the overlay. Returns null until the image has loaded.
	 */
	BufferedImage item(int itemId)
	{
		BufferedImage cached = items.get(itemId);
		if (cached != null)
		{
			return cached;
		}
		if (!requested.add(itemId))
		{
			return null;
		}
		net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(itemId);
		img.onLoaded(() -> items.put(itemId, ImageUtil.resizeImage(img, SIZE + 2, SIZE, true)));
		return null;
	}

	/**
	 * Item id for a name, for items the player doesn't own yet. Only tradeable items can be found.
	 */
	int idForName(String name)
	{
		Integer cached = idsByName.get(name);
		if (cached != null)
		{
			return cached;
		}
		List<ItemPrice> results = itemManager.search(name);
		if (results.isEmpty())
		{
			// Prices may not have loaded yet; try again later rather than caching a miss
			return NO_ITEM;
		}
		int id = NO_ITEM;
		for (ItemPrice p : results)
		{
			// Exact name only, so "Bonecrusher" doesn't show the Bonecrusher necklace
			if (p.getName().equalsIgnoreCase(name))
			{
				id = p.getId();
				break;
			}
		}
		idsByName.put(name, id);
		return id;
	}

	BufferedImage itemByName(String name)
	{
		int id = idForName(name);
		return id == NO_ITEM ? null : item(id);
	}

	BufferedImage cannon()
	{
		return item(ItemID.MCANNONBALL);
	}

	BufferedImage aoe(String aoe)
	{
		if ("CHINCHOMPA".equals(aoe))
		{
			return item(ItemID.CHINCHOMPA_BLACK);
		}
		return sprite("BURST".equals(aoe) ? SpriteID.Magicon2.ICE_BURST : SpriteID.Magicon2.ICE_BARRAGE);
	}

	/**
	 * Rune icon for an elemental weakness, or null.
	 */
	BufferedImage weakness(String weakness)
	{
		if (weakness == null)
		{
			return null;
		}
		switch (weakness.toLowerCase(java.util.Locale.ROOT))
		{
			case "air":
				return item(ItemID.AIRRUNE);
			case "water":
				return item(ItemID.WATERRUNE);
			case "earth":
				return item(ItemID.EARTHRUNE);
			case "fire":
				return item(ItemID.FIRERUNE);
			default:
				return null;
		}
	}

	BufferedImage style(String style)
	{
		switch (LoadoutCalculator.baseStyle(style == null ? "MELEE" : style))
		{
			case "RANGED":
				return sprite(SpriteID.Staticons.RANGED);
			case "MAGIC":
				return sprite(SpriteID.Staticons.MAGIC);
			default:
				return sprite(SpriteID.Staticons.ATTACK);
		}
	}
}
