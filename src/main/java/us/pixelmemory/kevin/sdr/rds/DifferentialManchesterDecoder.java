package us.pixelmemory.kevin.sdr.rds;

import us.pixelmemory.kevin.sdr.BooleanConsumer;

/**
 * Double clocked signal. A double-speed change is 0, a single speed change is 1.
 *
 * @param <T>
 */
public class DifferentialManchesterDecoder<T extends Exception> implements BooleanConsumer<T> {
	private final int firstClock;
	private final int secondClock;
	private final int lastClock;
	private final int missedClock;
	private final int tolerance;
	private final BooleanConsumer<T> out;
	private final boolean transitionSymbol;
	private int lastEdgeCounter;
	private boolean startValue;
	private boolean lastInputBit = false;

	public DifferentialManchesterDecoder(final int samplesPerSymbol, final boolean transitionSymbol, final BooleanConsumer<T> out) {
		firstClock = (int) Math.round(samplesPerSymbol * 0.25) - 1; // Minus one clock for sync
		secondClock = (int) Math.round(samplesPerSymbol * 0.75) - 1; // Minus one clock for sync
		lastClock = samplesPerSymbol;
		tolerance = (int) Math.ceil(samplesPerSymbol * 0.05);
		missedClock = samplesPerSymbol + 2 * tolerance;

		this.transitionSymbol = transitionSymbol;
		this.out = out;
		lastEdgeCounter = 0;
	}

	@Override
	public void accept(final boolean bit) throws T {
		// System.out.println(lastEdgeCounter + " : " + (bit ? "1" : "0") + " (" + firstClock + ", " + secondClock + ", " + lastClock + ", " + missedClock + ")");
		if (lastEdgeCounter == firstClock) {
			startValue = bit;
		} else if (lastEdgeCounter == secondClock) {
			out.accept((startValue == bit) ^ transitionSymbol);
		} else if ((bit != lastInputBit) && (lastEdgeCounter >= (lastClock - tolerance))) {
			// System.out.println(" Sync");
			lastEdgeCounter = 0; // Last edge sync
		} else if (lastEdgeCounter == missedClock) {
			// System.out.println("Missed last edge");
		}
		lastEdgeCounter++; // Always post increment. When sync resets to zero, it's actually clock 1.
		lastInputBit = bit;
	}

	public int getLastEdgeCounter() {
		return lastEdgeCounter;
	}
}
