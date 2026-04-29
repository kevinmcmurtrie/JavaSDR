package us.pixelmemory.kevin.sdr.iirfilters;

import us.pixelmemory.kevin.sdr.FloatFunction;

public final class RCHighPass implements FloatFunction<RuntimeException> {
	private final double ratio;
	private double acc = 0d;

	public RCHighPass(final double sampleRate, final double r, final double c) {
		this(sampleRate, r * c);
	}

	public RCHighPass(final double sampleRate, final double tau) {
		this(tau * sampleRate);
	}

	public RCHighPass(final double ratio) {
		this.ratio = ratio;
	}
	
	public void setValue (double value) {
		acc= value;
	}

	@Override
	public float apply(final float f) throws RuntimeException {
		acc += (f - acc) / ratio;
		return f - (float) acc;
	}
}
