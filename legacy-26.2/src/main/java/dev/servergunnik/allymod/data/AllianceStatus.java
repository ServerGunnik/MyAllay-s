package dev.servergunnik.allymod.data;

public enum AllianceStatus {
	KING("Król"),
	DEPUTY("Zastępca"),
	MEMBER("Członek");

	private final String displayName;

	AllianceStatus(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	// Null = wartosc spoza listy; wolajacy traktuje to jak MEMBER.
	public static AllianceStatus fromDisplayName(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		for (AllianceStatus s : values()) {
			if (s.displayName.equalsIgnoreCase(trimmed)) return s;
		}
		return null;
	}
}
