package us.pixelmemory.kevin.sdr.firfilters;

import us.pixelmemory.kevin.sdr.FloatPairFunction;

public final class SingleAlphaFilter implements FloatPairFunction<RuntimeException> {
	private final float circBufSignal[];
	private final float circBufAlpha[];
	private final FilterAlphaFIR filter;
	private final int sampleLatency;
	private int pos = 0;

	public SingleAlphaFilter(final float sampleRate, final FilterAlphaBuilder filter) {
		this.filter = filter.build(sampleRate);
		sampleLatency = this.filter.latency();
		circBufSignal = CircbufBuilder.createMaskableCircularBuffer(1 + 2 * sampleLatency);
		circBufAlpha = CircbufBuilder.createMaskableCircularBuffer(1 + 2 * sampleLatency);
	}

	@Override
	public float apply(final float f, final float alpha) {
		circBufSignal[pos] = f;
		circBufAlpha[pos] = alpha;
		final int sampleIdx = (pos - sampleLatency) & (circBufSignal.length - 1);
		pos = (pos + 1) & (circBufSignal.length - 1);
		return filter.apply(circBufSignal, circBufAlpha, sampleIdx);
	}

	// public static void main (String args[]) {
	// IQVisualizer vis= new IQVisualizer();
	// SingleFilter f= new SingleFilter(100000f, new BandPass(LanczosTable.of(16), 1500, 1600));
	//
	// for (int i= 0; i < 1000; ++i) {
	// float value= ((i & 0b1000000) == 0) ? -2f : 2f;
	// vis.drawAnalog(Color.green, 6 + value);
	// vis.drawAnalog(Color.red, f.apply(value));
	// }
	// vis.repaint();
	//
	// }
}
