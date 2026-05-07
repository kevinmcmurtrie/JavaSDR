package us.pixelmemory.kevin.sdr.rds;

import us.pixelmemory.kevin.sdr.BooleanFunction;

/**
 * Double clocked signal. A double-speed change is 0, a single speed change is 1.
 *
 * @param <T>
 */
public class DifferentialManchesterDecoder<T extends Exception> implements BooleanFunction<T> {
	private final boolean transitionSymbol;
	private boolean prev = false;

	public DifferentialManchesterDecoder(final boolean transitionSymbol) {
		this.transitionSymbol = transitionSymbol;
	}

	@Override
	public boolean apply(final boolean bit) throws T {
		boolean unchanged= bit == prev;
		prev= bit;
		return unchanged ^ transitionSymbol;
	}
}
