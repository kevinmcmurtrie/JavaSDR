package us.pixelmemory.kevin.sdr.resamplers;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;

public class DownsamplerIQ<T extends Throwable> implements IQSampleConsumer<T> {
	private final double destinationSampleRate;
	final Accumulator<T>[] accs;

	private static final class Accumulator<T extends Throwable> extends AccumulatorBase implements IQSampleConsumer<T> {
		private final IQSampleConsumer<T> out;
		private final IQSample acc;
		private float sum = 0f;

		public Accumulator(final LanczosTable lanczos, final IQSampleConsumer<T> out, final float inPosition, final float inToOut) {
			super(lanczos, inPosition, inToOut);
			this.out = out;
			acc = new IQSample(0, 0);
		}

		@Override
		public void accept(final IQSample iq) throws T {
			final float lz = lz();
			acc.in += lz * iq.in;
			acc.quad += lz * iq.quad;
			sum += lz;
			if (step()) {
				try {
					if (sum <= 0) {
						acc.set(0, 0);
					} else {
						acc.divide(sum);
					}
					out.accept(acc);
				} finally {
					acc.set(0, 0);
					sum = 0;
				}
			}
		}
	}

	public double getSampleRate() {
		return destinationSampleRate;
	}

	public DownsamplerIQ(final LanczosTable lanczos, final double sourceSampleRate, final double destinationSampleRate, final IQSampleConsumer<T> out) {
		this.destinationSampleRate = destinationSampleRate;

		int x = -2 * lanczos.A;
		final float ratio = (float) (sourceSampleRate / destinationSampleRate);
		accs = new Accumulator[2 * lanczos.A];
		for (int i = 0; i < 2 * lanczos.A; ++i) {
			accs[i] = new Accumulator<>(lanczos, out, x++, ratio);
		}
	}

	@Override
	public void accept(final IQSample iq) throws T {
		for (final var acc : accs) {
			acc.accept(iq);
		}
	}
}
