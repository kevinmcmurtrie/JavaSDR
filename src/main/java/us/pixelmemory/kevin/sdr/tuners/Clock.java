package us.pixelmemory.kevin.sdr.tuners;

/**
 * Clock helper class.  This is in double precision to avoid jitter, squealing, and unpredictable drift.
 */
public final class Clock {
	public final double tauPerSample;
	private double clock= 0;

	public Clock(final double sampleRate, final double frequency) {
		tauPerSample = frequency * Math.TAU / sampleRate;
		if (Math.abs(tauPerSample) >= Math.PI) {
			throw new IllegalArgumentException("sampleRate is too low: frequency=" + frequency + " sampleRate=" + sampleRate);
		}
	}
	
	public Clock(final double tauPerSample) {
		this.tauPerSample = tauPerSample;
		if (Math.abs(tauPerSample) >= Math.PI) {
			throw new IllegalArgumentException("Invalid tauPerSample=" + tauPerSample);
		}
	}
	
	public double tickAndGet() {
		clockTick();
		return getClock();
	}
	
	public double tickAndGet(final double adjustment) {
		clockTick(adjustment);
		return getClock();
	}
	
	public double getClock() {
		return clock;
	}
	
	public void clockTick() {
		clock = wrapClock(clock + tauPerSample);
	}

	public void clockTick(final double adjustment) {
		clock = wrapClock(clock + tauPerSample + adjustment);
	}
	
	public static double wrapClock (double clock) {
		if (clock > Math.PI) {
			return clock - Math.TAU;
		}
		if (clock < -Math.PI) {
			return clock + Math.TAU;
		}
		return clock;
	}
}
