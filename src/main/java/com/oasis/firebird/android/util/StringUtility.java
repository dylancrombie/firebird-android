package com.oasis.firebird.android.util;

public class StringUtility {

	public static String ellipsize(String input, int maxCharacters, int charactersAfterEllipsis) {
		if (maxCharacters < 3) {
			throw new IllegalArgumentException("maxCharacters must be at least 3 because the ellipsis already take up 3 characters");
		}
		if (maxCharacters - 3 < charactersAfterEllipsis) {
			throw new IllegalArgumentException("charactersAfterEllipsis must be less than maxCharacters");
		}
		if (input == null || input.length() < maxCharacters) {
			return input;
		}
		return input.substring(0, maxCharacters - 3 - charactersAfterEllipsis) + "..." + input.substring(input.length() - charactersAfterEllipsis);
	}
}
