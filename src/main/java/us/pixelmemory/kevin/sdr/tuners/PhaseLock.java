package us.pixelmemory.kevin.sdr.tuners;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassIQ;

public class PhaseLock implements PhaseTunerLock {
	private static final boolean enableDebug = true;
	private final boolean debug;
	private final IQVisualizer vis;

	// Previous - current is the frequency offset
	private final IQSample previous = new IQSample();

	// Need two AFT rates to dampen cycling (the parallel C and RC in every PLL)
	private final double aftLimit;
	private final double tauCyclesPerSample;

	private double frequencyAft = 0;
	private double phaseAft = 0;
	private float lockQuality= 0;
	private final Clock clock;

	private final RCLowPassIQ errorFastLowPass;
	private final RCLowPassIQ errorSlowLowPass;

	private final IQSample phaseDetectorSlow = new IQSample(0,0);//Normal running phase detector.  Let accumulate.
	private final IQSample phaseDetectorFast = new IQSample(1, 0);	//Used when the slow one doesn't catch. Start off strong.
	private final IQSample frequencyDetector = new IQSample(0.5, 0);		//Used when no phase detector catches.  Start off hopeful of an easy lock.

	public PhaseLock(final double sampleRate, final double frequency, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, frequency);
		double samplesPerCycle = sampleRate / frequency;
		if (samplesPerCycle < 2) {
			throw new IllegalArgumentException("sampleRate is too low");
		}
		tauCyclesPerSample = Math.TAU / samplesPerCycle;
		this.aftLimit = frequencyLimit / tauCyclesPerSample;
		this.debug = debug;
		errorSlowLowPass = new RCLowPassIQ(sampleRate, 2 / aftFractionalSpeed);
		errorFastLowPass = new RCLowPassIQ(sampleRate, 0.5 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}

	public PhaseLock(final double sampleRate, final double aftFractionalSpeed, final double frequencyLimit, final boolean debug) {
		clock= new Clock(sampleRate, 0);
				
		tauCyclesPerSample = Math.TAU / sampleRate;
		this.aftLimit = frequencyLimit * tauCyclesPerSample;
		this.debug = debug;
		errorSlowLowPass = new RCLowPassIQ(sampleRate, 1 / aftFractionalSpeed);
		errorFastLowPass = new RCLowPassIQ(sampleRate, 0.1 / aftFractionalSpeed);
		vis = (enableDebug && debug) ? new IQVisualizer() : null;
		if (enableDebug && debug) {
			vis.syncOnColor(Color.orange);
		}
	}
	
	public static void main (String args[]) throws Exception {
		double sampleRate= 200000;
		double frequency = 19000;
		
		PhaseLock fl= new PhaseLock(sampleRate, frequency, 60, 100, true);
		Clock c = new Clock(sampleRate, frequency+50);
		final IQSample src= new IQSample();
		final IQSample out= new IQSample();
		
		for (int i= 0; i < 10000000; ++i) {
			src.setMoment(c.getAndTick());
			fl.accept(src, out);
			if (i % 10 == 0) {
				fl.vis.repaint();
				Thread.sleep(1);
				fl.vis.fadeLight();
			}
		}
		
	}

	@Override
	public double getClockRateAdjustment() {
		return frequencyAft/tauCyclesPerSample;
	}

	@Override
	public void accept(final IQSample src, final IQSample out) {
		out.set(src);
		final double c= clock.getAndTick(frequencyAft + phaseAft);
		out.rotate(-c);
		
		previous.conjugate();
		previous.multiply(out);

		// Average in two dimensions using an IQ sample. This tolerates high levels of noise and moments of negative
		// amplitude. If phase detection comes before averaging, noise dominates so much that it doesn't average out.
		errorSlowLowPass.accept(out, phaseDetectorSlow);
		errorFastLowPass.accept(out, phaseDetectorFast);
		errorSlowLowPass.accept(previous, frequencyDetector);
		
		lockQuality= SimplerMath.clamp(4f * (float)phaseDetectorSlow.in - 10f * Math.abs((float)phaseDetectorSlow.quad), 0f, 1f);
		
		final double slowLockStrength= SimplerMath.clamp(2*phaseDetectorSlow.magnitude(), 0, 1);
		final double fastLockStrength= SimplerMath.clamp(2*phaseDetectorFast.magnitude(), 0.2, 1);
		
		final double phaseError = (slowLockStrength * phaseDetectorSlow.phase()) + (1-slowLockStrength)*(fastLockStrength*phaseDetectorFast.phase());
		final double frequencyError = (1-slowLockStrength)*(1-fastLockStrength)*frequencyDetector.phase();

		
		// Fast adjustments to the phase. This adjustment is consumed to prevent bouncing.
		frequencyAft+= (0.000001 * frequencyError + 0.0001d * phaseError);
		phaseAft= phaseError;
		
		//Debounce
		phaseDetectorFast.rotate(-phaseAft*0.008);
		phaseDetectorSlow.rotate(-phaseAft*0.0004);
		frequencyDetector.rotate(-frequencyError*0.00001);
		phaseDetectorFast.rotate(0.05 * frequencyError);
		
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
			vis.drawIQ(Color.blue, out);

			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.cyan, 10*frequencyError/tauCyclesPerSample);
			vis.drawAnalog(Color.orange, 10*phaseAft/tauCyclesPerSample);
			vis.drawAnalog(Color.pink, 1*frequencyAft/tauCyclesPerSample);
			
			vis.drawIQ(Color.magenta, frequencyDetector);
			vis.drawIQ(Color.green, phaseDetectorSlow);
			vis.drawIQ(Color.yellow, phaseDetectorFast);
		}
		previous.set(out);
	}

	@Override
	public double getClock() {
		return clock.getClock();
	}

	@Override
	public float getPhase() {
		return (float) previous.phase();
	}
	
	public float getLockQuality () {
		return lockQuality;
	}

}
