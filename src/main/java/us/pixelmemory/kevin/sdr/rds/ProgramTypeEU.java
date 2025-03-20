package us.pixelmemory.kevin.sdr.rds;

public enum ProgramTypeEU implements ProgramType {
	// Ordinal is the code
	None(""), /* */
	News, /* */
	CurrentAffairs("Current Affairs"), /* */
	Information, /* */
	Sport, /* */
	Education, /* */
	Drama, /* */
	Cultures, /* */
	Science, /* */
	VariedSpeech("Varied Speech"), /* */
	PopMusic("Pop Music"), /* */
	RockMusic("Rock Music"), /* */
	EasyListening("Easy Listening"), /* */
	LightClassics("Light Classics Music"), /* */
	SeriousClassics("Serious Classics"), /* */
	OtherMusic("Other Music"), /* */
	Weather("Weather & Meteorology"), /* */
	Finance, /* */
	Childrens("Children’s Programs"), /* */
	SocialAffairs("Social Affairs"), /* */
	Religion, /* */
	PhoneIn("Phone In"), /* */
	Travel("Travel & Touring"), /* */
	Leisure("Leisure & Hobby"), /* */
	JazzMusic("Jazz Music"), /* */
	CountryMusic("Country Music"), /* */
	NationalMusic("National Music"), /* */
	OldiesMusic("Oldies Music"), /* */
	FolkMusic("Folk Music"), /* */
	Documentary, /* */
	Test("Alarm Test"), /* */
	Alarm("Alarm - Alarm !"); /* */

	public final String title;

	@Override
	public int code() {
		return ordinal();
	}

	@Override
	public String title() {
		return title;
	}

	public static ProgramTypeEU ofCode(final int code) {
		return values[code & 0b11111];
	}

	private static final ProgramTypeEU values[] = ProgramTypeEU.values();

	private ProgramTypeEU(final String title) {
		this.title = title;
	}

	private ProgramTypeEU() {
		this.title = this.name();
	}
}
