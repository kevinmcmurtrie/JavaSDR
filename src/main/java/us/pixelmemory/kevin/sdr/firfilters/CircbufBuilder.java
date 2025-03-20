package us.pixelmemory.kevin.sdr.firfilters;

public class CircbufBuilder {
	private CircbufBuilder() {
	}

	public static float[] createMaskableCircularBuffer(final int minimumSize) {
		int actualSize = 2;
		while (actualSize < minimumSize) {
			actualSize <<= 2;
		}
		return new float[actualSize];
	}
}
