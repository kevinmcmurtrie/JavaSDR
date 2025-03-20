package us.pixelmemory.kevin.sdr.rds.groups;

import java.util.Arrays;
import java.util.Set;

import us.pixelmemory.kevin.sdr.rds.CharacterTable;

/*
 * T0A, T0B
 * 
 * Page 10
 * 1: PI code
 * 2: ....DI C1 C0
 * 3: Alt frequency
 * 4: Character code pair
 * 
 * Dx is reverse index of C1 C0!  00=3, 01=2, 10=1, 11=0
 * D0: 0: Mono, 1: Stereo
 * D1: 0: Not, 1: Is artificial head
 * D2: 0: Not, 1: Is compressed
 * D3: 0: Static PTY, 1: Dynamically switched PTY
 * 
 * Alt frequency:
 * 0: N/A
 * 1: 87.6 MHz
 * 2: 87.7 MHz
 * ...
 * 204: 107.9 MHz
 * 205: Filler
 * 206: Unused
 * ...
 * 223: Unused
 * 224: No alternate frequency
 * 225: 1 alternate frequency
 * ...
 * 249: 25 alternate frequencies
 * 250: LF/MF frequencies
 * 251: Unused
 * ...
 * 255: Unused
 */
public class T0X implements GroupHandler {
	private final char name[]= new char[8];
	private final int charset[]= new int[4];
	private boolean stereo;
	private boolean artificialHead;	//Undefined
	private boolean compressed;		//Undefined
	private boolean dynamicPTY;		//Undefined
	//Alternate frequency unimplemented
	
	public T0X () {
		reset();
	}
	
	public void reset () {
		Arrays.fill(name, ' ');
		Arrays.fill(charset, 0); //0 is default
		stereo= true;
		artificialHead= false;
		compressed= false;
		dynamicPTY= false;
	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public void accept(int a, int b, int c, int d) {
		int idx= b & 3;
		boolean dcb= ((b >> 2) & 1) != 0;
		switch (idx) {
			case 0 -> dynamicPTY= dcb;
			case 1 -> compressed= dcb;
			case 2 -> artificialHead= dcb;
			case 3 -> stereo= dcb;
		}
		setCharacters (idx, d);
	}
	
	private void setCharacters (int idx, int d) {
		if (d == 0x0F0F) {
			charset[idx] = 0;
		} else if (d == 0x0E0E) {
			charset[idx] = 1;
		} else if (d == 0x0B0E) {
			charset[idx] = 2;
		} else {
			name[idx * 2] = CharacterTable.getCharacter(charset[idx], (d >>> 8) & 0xFF);
			name[idx * 2 + 1] = CharacterTable.getCharacter(charset[idx], d & 0xFF);
		}
	}
	
	
	@Override
	public String toString() {
		return "T0X [name=" + new String(name).trim() + ", stereo=" + stereo + ", artificialHead=" + artificialHead + ", compressed=" + compressed + ", dynamicPTY=" + dynamicPTY + "]";
	}

	@Override
	public Set<GroupType> acceptanceTypes() {
		return Set.of(GroupType.T0A, GroupType.T0B);
	}
}
