package us.pixelmemory.kevin.sdr.firfilters;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;

public final class SingleFilterIQ implements IQSampleProcessor<RuntimeException, Void> {
	private final float circBufI[];
	private final float circBufQ[];
	private final FilterFIR filter;
	private final int sampleLatency;
	private int pos = 0;

	public SingleFilterIQ(final float sampleRate, final FilterBuilder filter) {
		this.filter = filter.build(sampleRate);
		sampleLatency = this.filter.latency();
		circBufI = CircbufBuilder.createMaskableCircularBuffer(1 + 2 * sampleLatency);
		circBufQ = CircbufBuilder.createMaskableCircularBuffer(1 + 2 * sampleLatency);
	}

	@Override
	public Void accept(final IQSample in, final IQSample out) throws RuntimeException {
		circBufI[pos] = (float) in.in;
		circBufQ[pos] = (float) in.quad;
		final int sampleIdx = (pos - sampleLatency) & (circBufI.length - 1);
		out.set(filter.apply(circBufI, sampleIdx), filter.apply(circBufQ, sampleIdx));
		pos = (pos + 1) & (circBufI.length - 1);
		return null;
	}

}
