package us.pixelmemory.kevin.sdr.firfilters;

import java.util.concurrent.atomic.AtomicReferenceArray;

import us.pixelmemory.kevin.sdr.FloatFunction;

public final class LanczosTable implements FloatFunction<RuntimeException> {
	public static LanczosTable of(final int a) {
		if ((a < 0) || (a > instancesPerA.length())) {
			throw new IllegalArgumentException("a must be 1.." + (instancesPerA.length()));
		}
		LanczosTable l;
		// Only allow a singleton to be in use because the table eats CPU cache memory
		while ((l = instancesPerA.get(a - 1)) == null) {
			instancesPerA.compareAndSet(a - 1, null, new LanczosTable(a));
		}
		return l;
	}

	public final int A;

	@Override
	public float apply(final float distance) {
		//Use linear interpolation to reduce the table size
		final float scaledDistance = (distance < 0) ? -distance / xScale : distance / xScale;
		final int base = (int) scaledDistance;
		if (base >= tableSize) {
			return 0f;
		}

		final float interpolation = scaledDistance - base;
		return (1 - interpolation) * table[base] + interpolation * table[base + 1]; // Table is oversized by 1 to accommodate
	}

	private static final AtomicReferenceArray<LanczosTable> instancesPerA = new AtomicReferenceArray<>(64);
	private final int tableSize;
	private final float table[]; // Half of the symmetrical table
	private final float xScale;

	private LanczosTable(final int a) {
		this.A = a;
		tableSize = switch (a) {
			case 1 -> 30;
			case 2 -> 80;
			case 3 -> 60;
			case 4 -> 100;
			case 5 -> 200;
			case 6 -> 250;
			case 7 -> 260;
			case 8 -> 300;
			case 10 -> 350;
			case 20 -> 600;
			default -> 2000;
		};

		final int overSizedTable = tableSize + 1; // For linear interpolation
		table = new float[overSizedTable];
		table[0] = 1;
		xScale = (float) A / tableSize;
		final double piScale = xScale * Math.PI;
		for (int idx = 1; idx < overSizedTable; ++idx) {
			final double piD = piScale * idx;
			table[idx] += (float) ((A * Math.sin(piD) * Math.sin(piD / A)) / Math.pow(piD, 2));
		}
	}

//	 public static void main(String args[]) throws InterruptedException {
//	 IQVisualizer vis = new IQVisualizer();
//	 int a= 10;
//	 LanczosTable lz = LanczosTable.of(a);
//	 float zoom = 8f;
//	 for (float i = -a/zoom; i < a/zoom; i += a/(zoom * 500f)) {
//	 vis.drawAnalog(Color.red, 6 * lz.apply(i));
//	 }
//	 vis.repaint();
//	 }
}
