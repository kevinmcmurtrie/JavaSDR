package us.pixelmemory.kevin.sdr.firfilters;

/**
 * Lanczos A should be a higher level for narrower bands.
 */
public record BandPass(LanczosTable lanczos, float lowBand, float highBand) implements FilterBuilder {
	@Override
	public BandPassFIR build(final float sampleRate) {
		// IQVisualizer vis = new IQVisualizer();

		// Lanczos distance for half wave
		final int lowLatency = (int) Math.ceil(0.5 * lanczos.A * sampleRate / lowBand);
		final int highLatency = (int) Math.ceil(0.5 * lanczos.A * sampleRate / highBand);
		final float lowScale = (float) (2d * lowBand / sampleRate);
		final float highScale = (float) (2d * highBand / sampleRate);

		// Test run at center frequency to get filter gain.
		// I don't know the formula to compensate for the gain drifting as lowScale and highScale converge.
		final double clockRate = (lowBand + highBand) * Math.PI / sampleRate;
		if (Math.abs(clockRate) >= Math.PI) {
			throw new IllegalArgumentException("Sample rate too low");
		}
		double clock = clockRate * -lowLatency;

		float lowSum = 0;
		float highSum = 0;
		float lowAcc = 0;
		float highAcc = 0;

		// Lowest end of larger low band
		float lowLzPos = -lowLatency * lowScale;
		for (int i = -lowLatency; i < -highLatency; ++i, lowLzPos += lowScale, clock += clockRate) {
			final double sample = Math.cos(clock);
			final float lzLow = lanczos.apply(lowLzPos);
			lowSum += lzLow;
			lowAcc += lzLow * sample;
			// for (int x = 0; x < 7; ++x) {
			// vis.drawAnalog(Color.red, sample);
			// vis.drawAnalog(Color.green, lzLow);
			// vis.drawAnalog(Color.blue, 0);
			// }
		}

		// Overlapping area
		float highLzPos = -highLatency * highScale;
		for (int i = -highLatency; i <= highLatency; ++i, lowLzPos += lowScale, highLzPos += highScale, clock += clockRate) {
			final double sample = Math.cos(clock);
			final float lzLow = lanczos.apply(lowLzPos);
			lowSum += lzLow;
			lowAcc += lzLow * sample;
			final float lzHigh = lanczos.apply(highLzPos);
			highSum += lzHigh;
			highAcc += lzHigh * sample;
			// for (int x = 0; x < 7; ++x) {
			// vis.drawAnalog(Color.red, sample);
			// vis.drawAnalog(Color.green, lzLow);
			// vis.drawAnalog(Color.blue, lzHigh);
			// }
		}

		// Highest end of larger low band
		for (int i = highLatency + 1; i <= lowLatency; ++i, lowLzPos += lowScale, clock += clockRate) {
			final double sample = Math.cos(clock);
			final float lzLow = lanczos.apply(lowLzPos);
			lowSum += lzLow;
			lowAcc += lzLow * sample;
			// for (int x = 0; x < 7; ++x) {
			// vis.drawAnalog(Color.red, sample);
			// vis.drawAnalog(Color.green, lzLow);
			// vis.drawAnalog(Color.blue, 0);
			// }
		}

		// vis.repaint();
		// vis.close();
		return new BandPassFIR(lanczos, lowScale, highScale, lowLatency, highLatency, lowSum, highSum, (float) (1d / ((highAcc / highSum) - (lowAcc / lowSum))));
	}
}