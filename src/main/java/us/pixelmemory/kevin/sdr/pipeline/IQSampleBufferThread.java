package us.pixelmemory.kevin.sdr.pipeline;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;

public class IQSampleBufferThread<T extends Throwable> extends AbstractBufferThread<IQSample[], T> implements IQSampleConsumer<T> {
	private final IQSampleConsumer<T> out;

	public IQSampleBufferThread(final int bufferSize, final String name, final IQSampleConsumer<T> out) {
		super(bufferSize, createBuffer(bufferSize), createBuffer(bufferSize), name);
		this.out = out;
	}

	private static IQSample[] createBuffer(final int size) {
		final IQSample buf[] = new IQSample[size];
		for (int i = 0; i < size; ++i) {
			buf[i] = new IQSample();
		}
		return buf;
	}

	@Override
	public void accept(final IQSample iq) throws T {
		if (writePrepare()) {
			dest[writePos++].set(iq);
		} else {
			throw new RuntimeException("closed");
		}
	}

	public IQSample getWriteSampleReference() throws T {
		if (writePrepare()) {
			return dest[writePos++];
		}
		throw new RuntimeException("closed");
	}

	@Override
	protected void send(final IQSample[] buffer) throws T {
		while (readPos < bufferSize) {
			out.accept(buffer[readPos++]);
		}
	}
}
