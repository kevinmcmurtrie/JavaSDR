package us.pixelmemory.kevin.sdr.firfilters;

public record BandPassFIR(LanczosTable lanczos, float lowScale, float highScale, int lowLatency, int highLatency, float lowSum, float highSum, float gain) implements FilterFIR {
	@Override
	public int latency() {
		return lowLatency;
	}

	@Override
	public float apply(final float[] circBuf, final int pos) {
		final int mask = circBuf.length - 1;
		final int lowStart = pos - lowLatency;
		final int lowEnd = pos + lowLatency;
		final int highStart = pos - highLatency;
		final int highEnd = pos + highLatency;

		float lowAcc = 0f;
		// Lowest end of larger low band
		float lowLzPos = -lowLatency * lowScale;
		for (int i = lowStart; i < highStart; ++i, lowLzPos += lowScale) {
			lowAcc += lanczos.apply(lowLzPos) * circBuf[i & mask];
		}

		// Overlapping area
		float highLzPos = -highLatency * highScale;
		float highAcc = 0f;
		for (int i = highStart; i <= highEnd; ++i, lowLzPos += lowScale, highLzPos += highScale) {
			final float sample = circBuf[i & mask];
			lowAcc += lanczos.apply(lowLzPos) * sample;
			highAcc += lanczos.apply(highLzPos) * sample;
		}

		// Highest end of larger low band
		for (int i = highEnd + 1; i <= lowEnd; ++i, lowLzPos += lowScale) {
			lowAcc += lanczos.apply(lowLzPos) * circBuf[i & mask];
		}

		return gain * ((highAcc / highSum) - (lowAcc / lowSum));
	}
}