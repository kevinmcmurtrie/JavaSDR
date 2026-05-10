package us.pixelmemory.kevin.sdr.rds;

/**
 * Decoder for the clusterfuck RDS character sets.
 */
public class CharacterTable {
	private static final char set1[] = new char[256];
	private static final char set2[] = new char[256];
	private static final char set3[] = new char[256];
	private static final char sets[][] = new char[][] { set1, set2, set3 };

	//
	static {
		for (int i = 0x20; i <= 0x7e; ++i) {
			set1[i] = set2[i] = set3[i] = (char) i;
		}
				
		setColumn(set1, 0, " 0@P║páâaoÁÂÃã");
		setColumn(set1, 1, "!1AQaqàäα1ÀÄÅå");
		setColumn(set1, 2, "\"2BRbréê©2ÉÊÆæ");
		setColumn(set1, 3, "#3CScsèë‰3ÈËŒœ");
		setColumn(set1, 4, "¤4DTdtíîĞ±ÍÎŷŵ");
		setColumn(set1, 5, "%5EUeuìïěİÌÏÝý");
		setColumn(set1, 6, "&6FVfvóôňńÓÔÕõ");
		setColumn(set1, 7, "'7GWgwòöőűÒÖØø");
		setColumn(set1, 8, "(8HXhxúûπμÚÛÞþ");
		setColumn(set1, 9, ")9IYiyùü€¿ÙÜŊŋ");
		setColumn(set1, 10, "*:JZjzÑñ£÷ŘřŔŕ");
		setColumn(set1, 11, "+;K[k{Çç$°ČčĆć");
		setColumn(set1, 12, ",<L\\l|Şş←\u00bcŠšŚś");
		setColumn(set1, 13, "-=M]m}ßğ↑\u00bdŽžŹź");
		setColumn(set1, 14, ".>N―n¯¡ı→\u00beĐđŦŧ");
		setColumn(set1, 15, "/?O_o \u0132\u0133↓§\u013F\u0140ð ");
		set1[0x7e]= '\u0304';	//This is a combining overline that can't be in a string, or it isn't.  Fucking inconsistent spec.
		set1[0x7f] = set2[0x7f] = set3[0x7f] = ' ';
		set1[0xA] = set2[0xA] = set3[0xA] = 0xA;	//Pass-through newline
		set1[0xD] = set2[0xD] = set3[0xD] = 0xD;	//Pass-through end-of-text
		
		for (int i = 0x20; i <= 0x7e; ++i) {
			if ((i != '$') && (i != '^') && (i != '`') && (i != '~') &&  (set1[i] != i)) {
				throw new IllegalArgumentException ("Self-test failed on character " + (char)i);
			}
		}
		
		//Can't find set2 and set3 in text format.
	}
	
	private static void setColumn(char set[], int row, String chars) {
		if (chars.length() != 14) {
			throw new IllegalArgumentException ("Not a full column (" + chars.length()+ ") : " + chars);
		}
		for (int i= 2; i < 16; ++i) {
			set[16*i + row]= chars.charAt(i-2);
		}
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
