package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface FloatArrayConsumer<T extends Throwable> {
	/**
	 * @param f Reference to an array of floats that belongs to the sender. The values in this array are only valid during the callback.
	 * @throws T
	 */
	void accept(float f[]) throws T;
}