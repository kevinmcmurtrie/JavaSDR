package us.pixelmemory.kevin.sdr.firfilters;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;
import us.pixelmemory.kevin.sdr.IQSampleProcessor;

public final class SingleFilterIQ implements IQSampleProcessor<RuntimeException> {
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
	public void accept(final IQSample in, final IQSample out) throws RuntimeException {
		circBufI[pos] = in.in;
		circBufQ[pos] = in.quad;
		final int sampleIdx = (pos - sampleLatency) & (circBufI.length - 1);
		out.set(filter.apply(circBufI, sampleIdx), filter.apply(circBufQ, sampleIdx));
		pos = (pos + 1) & (circBufI.length - 1);
	}
	
	public <T extends Throwable> IQSampleConsumer<T> asIQSampleConsumer (IQSampleConsumer<T> out) {
		final IQSample outSample= new IQSample();
		return (inSample) -> {
			accept(inSample, outSample);
			out.accept(outSample);
		};
	}

}
