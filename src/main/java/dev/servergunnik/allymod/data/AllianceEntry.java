package dev.servergunnik.allymod.data;

/**
 * Jeden wpis z configu sojuszu. Etykieta "[Panstwo • Status] " jest liczona raz
 * przy wczytaniu, zeby render loop nie skladal stringow co klatke.
 */
public final class AllianceEntry {
	private final String nick;
	private final boolean allay;
	private final AllianceStatus status;
	private final String kingdom;
	private final String label;

	public AllianceEntry(String nick, boolean allay, AllianceStatus status, String kingdom) {
		this.nick = nick;
		this.allay = allay;
		this.status = status;
		this.kingdom = kingdom;
		this.label = "[" + kingdom + " • " + status.displayName() + "] ";
	}

	public String nick() {
		return nick;
	}

	public boolean allay() {
		return allay;
	}

	public AllianceStatus status() {
		return status;
	}

	public String kingdom() {
		return kingdom;
	}

	public String label() {
		return label;
	}
}
