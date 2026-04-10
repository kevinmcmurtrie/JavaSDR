package us.pixelmemory.kevin.sdr.sampleformat;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.FloatPairConsumer;

public final class MonoToStereo<T extends Throwable> implements FloatConsumer<T> {
	private final FloatPairConsumer<T> out;

	public MonoToStereo(FloatPairConsumer<T> out) {
		this.out = out;
	}

	@Override
	public void accept(float f) throws T {
		out.accept(f, f);
	}
}
