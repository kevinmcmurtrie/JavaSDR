package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPass;

public class FrequencyLock implements PhaseTunerLock {
	private static final boolean enableDebug = false;
	private static final boolean liveDebug = true;

	private final boolean debug;
	private final IQVisualizer vis;

	// Previous - current is the frequency offset
	private final IQSample previous = new IQSample();
	private final float samplesPerCycle;

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final float aftLimit;
	private final Clock clock;
	private float phaseOffset;

	private final RCLowPass frequencyAft;
	
	private double avgNegDebug= 0d;
	private double avgPosDebug= 0d;

	public FrequencyLock(final float sampleRate, final float frequency, final float aftFractionalSpeed, final float frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, frequency);
		samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		aftLimit = (float) (frequencyLimit * Math.TAU / sampleRate);
		this.debug = debug;
		frequencyAft = new RCLowPass(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && !liveDebug && debug)  {
			vis.syncOnColor(Color.orange);
		}
	}

	public FrequencyLock(final float sampleRate, final float aftFractionalSpeed, final float frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, 0);
		samplesPerCycle = 1;
		aftLimit = (float) (frequencyLimit * Math.TAU / sampleRate);
		this.debug = debug;
		frequencyAft = new RCLowPass(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && !liveDebug && debug)  {
			vis.syncOnColor(Color.cyan);
		}
	}
	
	public static void main(String args[]) throws Exception {
		float sampleRate = 200000;
		float frequency = 10000;

		FrequencyLock fl = new FrequencyLock(sampleRate, frequency, 1f, 10000f, true);
		fl.vis.syncOnColor(new Color(1, 2, 3));
		Clock c = new Clock(sampleRate, frequency);
		final IQSample src = new IQSample();
		final IQSample out = new IQSample();

		boolean speedToggle= false;
		for (int i = 0; i < 10000000; ++i) {
			src.setMoment((float) c.tickAndGet(speedToggle ? 0.003d : -0.003d));
			fl.accept(src, out);
		
			if (i % 2000 == 0) {
				speedToggle= !speedToggle;
			}
		}
	}

	@Override
	public float getClockRateAdjustment() {
		return (float)(samplesPerCycle * frequencyAft.getLastValue() / Math.TAU);
	}

	@Override
	public void accept(final IQSample src, final IQSample out) {
		out.set(src);
		out.rotate((float)(-clock.tickAndGet(frequencyAft.getLastValue())));
		
		previous.conjugate();
		previous.multiply(out);
		phaseOffset= previous.phase();

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.

		float aft= frequencyAft.integrate(phaseOffset/ samplesPerCycle);
		
		
		if (phaseOffset < 0) {
			avgNegDebug += (phaseOffset - avgNegDebug) / 1000d;
		} else if (phaseOffset > 0) {
			avgPosDebug += (phaseOffset - avgPosDebug) / 1000d;
		}
//		System.out.println (avgNegDebug + " \t" + avgPosDebug + " \t" + frequencyDetector);

		if (aft > aftLimit) {
			if (enableDebug) {
				System.out.println("AFT limit: " + aft);
			}
			frequencyAft.setValue(aftLimit);
		}
		if (aft < -aftLimit) {
			if (enableDebug) {
				System.out.println("AFT limit: " + aft);
			}
			frequencyAft.setValue(-aftLimit);
		}

		if (enableDebug && debug) {
			if (liveDebug) {
				vis.fadeLight();
			}
			vis.markCenter();
			vis.drawIQ(Color.red, src);
			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.cyan, (float)(100*samplesPerCycle * aft));
			vis.drawAnalog(Color.orange, phaseOffset * samplesPerCycle);
			vis.drawIQ(Color.blue, out);

			if (liveDebug) {
				vis.repaint();
			}
		}
		previous.set(out);
	}

	@Override
	public double getClock() {
		return clock.getClock();
	}

	@Override
	public float getPhase() {
		return phaseOffset;
	}
}
