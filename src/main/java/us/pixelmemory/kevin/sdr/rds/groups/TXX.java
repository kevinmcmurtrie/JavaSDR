package us.pixelmemory.kevin.sdr.rds.groups;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/*
 * T2A, T2B
 * 1: PI code
 * 2: GroupType (5), TP, PTY(5), A/B, Text Segment (4)
 * 3: PI code
 * 4: Character code pair 1/2, 3/4, ... 31/32
 */
public class TXX implements GroupHandler {
	private int a, b, c, d;
	private final AtomicReference<String> cachedText= new AtomicReference<>(null);

	// Alternate frequency unimplemented

	public TXX() {
		reset();
	}

	public void reset() {
		a = b = c = d = 0;
		cachedText.set(null);
	}

	@Override
	public void accept(final int a, final int b, final int c, final int d) {
		this.a= a;
		this.b= b;
		this.c=c;
		this.d= d;
		cachedText.set(null);
	}


	
	@Override
	public String toString() {
		String txt = cachedText.get();
		if (txt == null) {
			txt= GroupType.fromBlockB(b).name() + " PI=" + Integer.toHexString(a) +" TP=" + ((b>>>10) & 1) + " PTY=" + Integer.toHexString((b>>>5) & 0b11111) + " B=" + Integer.toHexString(b & 0b11111) + " C=" + Integer.toHexString(c)  + " D=" + Integer.toHexString(d);
			cachedText.compareAndSet(null, txt);
		}

		return txt;
	}

	@Override
	public Set<GroupType> acceptanceTypes() {
		return Set.of();
	}
}
