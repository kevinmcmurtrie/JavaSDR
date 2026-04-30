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
	
	public double getClock() {
		return clock;
	}
	
	public double getAndTick() {
		final double c= clock;
		clockTick();
		return c;
	}
	
	public double getAndTick(final double adjustment) {
		final double c= clock;
		clockTick(adjustment);
		return c;
	}
	
	public void clockTick() {
		clock += tauPerSample;
		if (clock > Math.PI) {
			clock -= Math.TAU;
		}
		if (clock < -Math.PI) {
			clock += Math.TAU;
		}
	}
	
	public void clockTick(final double adjustment) {
		clock += tauPerSample + adjustment;
		if (clock > Math.PI) {
			clock -= Math.TAU;
		}
		if (clock < -Math.PI) {
			clock += Math.TAU;
		}
	}
}
