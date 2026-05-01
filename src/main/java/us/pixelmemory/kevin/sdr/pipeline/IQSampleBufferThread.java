package us.pixelmemory.kevin.sdr.pipeline;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;

/**
 * Push IQSamples from one thread to another with an asynchronous buffer.
 * Latency is as small as one sample at the cost of some CPU synchronization overhead. 
 * @param <T> The exception that may be thrown from the consumer
 */
public class IQSampleBufferThread<T extends Throwable> implements IQSampleConsumer<T> {
	private static final long timeOutNanos = TimeUnit.SECONDS.toNanos(1);
	private final IQSampleConsumer<T> out;
	public final String name;
	private final float[] bufferIQ;
	private final int length;
	private volatile int nextInsert;
	private volatile int lastConsumed;
	private volatile Throwable err = null;
	private final AtomicReference<Thread> consumerThread = new AtomicReference<>(null);
	private final AtomicReference<Thread> waitingSupplierThread = new AtomicReference<>(null);
	private final AtomicReference<Thread> waitingConsumerThread = new AtomicReference<>(null);

	public IQSampleBufferThread(final int bufferSize, final String name, final IQSampleConsumer<T> out) {
		this.name = name;
		this.out = out;
		bufferIQ = new float[2*bufferSize];
		length= bufferSize;
		nextInsert = 0;
		lastConsumed = bufferSize - 1;
	}

	private void run() {
		final Thread me = Thread.currentThread();
//		System.out.println("Consumer " + name + " started");
		try {
			final IQSample iq = new IQSample(0, 0);

			while ((err == null) && (me == consumerThread.get())) {
				int nextIdx;
				while ((nextIdx = nextIdx(lastConsumed)) != nextInsert) {
					iq.set(bufferIQ[2*nextIdx], bufferIQ[1+2*nextIdx]);
					lastConsumed = nextIdx;
					unblockWaitingProvider();
					out.accept(iq);
				}

				// System.out.println("Wait for supply");
				waitingConsumerThread.set(me);
				unblockWaitingProvider();
				LockSupport.parkNanos(timeOutNanos);

				if (nextIdx == nextInsert) {
//					System.out.println("Consumer " + name + " quit");
					return;
				}
				// System.out.println("Wait for supply - resume");
			}
		} catch (final Throwable error) {
			err = error;
		} finally {
			consumerThread.compareAndSet(me, null);
			waitingConsumerThread.compareAndSet(me, null);
			unblockWaitingProvider();
		}
	}

	private Thread checkThread() {
		Thread t;
		while ((t = consumerThread.get()) == null) {

			final Thread newt = new Thread(this::run, name);
			if (consumerThread.compareAndSet(null, newt)) {
				newt.start();
				t = newt;
				return t;
			}
		}
		return t;
	}

	@SuppressWarnings("unchecked")
	private void checkError() throws T {
		if (err != null) {
			if (err instanceof final RuntimeException rt) {
				throw rt;
			}
			if (err instanceof final Error fatal) {
				throw fatal;
			}
			throw (T) err;
		}
	}

	private int nextIdx(final int idx) {
		return (idx + 1) % length;
	}

	private void unblockWaitingProvider() {
		final Thread waitingThread = waitingSupplierThread.getAndSet(null);
		if (waitingThread != null) {
			// System.out.println("Notify consumption");
			LockSupport.unpark(waitingThread);
		}
	}

	private void unblockWaitingConsumer() {
		final Thread waitingThread = waitingConsumerThread.getAndSet(null);
		if (waitingThread != null) {
			// System.out.println("Notify supply");
			LockSupport.unpark(waitingThread);
		}
	}

	@Override
	public void accept(final IQSample iq) throws T {
		Thread cThread = checkThread();
		checkError();

		while (nextInsert == lastConsumed) {
			cThread = checkThread();
			final Thread me = Thread.currentThread();

			// System.out.println("Wait for consumption");
			if (!waitingSupplierThread.compareAndSet(null, me)) {
				throw new IllegalStateException("Multiple concurrent writer threads");
			}
			unblockWaitingConsumer();
			LockSupport.parkNanos(cThread, timeOutNanos);
			waitingSupplierThread.compareAndSet(me, null);
			// System.out.println("Wait for consumption - resume");

			checkError();
		}

		final int nextIdx = nextIdx(nextInsert);
		bufferIQ[2*nextIdx]= iq.in;
		bufferIQ[1+2*nextIdx]= iq.quad;
		nextInsert = nextIdx;

		unblockWaitingConsumer();
	}
}
