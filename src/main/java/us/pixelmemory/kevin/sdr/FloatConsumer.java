package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface FloatConsumer<T extends Throwable> {
	void accept(float f) throws T;
}