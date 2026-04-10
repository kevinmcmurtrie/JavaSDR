package us.pixelmemory.kevin.sdr.firfilters;

public record LowPassAlphaFIR(LanczosTable lanczos, float highScale, int latency) implements FilterAlphaFIR {
	@Override
	public float apply(final float[] circBufSignal, final float[] circBufAlpha , final int pos) {
		final int mask = circBufSignal.length - 1;
		final int highStart = pos - latency;
		final int highEnd = pos + latency;
		float highLzPos = -latency * highScale;
		float highAcc = 0f;
		float sum= 0;
		for (int i = highStart; i <= highEnd; ++i, highLzPos += highScale) {
			int idx= i & mask;
			final float lz= lanczos.apply(highLzPos) * circBufAlpha[idx];
			highAcc += lz * circBufSignal[idx];
			sum+= lz;
		}
		return (sum != 0) ? highAcc / sum : 0;
	}
}