package com.slayqueen;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Builder;
import lombok.Value;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Sidebar panel with the full task advice and loadout, one row per item with its game icon.
 * Scrolls, so it fits any window size.
 */
public class SlayQueenPanel extends PluginPanel
{
	private static final int ICON_W = 36;

	/**
	 * Everything the panel shows, gathered on the client thread.
	 */
	@Value
	@Builder
	static class View
	{
		String taskName;
		String location;
		int remaining;
		TaskInfo task;
		LoadoutCalculator.Loadout loadout;
		Theme theme;
		BufferedImage prayerIcon;
		BufferedImage sustainIcon;
		BufferedImage cannonIcon;
		BufferedImage aoeIcon;
		BufferedImage styleIcon;
		BufferedImage weaknessIcon;
		List<TaskInfo.Requirement> unmet;
		List<String> waived;
		/** Item icon lookup by item id (owned items). */
		java.util.function.IntFunction<AsyncBufferedImage> itemImage;
		/** Item icon lookup by name (missing items), may return null. */
		java.util.function.Function<String, AsyncBufferedImage> itemImageByName;
	}

	private final JPanel list = new JPanel();
	private Theme theme = Theme.PINK;

	SlayQueenPanel()
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(list, BorderLayout.NORTH);
		display(null);
	}

	/**
	 * Must be called on the Swing thread.
	 */
	void display(View v)
	{
		list.removeAll();
		theme = v == null || v.getTheme() == null ? Theme.PINK : v.getTheme();

		if (v == null || v.getTaskName() == null)
		{
			addText("No slayer task. Get one from a slayer master.", theme.getDim(), false);
			refresh();
			return;
		}

		addText(v.getTaskName() + " (" + v.getRemaining() + ")", theme.getTitle(), true);
		if (v.getLocation() != null)
		{
			addText(v.getLocation(), theme.getDim(), false);
		}

		TaskInfo task = v.getTask();
		if (task == null)
		{
			addText("No advice for this task yet.", theme.getDim(), false);
			refresh();
			return;
		}

		LoadoutCalculator.Loadout loadout = v.getLoadout();
		if (v.getUnmet() != null && !v.getUnmet().isEmpty())
		{
			section("Before you go");
			for (TaskInfo.Requirement r : v.getUnmet())
			{
				row(null, r.label() + " (missing)", null, r.getReason(), theme.getMissing());
			}
		}
		spacer();
		row(v.getPrayerIcon(), "Pray", SlayQueenOverlay.prayerText(task.getPrayer()), task.getPrayerNote(), theme.getOwned());
		row(v.getSustainIcon(), "Sustain", SlayQueenOverlay.sustainText(task.getSustain()), task.getSustainNote(), theme.getOwned());
		if ("POISON".equals(task.getPoison()) || "VENOM".equals(task.getPoison()))
		{
			row(null, "Poison", SlayQueenOverlay.pretty(task.getPoison()), task.getPoisonNote(), theme.getMissing());
		}
		row(v.getCannonIcon(), "Cannon", SlayQueenOverlay.cannonText(task.getCannon()), task.getCannonNote(),
			"NO".equals(task.getCannon()) ? theme.getMissing() : theme.getOwned());
		String aoeWhere = task.getAoeWhere() == null ? "" : "at " + task.getAoeWhere() + ". ";
		String aoeNote = task.getAoe() == null ? null : aoeWhere + (task.getAoeNote() == null ? "" : task.getAoeNote());
		row(v.getAoeIcon(), "AoE", task.aoeText(), aoeNote, task.aoeStandard() ? theme.getOwned() : theme.getDim());
		row(v.getStyleIcon(), "Attack with", loadout == null ? "" : SlayQueenOverlay.styleLabel(loadout.getStyle()), null, theme.getOwned());
		if (task.weaknessText() != null)
		{
			row(v.getWeaknessIcon(), "Weak to", task.weaknessText(), null, theme.getOwned());
		}

		if (loadout != null)
		{
			needs(v, "Required", loadout.getRequired(), true);
			needs(v, "Bring", loadout.getLoot(), false);
			needs(v, "Recommended", loadout.getRecommended(), false);
			needs(v, "Supplies", loadout.getSupplies(), false);
			travel(v, task, loadout.getTravel());

			section("Best gear you own");
			if (loadout.getGear().isEmpty())
			{
				addText("Open your bank so the plugin can see your gear.", theme.getDim(), false);
			}
			for (Map.Entry<String, LoadoutCalculator.Pick> e : loadout.getGear().entrySet())
			{
				row(v.getItemImage().apply(e.getValue().getItemId()), SlayQueenOverlay.pretty(e.getKey()),
					e.getValue().getName(), null, theme.getOwned());
			}

		}

		if (v.getWaived() != null && !v.getWaived().isEmpty())
		{
			section("Not needed for you");
			for (String w : v.getWaived())
			{
				addText("- " + w, theme.getDim(), false);
			}
		}

		if (task.getNotes() != null && !task.getNotes().isEmpty())
		{
			section("Notes");
			for (String n : task.getNotes())
			{
				addText("- " + n, theme.getDim(), false);
			}
		}

		spacer();
		addText("Tip: search tag:slayqueen in your bank to see only these items.", theme.getDim(), false);
		refresh();
	}

	private void travel(View v, TaskInfo task, List<LoadoutCalculator.NeedStatus> items)
	{
		List<TaskInfo.Teleport> teleports = TaskInfo.orEmpty(task.getTeleports());
		if (teleports.isEmpty())
		{
			return;
		}
		section("Getting there");
		for (TaskInfo.Teleport t : teleports)
		{
			String dest = t.getDestination() == null ? "" : t.getDestination();
			if ("Fairy ring".equals(t.getItem()))
			{
				row(null, "Fairy ring " + (t.getCode() == null ? "" : t.getCode()), null, dest, theme.getOwned());
			}
			else if ("Spirit tree".equals(t.getItem()) || "Spellbook".equals(t.getItem()) || "Minigame teleport".equals(t.getItem()))
			{
				row(null, t.getItem() + (t.getNote() == null ? "" : ": " + t.getNote()), null, dest, theme.getOwned());
			}
			else
			{
				LoadoutCalculator.NeedStatus s = items.stream()
					.filter(n -> n.getNeed().getName().equals(t.getItem())).findFirst().orElse(null);
				boolean have = s != null && s.getOwned() != null;
				BufferedImage icon = have ? v.getItemImage().apply(s.getOwned().getItemId()) : v.getItemImageByName().apply(t.getItem());
				row(icon, (have ? s.getOwned().getName() : t.getItem() + " (missing)"), null, dest,
					have ? theme.getOwned() : theme.getDim());
			}
		}
	}

	private void needs(View v, String label, List<LoadoutCalculator.NeedStatus> statuses, boolean missingIsBad)
	{
		if (statuses == null || statuses.isEmpty())
		{
			return;
		}
		section(label);
		for (LoadoutCalculator.NeedStatus s : statuses)
		{
			boolean have = s.getOwned() != null;
			BufferedImage icon = have
				? v.getItemImage().apply(s.getOwned().getItemId())
				: v.getItemImageByName().apply(s.getNeed().getName());
			String name = have ? s.getOwned().getName() : s.getNeed().getName();
			Color c = have ? theme.getOwned() : (missingIsBad ? theme.getMissing() : theme.getDim());
			row(icon, name + (have ? "" : " (missing)"), null, s.getNeed().getReason(), c);
		}
	}

	/**
	 * One row: icon on the left, "label: value" and an optional note underneath.
	 */
	private void row(BufferedImage icon, String label, String value, String note, Color valueColor)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		r.setBorder(new EmptyBorder(3, 4, 3, 4));
		r.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(ICON_W, 32));
		iconLabel.setHorizontalAlignment(JLabel.CENTER);
		if (icon instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) icon).addTo(iconLabel);
		}
		else if (icon != null)
		{
			iconLabel.setIcon(new ImageIcon(icon));
		}
		r.add(iconLabel, BorderLayout.WEST);

		StringBuilder h = new StringBuilder("<html><body style='width:150px'>");
		if (value == null)
		{
			h.append(color(valueColor, esc(label)));
		}
		else
		{
			h.append(color(theme.getDim(), esc(label) + ": ")).append(color(valueColor, esc(value)));
		}
		if (note != null && !note.isEmpty())
		{
			h.append("<br>").append(color(theme.getDim(), esc(note)));
		}
		JLabel text = new JLabel(h.append("</body></html>").toString());
		text.setFont(FontManager.getRunescapeSmallFont());
		r.add(text, BorderLayout.CENTER);

		list.add(r);
		list.add(gap(2));
	}

	private void section(String title)
	{
		list.add(gap(6));
		addText(title, theme.getHeader(), true);
	}

	private void addText(String text, Color c, boolean bold)
	{
		JLabel l = new JLabel("<html><body style='width:190px'>" + esc(text) + "</body></html>");
		l.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		l.setForeground(c);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(2, 2, 2, 2));
		list.add(l);
	}

	private void spacer()
	{
		list.add(gap(6));
	}

	private static Component gap(int h)
	{
		JPanel g = new JPanel();
		g.setOpaque(false);
		g.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		g.setPreferredSize(new Dimension(0, h));
		g.setAlignmentX(Component.LEFT_ALIGNMENT);
		return g;
	}

	private void refresh()
	{
		list.revalidate();
		list.repaint();
	}

	private static String color(Color c, String text)
	{
		return "<font color='" + Theme.hex(c) + "'>" + text + "</font>";
	}

	private static String esc(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
