package us.pixelmemory.kevin.sdr.rds;

public class CharacterTable {
	private static final char set1[] = new char[256];
	private static final char set2[] = new char[256];
	private static final char set3[] = new char[256];
	private static final char sets[][] = new char[][] { set1, set2, set3 };

	static {
		for (int i = 0x20; i <= 0x7e; ++i) {
			set1[i] = set2[i] = set3[i] = (char) i;
		}
		set1[0x7f] = set2[0x7f] = set3[0x7f] = ' ';
		// Thanks for the blurry bitmap for the other 380 characters. Supper helpful, RDS team.
		set1[0x80] = 'á';
		set1[0x81] = 'à';
		set1[0x8c] = 'Ş';
		set1[0x8d] = 'β';
		set1[0x90] = 'â';
		set1[0xbc] = '¼';
		set1[0xbd] = '½';
		set1[0xc7] = 'Ò';
		set1[0xd2] = 'Ê';
		set1[0xde] = 'đ';
		set1[0xe5] = 'Ý';
	}

	public static char getCharacter(final int set, final int code) {
		if ((set < 0) || (set > 2) || (code < 0) || (code > 255)) {
			throw new IllegalArgumentException("set: " + set + " code:" + code);
		}
		final char c = sets[set][code];
		if (c == 0) {
			System.out.println("Need mapping for set: " + set + " code:" + Integer.toHexString(code));
			return ' ';
		}
		return c;
	}
}
