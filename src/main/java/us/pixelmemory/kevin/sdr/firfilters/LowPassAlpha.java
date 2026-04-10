package us.pixelmemory.kevin.sdr.firfilters;

public record LowPassAlpha(LanczosTable lanczos, float highBand) implements FilterAlphaBuilder {
	@Override
	public LowPassAlphaFIR build(final float sampleRate) {
		final float highScale = (float) (2d * highBand / sampleRate);
		final int highLatency = (int) Math.ceil(0.5 * lanczos.A * sampleRate / highBand);
		return new LowPassAlphaFIR(lanczos, highScale, highLatency);
	}
}