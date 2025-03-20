package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface BooleanFunction<T extends Throwable> {
	boolean apply(boolean b) throws T;
}