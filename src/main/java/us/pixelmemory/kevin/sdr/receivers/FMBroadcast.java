package us.pixelmemory.kevin.sdr.receivers;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.FloatPairConsumer;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.SimplerMath;
import us.pixelmemory.kevin.sdr.firfilters.BandPass;
import us.pixelmemory.kevin.sdr.firfilters.FilterBuilder;
import us.pixelmemory.kevin.sdr.firfilters.IdentityPass;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.firfilters.LowPass;
import us.pixelmemory.kevin.sdr.firfilters.MultiFilter;
import us.pixelmemory.kevin.sdr.firfilters.SingleFilter;
import us.pixelmemory.kevin.sdr.firfilters.TimeShiftToQuadrature;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPass;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPassStereo;
import us.pixelmemory.kevin.sdr.tuners.PhaseLock;

public class FMBroadcast<T extends Throwable> implements FloatConsumer<T> {
	private static final boolean enableDebug = true;

	private static final float pilotFrequency = 19000f;

	private final float pilotStrengthRc = 1f;

	private final PhaseLock pilotTuner;
	private final IQSample pilotLockedIQ = new IQSample();
	private final IQSample pilotIQ = new IQSample();
	private final MultiFilter<T> sourceMultiFilter;
	private final SingleFilter audioLeftFilter;
	private final SingleFilter audioRightFilter;
	private final FloatConsumer<T> rdsOut;
	private final float initialGain;

	
	private static final float bandwidth= 16500f;
	private static final float extraStereoGain= 1.5f;
	
	// A higher Lanczos level on the pilot BandPass filter helps with weaker stations. If too high, will lock on a pilot even if the stereo band is noise.
	private static final FilterBuilder stereoPilotFilter = new BandPass(LanczosTable.of(6), pilotFrequency-500f, pilotFrequency+500f);
	private static final FilterBuilder monoBandFilter = new IdentityPass(); //Self-filtering in audio section
	private static final FilterBuilder stereoBandFilter = new IdentityPass();//Self-filtering in audio section
	private static final FilterBuilder rdsBandFilter = new IdentityPass();// RDS has its own filter
	
	private static final FilterBuilder audioFilter = new LowPass(LanczosTable.of(6), bandwidth);

	private final RCLowPass pilotStrengthFilter;
	private final RCLowPassStereo<T> deEmphasis;
	private final TimeShiftToQuadrature pilotQuad;
	private final IQVisualizer vis = enableDebug ? new IQVisualizer(2f) : null;

	public FMBroadcast(final float sampleRate, final FloatPairConsumer<T> stereoOut, final FloatConsumer<T> rdsOut) {
		deEmphasis = new RCLowPassStereo<>(sampleRate, 0.000075d, stereoOut);
		pilotTuner = new PhaseLock(sampleRate, pilotFrequency, 2, 100d, enableDebug);
		sourceMultiFilter = new MultiFilter<>(sampleRate, f -> bandpassIn(f[0], f[1], f[2], f[3]), stereoPilotFilter, monoBandFilter, stereoBandFilter, rdsBandFilter);
		audioLeftFilter= new SingleFilter(sampleRate,audioFilter);
		audioRightFilter= new SingleFilter(sampleRate,audioFilter);
		pilotStrengthFilter = new RCLowPass(sampleRate, pilotStrengthRc);
		pilotQuad= new TimeShiftToQuadrature(sampleRate, pilotFrequency);
		this.rdsOut = rdsOut;
		initialGain= sampleRate/360000;
		if (enableDebug) {
			vis.syncOnColor(Color.gray);
		}
	}

	private void bandpassIn(final float pilot, final float monoBand, final float stereoBand, final float rdsBand) throws T {
		pilotQuad.convert(16 * pilot, pilotIQ);
		pilotTuner.accept(pilotIQ, pilotLockedIQ);
		// The tuned pilot should be positive in the quadrature and zero in the in-phase.
		// Calculate the strength of the stereo and scale it gradually. It will naturally fade in and out on weak signals.
		final float pilotStrength = pilotStrengthFilter.apply((float) (5 * pilotLockedIQ.in - 2 * Math.abs(pilotLockedIQ.quad)));
		final float stereoStrength = SimplerMath.clamp(pilotStrength - 0.1f, 0, 1);
		final float doubleClock = (float) Math.sin(2 * pilotTuner.getClock());
		final float stereo= stereoStrength * extraStereoGain * stereoBand * doubleClock; // L-R

		if (rdsOut != null) {
			// It would be nice to use the 3*pilot to demodulate RDS, but stations violate the requirement that it be in sync.
			rdsOut.accept(rdsBand);
		}
		
		final float left= audioLeftFilter.apply(monoBand + stereo);
		final float right= audioRightFilter.apply(monoBand - stereo);

		if (enableDebug) {
			vis.drawAnalog(Color.gray, 0);
			vis.drawAnalog(Color.DARK_GRAY, 1);
			vis.drawAnalog(Color.green, stereoStrength);
			vis.drawAnalog(Color.pink, pilotStrength);
			vis.drawAnalog(Color.orange, 2 + monoBand);
			vis.drawAnalog(Color.cyan, 3 + stereo);
			vis.drawAnalog(Color.red, 4 + left);
			vis.drawAnalog(Color.blue, 4 + right);
		}

		deEmphasis.accept(left, right);
	}


	@Override
	public void accept(final float f) throws T {
		sourceMultiFilter.accept(f * initialGain);
	}
}
