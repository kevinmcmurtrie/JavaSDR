package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

public class FrequencyLock implements PhaseTunerLock {
	private static final boolean enableDebug = false;
	private final boolean debug;
	private final IQVisualizer vis;

	// Previous - current is the frequency offset
	private final IQSample previous = new IQSample();
	private final double samplesPerCycle;

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private double frequencyAft = 0;
	private final Clock clock;
	private float phaseOffset;

	private final RCLowPassIQ errorLowPass;

	private final IQSample frequencyDetector = new IQSample();

	public FrequencyLock(final double sampleRate, final double frequency, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, frequency);
		samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		final double tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = frequencyLimit / tauCyclesPerSample;
		this.debug = debug;
		errorLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}

	public FrequencyLock(final double sampleRate, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, 0);
		samplesPerCycle = 1;
		final double tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = frequencyLimit / tauCyclesPerSample;
		this.debug = debug;
		errorLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.cyan);
		}
	}
	
	public static void main (String args[]) throws Exception {
		double sampleRate= 100000;
		double frequency = 100;
		
		FrequencyLock fl= new FrequencyLock(sampleRate, frequency, 1, 200, true);
		Clock c = new Clock(sampleRate, frequency +50);
		final IQSample src= new IQSample();
		final IQSample out= new IQSample();
		
		for (int i= 0; i < 10000000; ++i) {
			src.setMoment(c.getAndTick());
			fl.accept(src, out);
		}
		
	}

	@Override
	public double getClockRateAdjustment() {
		return frequencyAft;
	}

	@Override
	public void accept(final IQSample src, final IQSample out) {
		out.set(src);
		final double c= clock.getAndTick(frequencyAft);
		out.rotate(-c);
		
		previous.conjugate();
		previous.multiply(out);
		phaseOffset= (float)previous.phase();

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.
		errorLowPass.accept(previous, frequencyDetector);
		
		//The frequency mismatch is noisy and only useful when the phase mismatch is zero magnitude.
		final double frequencyMismatchPhase = frequencyDetector.phase();
		
		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.
		frequencyAft+= 0.00001d * frequencyMismatchPhase;
		
		frequencyDetector.rotate(-frequencyMismatchPhase*0.000002);	//Debounce frequency correction with forward feedback
		

		if (frequencyAft > aftLimit) {
			if (enableDebug) {
				System.out.println("AFT limit: " + frequencyAft);
			}
			frequencyAft = aftLimit;
		}
		if (frequencyAft < -aftLimit) {
			if (enableDebug) {
				System.out.println("AFT limit: " + frequencyAft);
			}
			frequencyAft = -aftLimit;
		}

		if (enableDebug && debug) {
			vis.markCenter();
			vis.drawIQ(Color.red, src);
			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.cyan, samplesPerCycle * frequencyAft);
			vis.drawAnalog(Color.orange, phaseOffset * samplesPerCycle);

			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.blue, out);

//			vis.repaint();// only interactive debugging
//			vis.fadeLight();
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
