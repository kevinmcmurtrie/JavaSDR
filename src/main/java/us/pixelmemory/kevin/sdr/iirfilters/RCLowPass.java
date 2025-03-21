package us.pixelmemory.kevin.sdr.iirfilters;

import us.pixelmemory.kevin.sdr.FloatFunction;

public final class RCLowPass implements FloatFunction<RuntimeException> {
	private final double ratio;
	private double acc = 0d;

	public RCLowPass(final double sampleRate, final double r, final double c) {
		this(sampleRate, r * c);
	}

	public RCLowPass(final double sampleRate, final double tau) {
		this(tau * sampleRate);
	}

	public RCLowPass(final double ratio) {
		this.ratio = ratio;
	}

	@Override
	public float apply(final float f) throws RuntimeException {
		acc += (f - acc) / ratio;
		return (float) acc;
	}
}
