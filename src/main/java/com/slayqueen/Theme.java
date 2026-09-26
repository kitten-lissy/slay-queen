package com.slayqueen;

import java.awt.Color;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Colour sets for the overlay and side panel.
 */
@Getter
@AllArgsConstructor
public enum Theme
{
	PINK(
		new Color(70, 22, 52, 215),
		new Color(255, 105, 180),
		new Color(255, 182, 219),
		new Color(255, 214, 236),
		new Color(255, 92, 138),
		new Color(236, 196, 218)),
	CLASSIC(
		null,
		new Color(255, 200, 80),
		new Color(255, 200, 80),
		new Color(90, 220, 110),
		new Color(255, 90, 90),
		new Color(190, 190, 190));

	/** Overlay background, or null for RuneLite's default. */
	private final Color background;
	private final Color title;
	private final Color header;
	private final Color owned;
	private final Color missing;
	private final Color dim;

	static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}
