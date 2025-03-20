package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface ByteArrayConsumer<T extends Throwable> {
	void accept(byte buffer[], int offset, int length) throws T;
}
