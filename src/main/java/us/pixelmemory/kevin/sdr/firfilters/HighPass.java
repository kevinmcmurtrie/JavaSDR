package us.pixelmemory.kevin.sdr.firfilters;

public record HighPass(LanczosTable lanczos, float lowBand) implements FilterBuilder {
	@Override
	public FilterFIR build(final float sampleRate) {
		final int lowLatency = (int) Math.ceil(0.5 * lanczos.A * sampleRate / lowBand);
		final float lowScale = (float) (2d * lowBand / sampleRate);
		float lowSum = 0f;
		float lowLzPos = -lowLatency * lowScale;
		for (int i = -lowLatency; i <= lowLatency; ++i, lowLzPos += lowScale) {
			lowSum += lanczos.apply(lowLzPos);
		}
		return new HighPassFIR(lanczos, lowScale, lowLatency, lowSum);
	}
}