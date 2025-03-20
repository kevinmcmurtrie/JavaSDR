package us.pixelmemory.kevin.sdr.firfilters;

import us.pixelmemory.kevin.sdr.FloatFunction;

public final class SingleFilter implements FloatFunction<RuntimeException> {
	private final float circBuf[];
	private final FilterFIR filter;
	private final int sampleLatency;
	private int pos = 0;

	public SingleFilter(final float sampleRate, final FilterBuilder filter) {
		this.filter = filter.build(sampleRate);
		sampleLatency = this.filter.latency();
		circBuf = CircbufBuilder.createMaskableCircularBuffer(1 + 2 * sampleLatency);
	}

	@Override
	public float apply(final float f) {
		circBuf[pos] = f;
		final int sampleIdx = (pos - sampleLatency) & (circBuf.length - 1);
		pos = (pos + 1) & (circBuf.length - 1);
		return filter.apply(circBuf, sampleIdx);
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
