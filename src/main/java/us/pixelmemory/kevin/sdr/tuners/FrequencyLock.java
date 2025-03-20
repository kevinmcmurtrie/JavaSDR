package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;

public final class FrequencyLock implements AnalogTunerLock {
	private static final boolean enableDebug = true;
	private final boolean debug;
	private final IQVisualizer vis;

	private final IQSample previous = new IQSample();
	private final double frequencyAlignmentSpeed;

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private double frequencyAft = 0;
	private float dcAccumulator = 0;
	private float frequencyMismatchPhase;

	public FrequencyLock(final double aftFractionalSpeed, final double aftPercentLimit, final boolean debug) {
		this.frequencyAlignmentSpeed = aftFractionalSpeed;
		this.aftLimit = aftPercentLimit;
		this.debug = debug;
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.CYAN);
		}
	}

	@Override
	public double getAFTLimit() {
		return aftLimit;
	}

	@Override
	public double getClockRateAdjustment() {
		return frequencyAft;
	}

	@Override
	public float getValue() {
		return frequencyMismatchPhase;
	}

	@Override
	public void accept(final IQSample src, final IQSample out, final double clock) {
		previous.conjugate();
		previous.multiply(out);
		previous.rotateRight();
		frequencyMismatchPhase = (float) previous.phase();
		dcAccumulator = dcAccumulator * .99f + 0.01f * frequencyMismatchPhase;

		if (enableDebug && debug) {
			vis.markCenter();
			vis.drawIQ(Color.red, src);
			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.CYAN, 100 * frequencyAft);
			vis.drawIQ(Color.magenta, previous);
			vis.drawIQ(Color.blue, out);
		}
		previous.set(out);

		final float clampedFrequencyMismatchPhase = SimplerMath.clamp(frequencyMismatchPhase, -1f, 1f) + SimplerMath.clamp(dcAccumulator, -0.1f, 0.1f);

		frequencyAft += clampedFrequencyMismatchPhase * frequencyAlignmentSpeed;

		if (frequencyAft > aftLimit) {
			System.out.println("AFT limit: " + frequencyAft);
			frequencyAft = aftLimit;
		}
		if (frequencyAft < -aftLimit) {
			System.out.println("AFT limit: " + frequencyAft);
			frequencyAft = -aftLimit;
		}
	}
}
