package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface FloatPairConsumer<T extends Throwable> {
	void accept(float a, float b) throws T;
}
