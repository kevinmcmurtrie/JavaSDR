package us.pixelmemory.kevin.sdr.iirfilters;

import us.pixelmemory.kevin.sdr.FloatFunction;
import us.pixelmemory.kevin.sdr.SimplerMath;

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
	
	public void setValue (double value) {
		acc= value;
	}
	
	public float applyMaxClamped (final float f, float max) {
		acc += (f - acc) / ratio;
		acc= Math.min(acc, max);
		return (float)acc;
	}
	
	public float applyMinClamped (final float f, float min) {
		acc += (f - acc) / ratio;
		acc= Math.max(acc, min);
		return (float)acc;
	}
	
	public float applyClamped (final float f, float min, float max) {
		acc += (f - acc) / ratio;
		acc= SimplerMath.clamp(acc, min, max);
		return (float)acc;
	}

	@Override
	public float apply(final float f) throws RuntimeException {
		acc += (f - acc) / ratio;
		return (float) acc;
	}
}
