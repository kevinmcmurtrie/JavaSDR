package us.pixelmemory.kevin.sdr.tuners;

public final class Clock {
	public final double tauPerSample;
	private double clock= 0;

	public Clock(final double sampleRate, final double frequency) {
		if (2*Math.abs(frequency) > sampleRate) {
			throw new IllegalArgumentException("sampleRate is too low: frequency=" + frequency + " sampleRate=" + sampleRate);
		}
		tauPerSample = frequency * Math.TAU / sampleRate;
	}
	
	public Clock(final double tauPerSample) {
		this.tauPerSample = tauPerSample;
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
