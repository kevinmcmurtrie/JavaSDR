package us.pixelmemory.kevin.sdr.rds;

// FIXME!
public enum ProgramTypeUS implements ProgramType {
	// Ordinal is the code
	None(""), /* */
	News, /* */
	Information, /* */
	Sports, /* */
	Talk, /* */
	Rock, /* */
	ClassicRock("Classic Rock"), /* */
	AdultHits("Adult Hits"), /* */
	SoftRock("Soft Rock"), /* */
	Top40("Top 40"), /* */
	Country, /* */
	Oldies, /* */
	Soft, /* */
	Nostalgia, /* */
	Jazz, /* */
	Classical, /* */
	RhythmAndBlues("Rhythm and Blues"), /* */
	SoftRhythmAndBlues("Soft Rhythm and Blues"), /* */
	Foreign("Foreign Language"), /* */
	ReligiousMusic("Religious Music"), /* */
	ReligiousTalk("Religious Talk"), /* */
	Personality, /* */
	Public, /* */
	College, /* */
	SpanishTalk("Hablar Espanol"), /* */
	SpanishMusic("Musica Espanol"), /* */
	HipHop("Hip-Hop"), /* */
	Unassigned27("Code 27"), /* */
	Unassigned28("Code 28"), /* */
	Weather, /* */
	EmergencyTest("Emergency Test "), /* */
	Emergency("ALERT! ALERT !"); /* */

	public final String title;

	@Override
	public int code() {
		return ordinal();
	}

	@Override
	public String title() {
		return title;
	}

	public static ProgramTypeUS ofCode(final int code) {
		return values[code & 0b11111];
	}

	private static final ProgramTypeUS values[] = ProgramTypeUS.values();

	private ProgramTypeUS(final String title) {
		this.title = title;
	}

	private ProgramTypeUS() {
		this.title = this.name();
	}

}
