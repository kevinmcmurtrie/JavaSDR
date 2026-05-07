package us.pixelmemory.kevin.sdr;

public final class SimplerMath {
	private SimplerMath() {
	}

	public static float clamp(final float value, final float min, final float max) {
		if ((value >= min) && (value <= max)) {
			return value;
		}
		return (value >= min) ? max : min;
	}
	
	public static int clamp(final int value, final int min, final int max) {
		if ((value >= min) && (value <= max)) {
			return value;
		}
		return (value >= min) ? max : min;
	}
	
	public static double clamp(final double value, final double min, final double max) {
		if ((value >= min) && (value <= max)) {
			return value;
		}
		return (value >= min) ? max : min;
	}
}
