package us.pixelmemory.kevin.sdr.pipeline;

import us.pixelmemory.kevin.sdr.FloatConsumer;

public class FloatBufferThread<T extends Throwable> extends AbstractBufferThread<float[], T> implements FloatConsumer<T> {
	private final FloatConsumer<T> out;

	public FloatBufferThread(final int bufferSize, final String name, final FloatConsumer<T> out) {
		super(bufferSize, new float[bufferSize], new float[bufferSize], name);
		this.out = out;
	}

	@Override
	public void accept(final float f) throws T {
		if (writePrepare()) {
			dest[writePos++] = f;
		} else {
			throw new RuntimeException("closed");
		}
	}

	@Override
	protected void send(final float[] buffer) throws T {
		while (readPos < bufferSize) {
			out.accept(buffer[readPos++]);
		}
	}
}
