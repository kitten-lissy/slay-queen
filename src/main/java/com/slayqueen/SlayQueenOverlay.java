package com.slayqueen;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import java.awt.Point;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class SlayQueenOverlay extends OverlayPanel
{

	private final SlayQueenPlugin plugin;
	private static final int WIDTH = 230;
	private static final BufferedImage BLANK = new BufferedImage(Icons.SIZE + 2, Icons.SIZE, BufferedImage.TYPE_INT_ARGB);

	private final SlayQueenConfig config;
	private final Icons icons;
	private Theme theme = Theme.PINK;
	private boolean compact;

	@Inject
	SlayQueenOverlay(SlayQueenPlugin plugin, SlayQueenConfig config, Icons icons)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.icons = icons;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
		panelComponent.setGap(new Point(0, 2));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.shouldShowOverlay())
		{
			return null;
		}
		theme = config.theme();
		compact = config.overlayDetail() == SlayQueenConfig.OverlayDetail.COMPACT;
		panelComponent.setBackgroundColor(theme.getBackground() != null ? theme.getBackground() : net.runelite.client.ui.overlay.components.ComponentConstants.STANDARD_BACKGROUND_COLOR);

		String taskName = plugin.getCurrentTaskName();
		String title = taskName + " (" + plugin.getRemaining() + ")";
		panelComponent.getChildren().add(TitleComponent.builder().text(title).color(theme.getTitle()).build());
		if (plugin.getCurrentLocation() != null)
		{
			line("Location", plugin.getCurrentLocation(), theme.getDim());
		}

		TaskInfo task = plugin.getDisplayTask();
		if (task == null)
		{
			line("No advice for this task yet", "", theme.getDim());
			return super.render(graphics);
		}

		for (TaskInfo.Requirement r : plugin.getUnmetRequirements())
		{
			iconLine(null, "Needs " + r.label(), "", theme.getMissing());
		}

		if ("POISON".equals(task.getPoison()) || "VENOM".equals(task.getPoison()))
		{
			iconLine(null, "Poison", pretty(task.getPoison()), theme.getMissing());
		}
		iconLine(icons.cannon(), "Cannon", cannonText(task.getCannon()), cannonColor(task.getCannon()));
		if ("SOME".equals(task.getCannon()) && task.getCannonNote() != null)
		{
			// Where you can/can't cannon is the useful part, so show it even in compact mode
			iconLine(null, task.getCannonNote(), "", theme.getDim());
		}
		else
		{
			note(task.getCannonNote());
		}

		iconLine(icons.aoe(task.getAoe() == null ? "BARRAGE" : task.getAoe()), "AoE", task.aoeText(),
			task.aoeStandard() ? theme.getOwned() : theme.getDim());
		if (task.getAoe() != null && task.getAoeWhere() != null)
		{
			iconLine(null, "at " + task.getAoeWhere(), "", theme.getDim());
		}
		if (task.getAoe() != null)
		{
			note(task.getAoeNote());
		}

		LoadoutCalculator.Loadout loadout = plugin.getLoadout();
		String style = loadout == null ? "" : styleLabel(loadout.getStyle());
		iconLine(icons.style(loadout == null ? null : loadout.getStyle()), "Attack with", style, theme.getOwned());
		if (task.weaknessText() != null)
		{
			iconLine(icons.weakness(task.getWeakness()), "Weak to", task.weaknessText(), theme.getOwned());
		}

		if (config.overlayDetail() == SlayQueenConfig.OverlayDetail.COMPACT)
		{
			if (loadout != null)
			{
				missingOnly(loadout.getRequired());
			}
			return super.render(graphics);
		}

		if (loadout != null)
		{
			needs("Required", loadout.getRequired(), true);
			needs("Bring", loadout.getLoot(), false);
			needs("Recommended", loadout.getRecommended(), false);

			if (config.showGear() && !loadout.getGear().isEmpty())
			{
				header("Best gear you own");
				for (Map.Entry<String, LoadoutCalculator.Pick> e : loadout.getGear().entrySet())
				{
					iconLine(icons.item(e.getValue().getItemId()), pretty(e.getKey()), e.getValue().getName(), theme.getOwned());
				}
			}
		}

		if (config.showNotes() && task.getNotes() != null && !task.getNotes().isEmpty())
		{
			header("Notes");
			for (String n : task.getNotes())
			{
				panelComponent.getChildren().add(LineComponent.builder().left("- " + n).leftColor(theme.getDim()).build());
			}
		}

		return super.render(graphics);
	}

	private void needs(String label, List<LoadoutCalculator.NeedStatus> statuses, boolean missingIsBad)
	{
		if (statuses == null || statuses.isEmpty())
		{
			return;
		}
		header(label);
		for (LoadoutCalculator.NeedStatus s : statuses)
		{
			boolean have = s.getOwned() != null;
			String name = have ? s.getOwned().getName() : s.getNeed().getName();
			Color color = have ? theme.getOwned() : (missingIsBad ? theme.getMissing() : theme.getDim());
			BufferedImage icon = have ? icons.item(s.getOwned().getItemId()) : icons.itemByName(s.getNeed().getName());
			iconLine(icon, name, have ? "have" : "missing", color);
		}
	}

	private void missingOnly(List<LoadoutCalculator.NeedStatus> required)
	{
		for (LoadoutCalculator.NeedStatus s : required)
		{
			if (s.getOwned() == null)
			{
				iconLine(icons.itemByName(s.getNeed().getName()), "Need", s.getNeed().getName(), theme.getMissing());
			}
		}
	}

	private void iconLine(BufferedImage icon, String left, String right, Color rightColor)
	{
		LineComponent text = LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.preferredSize(new Dimension(WIDTH - 8 - (Icons.SIZE + 2) - 4, 0))
			.build();
		panelComponent.getChildren().add(SplitComponent.builder()
			.first(new ImageComponent(icon != null ? icon : BLANK))
			.second(text)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(new Point(4, 0))
			.build());
	}

	private void header(String text)
	{
		panelComponent.getChildren().add(LineComponent.builder().left(text).leftColor(theme.getHeader()).build());
	}

	private void line(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.build());
	}

	private void note(String text)
	{
		if (!compact && text != null && !text.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("  " + text).leftColor(theme.getDim()).build());
		}
	}

	static String cannonText(String cannon)
	{
		if (cannon == null)
		{
			return "?";
		}
		switch (cannon)
		{
			case "YES":
				return "Yes";
			case "NO":
				return "No";
			default:
				return "Some areas";
		}
	}

	private Color cannonColor(String cannon)
	{
		if ("NO".equals(cannon))
		{
			return theme.getMissing();
		}
		return theme.getOwned();
	}

	static String styleLabel(String style)
	{
		if (LoadoutCalculator.AOE_MAGIC.equals(style))
		{
			return "Magic (Ancients)";
		}
		if (LoadoutCalculator.CHINCHOMPA.equals(style))
		{
			return "Chinchompas";
		}
		return pretty(style);
	}

	static String pretty(String upper)
	{
		if (upper == null || upper.isEmpty())
		{
			return "";
		}
		String lower = upper.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
