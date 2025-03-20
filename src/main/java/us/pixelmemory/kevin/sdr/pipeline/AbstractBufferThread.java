package us.pixelmemory.kevin.sdr.pipeline;

public abstract class AbstractBufferThread<BUF, ERR extends Throwable> extends Thread implements AutoCloseable {
	protected final Object sync = new Object();
	private final BUF buffer1;
	private final BUF buffer2;
	private boolean readOneWriteTwo;

	protected boolean run = true;
	protected Throwable consumerError;
	protected final int bufferSize;
	protected BUF dest;
	protected int writePos;
	protected int readPos;

	public AbstractBufferThread(final int bufferSize, final BUF buffer1, final BUF buffer2, final String name) {
		super(name);
		this.buffer1 = buffer1;
		this.buffer2 = buffer2;

		readPos = bufferSize;
		writePos = 0;
		this.bufferSize = bufferSize;
		readOneWriteTwo = false;
		dest = buffer1;
		start();
	}

	/**
	 * Prepare for writing to dest[pos++]
	 *
	 * @return True if running, false if quit
	 * @throws ERR
	 */
	protected boolean writePrepare() throws ERR {
		try {
			if (writePos == bufferSize) {
				synchronized (sync) {
					while (run && (readPos != bufferSize)) {
						sync.wait();
					}
					if (consumerError != null) {
						if (consumerError instanceof final RuntimeException rtErr) {
							throw rtErr;
						}
						if (consumerError instanceof final Error errErr) {
							throw errErr;
						}
						throw (ERR) consumerError;
					}

					readPos = 0;
					writePos = 0;
					readOneWriteTwo = !readOneWriteTwo;
					dest = readOneWriteTwo ? buffer2 : buffer1;
					sync.notifyAll();
				}
			}
		} catch (final InterruptedException e) {
			close();
			throw new RuntimeException(e);
		}

		return run;
	}

	/**
	 * while (readPos < bufferSize) {
	 * out.accept(buffer[readPos++]);
	 * }
	 *
	 * @param buffer
	 * @throws ERR
	 */
	protected abstract void send(BUF buffer) throws ERR;

	@Override
	public void run() {
		try {
			while (run) {
				BUF src;
				synchronized (sync) {
					sync.notifyAll();
					while (run && (readPos != 0)) {
						sync.wait();
					}
					src = readOneWriteTwo ? buffer1 : buffer2;
				}
				send(src);
			}
		} catch (final Throwable err) {
			consumerError = err;
		} finally {
			close();
		}
	}

	@Override
	public void close() {
		synchronized (sync) {
			run = false;
			sync.notifyAll();
		}
	}
}
