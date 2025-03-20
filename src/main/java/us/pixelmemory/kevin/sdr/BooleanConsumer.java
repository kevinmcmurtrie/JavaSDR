package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface BooleanConsumer<T extends Throwable> {
	void accept(boolean b) throws T;
}