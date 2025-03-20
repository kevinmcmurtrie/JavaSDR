package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface IntConsumer<T extends Throwable> {
	void accept(int f) throws T;
}