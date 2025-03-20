package us.pixelmemory.kevin.sdr.firfilters;

import java.util.concurrent.atomic.AtomicReferenceArray;

import us.pixelmemory.kevin.sdr.FloatFunction;

public final class LanczosTable implements FloatFunction<RuntimeException> {
	public static LanczosTable of(final int a) {
		if ((a < 0) || (a > instancesPerA.length())) {
			throw new IllegalArgumentException("a must be 1.." + (instancesPerA.length() + 1));
		}
		LanczosTable l;
		// Only allow a singleton to be in use because it eats cache memory
		while ((l = instancesPerA.get(a - 1)) == null) {
			instancesPerA.compareAndSet(a - 1, null, new LanczosTable(a));
		}
		return l;
	}

	public final int A;

	@Override
	public float apply(final float distance) {
		int d = Math.round(distance / xScale);
		if (d < 0) {
			d = -d;
		}
		if (d >= tableSize) {
			return 0f;
		}
		return table[d];
	}

	private static final AtomicReferenceArray<LanczosTable> instancesPerA = new AtomicReferenceArray<>(64);
	private final int tableSize;
	private final float table[]; // Half of the symmetrical table
	private final float xScale;

	private LanczosTable(final int a) {
		this.A = a;
		// TODO test and tune
		tableSize = switch (a) {
			case 1 -> 128;
			case 2 -> 512;
			case 3 -> 1024;
			case 4 -> 1152;
			case 5 -> 1280;
			case 6 -> 2048;
			case 7 -> 2048;
			case 8 -> 3072;
			case 9 -> 3072;
			case 10 -> 3072;
			case 20 -> 6144;
			default -> 8192;
		};

		table = new float[tableSize];
		table[0] = 1;
		xScale = (float) A / tableSize;
		for (int idx = 1; idx < tableSize; ++idx) {
			final double d = idx * xScale;
			final double piD = Math.PI * d;
			table[idx] += (float) ((A * Math.sin(piD) * Math.sin(piD / A)) / Math.pow(piD, 2));
		}
	}

	// public static void main (String args[]) throws InterruptedException {
	// IQVisualizer vis= new IQVisualizer();
	// LanczosTable lz= LanczosTable.of(64);
	// for (float i= -100; i < 100; i+= 0.2) {
	// vis.drawAnalog(Color.red, 6*lz.apply(i));
	// }
	// vis.repaint();
	// }
}
