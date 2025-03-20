package us.pixelmemory.kevin.sdr;

@FunctionalInterface
public interface IQSampleConsumer<T extends Throwable> {
	void accept(IQSample iq) throws T;
}