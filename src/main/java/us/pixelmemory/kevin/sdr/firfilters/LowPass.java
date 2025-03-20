package us.pixelmemory.kevin.sdr.firfilters;

public record LowPass(LanczosTable lanczos, float highBand) implements FilterBuilder {
	@Override
	public LowPassFIR build(final float sampleRate) {
		final float highScale = (float) (2d * highBand / sampleRate);
		final int highLatency = (int) Math.ceil(0.5 * lanczos.A * sampleRate / highBand);
		float highSum = 0f;
		float highLzPos = -highLatency * highScale;
		for (int i = -highLatency; i <= highLatency; ++i, highLzPos += highScale) {
			highSum += lanczos.apply(highLzPos);
		}
		return new LowPassFIR(lanczos, highScale, highLatency, highSum);
	}
}