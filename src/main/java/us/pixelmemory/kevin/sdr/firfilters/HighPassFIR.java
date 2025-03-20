package us.pixelmemory.kevin.sdr.firfilters;

public record HighPassFIR(LanczosTable lanczos, float lowScale, int latency, float sum) implements FilterFIR {
	@Override
	public float apply(final float[] circBuf, final int pos) {
		final int mask = circBuf.length - 1;
		final int highStart = pos - latency;
		final int highEnd = pos + latency;
		float lowLzPos = -latency * lowScale;
		float highAcc = 0f;
		for (int i = highStart; i <= highEnd; ++i, lowLzPos += lowScale) {
			highAcc += lanczos.apply(lowLzPos) * circBuf[i & mask];
		}
		return circBuf[pos] - highAcc / sum;
	}
}