package us.pixelmemory.kevin.sdr.pipeline;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;

public class IQSampleBufferThread<T extends Throwable> implements IQSampleConsumer<T> {
	private static final long timeOutNanos = TimeUnit.SECONDS.toNanos(1);
	private final IQSampleConsumer<T> out;
	public final String name;
	private final IQSample[] buffer;
	private volatile int nextInsert;
	private volatile int lastConsumed;
	private volatile Throwable err = null;
	private final AtomicReference<Thread> consumerThread = new AtomicReference<>(null);
	private final AtomicReference<Thread> waitingSupplierThread = new AtomicReference<>(null);
	private final AtomicReference<Thread> waitingConsumerThread = new AtomicReference<>(null);

	public IQSampleBufferThread(final int bufferSize, final String name, final IQSampleConsumer<T> out) {
		this.name = name;
		this.out = out;
		buffer = new IQSample[bufferSize];
		for (int i = 0; i < bufferSize; ++i) {
			buffer[i] = new IQSample(0, 0);
		}
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
					iq.set(buffer[nextIdx]);
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
		return (idx + 1) % buffer.length;
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
			LockSupport.park(cThread);
			waitingSupplierThread.compareAndSet(me, null);
			// System.out.println("Wait for consumption - resume");

			checkError();
		}

		final int nextIdx = nextIdx(nextInsert);
		buffer[nextIdx].set(iq);
		nextInsert = nextIdx;

		unblockWaitingConsumer();
	}
}
