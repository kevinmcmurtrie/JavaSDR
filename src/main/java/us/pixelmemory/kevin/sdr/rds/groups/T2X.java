package us.pixelmemory.kevin.sdr.rds.groups;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import us.pixelmemory.kevin.sdr.rds.CharacterTable;

/*
 * T2A, T2B
 * 1: PI code
 * 2: GroupType (5), TP, PTY(5), A/B, Text Segment (4)
 * 3: PI code
 * 4: Character code pair 1/2, 3/4, ... 31/32
 */
public class T2X implements GroupHandler {
	private final char text[] = new char[64];
	private final int charset[] = new int[32];
	private boolean textA= false;
	private boolean typeA= false;
	private final AtomicReference<String> cachedText= new AtomicReference<>(null);

	// Alternate frequency unimplemented

	public T2X() {
		reset();
	}

	public void reset() {
		Arrays.fill(text, (char)0x0d);	//Terminator
		Arrays.fill(charset, 0); // 0 is default
		typeA = false;
		textA= false;
		cachedText.set(null);
	}

	@Override
	public void accept(final int a, final int b, final int c, final int d) {
		final int idx= b & 0b1111;
		final boolean isTextA = ((b >> 4) & 1) == 0;
		if (isTextA != textA) {
			reset();
			textA= isTextA;
		}
		
		if (GroupType.fromBlockB(b) == GroupType.T2A) {
			//64 character mode using C and D
			typeA= true;
			setCharacters (2*idx, c);
			setCharacters (2*idx + 1, d);
		} else {
			//32 character mode using  D
			typeA= false;
			setCharacters (idx, d);
		}
	}

	private void setCharacters(final int idx, final int seg) {
		if (seg == 0x0F0F) {
			charset[idx] = 0;
		} else if (seg == 0x0E0E) {
			charset[idx] = 1;
		} else if (seg == 0x0B0E) {
			charset[idx] = 2;
		} else {
			text[idx * 2] = CharacterTable.getCharacter(charset[idx], (seg >>> 8) & 0xFF);
			text[idx * 2 + 1] = CharacterTable.getCharacter(charset[idx], seg & 0xFF);
			cachedText.set(null);
		}
	}
	
	public String getText() {
		String txt = cachedText.get();
		if (txt == null) {
			int len;
			for (len = 0; len < (typeA ? 64 : 32); ++len) {
				if (text[len] == 0x0d) {
					break;	//Terminator
				}
			}
			txt = new String(text, 0, len).trim();
			cachedText.compareAndSet(null, txt);
		}

		return txt;
	}

	@Override
	public String toString() {
		return "T2X: " + getText();
	}

	@Override
	public Set<GroupType> acceptanceTypes() {
		return Set.of(GroupType.T2A, GroupType.T2B);
	}
}
