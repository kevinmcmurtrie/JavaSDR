package us.pixelmemory.kevin.sdr.firfilters;

public record LowPassFIR(LanczosTable lanczos, float highScale, int latency, float sum) implements FilterFIR {
	@Override
	public float apply(final float[] circBuf, final int pos) {
		final int mask = circBuf.length - 1;
		final int highStart = pos - latency;
		final int highEnd = pos + latency;
		float highLzPos = -latency * highScale;
		float highAcc = 0f;
		for (int i = highStart; i <= highEnd; ++i, highLzPos += highScale) {
			highAcc += lanczos.apply(highLzPos) * circBuf[i & mask];
		}
		return highAcc / sum;
	}
}