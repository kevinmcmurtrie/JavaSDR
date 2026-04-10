package us.pixelmemory.kevin.sdr.firfilters;

import us.pixelmemory.kevin.sdr.FloatArrayConsumer;
import us.pixelmemory.kevin.sdr.FloatConsumer;

/**
 * Apply multiple filters at once and deliver results in matching phase. The delay is set by the
 * lowest frequency filter.
 * The cost of this operation is proportional to the sum of the number of samples each filter covers.
 * This means lower frequencies are more expensive.
 *
 * @param <T>
 */
public final class MultiFilter<T extends Throwable> implements FloatConsumer<T> {
	private final FloatArrayConsumer<T> out;
	private final float circBuf[];
	private final float extraCircBuf[][];
	private final float results[];
	private final FilterFIR filters[];
	private final float sampleRate;
	private final int sampleLatency;
	private int pos = 0;

	// public static void main(final String args[]) throws InterruptedException {
	//
	// final IQVisualizer vis = new IQVisualizer();
	// final FloatArrayConsumer<RuntimeException> drawer = data -> {
	// vis.drawAnalog(Color.green, data[0] + 2);
	// vis.drawAnalog(Color.blue, data[1] + 4);
	// vis.drawAnalog(Color.orange, data[2] + 6);
	// };
	// final MultiPass<RuntimeException> d = new MultiPass<>(700000, drawer, new BandPass(18700, 19300), new HighPass(19000), new IdentityPass());
	// for (float clockRate = d.getSampleRate() / 9500; clockRate >= d.getSampleRate() / 36000; clockRate -= 0.01) {
	//
	// System.out.println("Frequency: " + d.getSampleRate() / clockRate);
	//
	// for (int i = 0; i < 990; ++i) {
	// final float sample = (float) Math.sin(2d * Math.PI * i / clockRate);
	// vis.drawAnalog(Color.red, sample);
	// vis.drawAnalog(Color.DARK_GRAY, 0);
	// d.accept(sample);
	// }
	// vis.repaint();
	//
	// Thread.sleep(10);
	// vis.reset(Color.red);
	// vis.reset(Color.green);
	// vis.reset(Color.blue);
	// vis.reset(Color.orange);
	// vis.reset(Color.DARK_GRAY);
	// vis.clear();
	// }
	// }

	@SafeVarargs
	public MultiFilter(final float sampleRate, final FloatArrayConsumer<T> consumer, final FilterBuilder... filters) {
		this(sampleRate, consumer, 0, filters);
	}

	@SafeVarargs
	public MultiFilter(final float sampleRate, final FloatArrayConsumer<T> consumer, final int extraFields, final FilterBuilder... filters) {
		this.sampleRate = sampleRate;
		results = new float[extraFields + filters.length];
		out = consumer;
		this.filters = new FilterFIR[filters.length];
		int maxDistance = 1;
		for (int i = 0; i < filters.length; ++i) {
			this.filters[i] = filters[i].build(sampleRate);
			maxDistance = Math.max(maxDistance, this.filters[i].latency());
		}
		sampleLatency = maxDistance;
		circBuf = CircbufBuilder.createMaskableCircularBuffer(1 + 2 * maxDistance);
		if (extraFields > 0) {
			extraCircBuf = new float[circBuf.length][];
			for (int i = 0; i < extraCircBuf.length; ++i) {
				extraCircBuf[i] = new float[extraFields];
			}
		} else {
			extraCircBuf = null;
		}
	}

	public float getSampleRate() {
		return sampleRate;
	}

	@Override
	public void accept(final float f) throws T {
		circBuf[pos] = f;

		final int sampleIdx = (pos - sampleLatency) & (circBuf.length - 1);
		pos = (pos + 1) & (circBuf.length - 1);
		for (int filterIdx = 0; filterIdx < filters.length; ++filterIdx) {
			results[filterIdx] = filters[filterIdx].apply(circBuf, sampleIdx);
		}
		out.accept(results);
	}

	public void accept(final float f, final float... extraFields) throws T {
		System.arraycopy(extraFields, 0, extraCircBuf[pos], 0, extraFields.length);
		circBuf[pos] = f;

		final int sampleIdx = (pos - sampleLatency) & (circBuf.length - 1);
		pos = (pos + 1) & (circBuf.length - 1);
		for (int filterIdx = 0; filterIdx < filters.length; ++filterIdx) {
			results[filterIdx] = filters[filterIdx].apply(circBuf, sampleIdx);
		}

		final float[] extras = extraCircBuf[sampleIdx];
		if (extras != null) {
			System.arraycopy(extras, 0, results, filters.length, extras.length);
		}

		out.accept(results);
	}

}
