package us.pixelmemory.kevin.sdr.receivers;

import java.awt.Color;

import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.IQSampleConsumer;
import us.pixelmemory.kevin.sdr.IQVisualizer;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.firfilters.LowPass;
import us.pixelmemory.kevin.sdr.firfilters.SingleFilterIQ;
import us.pixelmemory.kevin.sdr.iirfilters.RCHighPass;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPass;
import us.pixelmemory.kevin.sdr.pipeline.IQSampleBufferThread;
import us.pixelmemory.kevin.sdr.resamplers.Downsampler;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerIQ;
import us.pixelmemory.kevin.sdr.tuners.FrequencyShift;
import us.pixelmemory.kevin.sdr.tuners.PhaseLock;

public final class AM<T extends Throwable> implements IQSampleConsumer<T> {
	private static final boolean enableDebug = true;

	private static final float carrierAGCStrengthRc = 1f;
	private static final float minimumAGCSignal = 0.0001f; // Prevent AGC from going to infinity as signal goes to zero.

	private final IQVisualizer vis;

	private final IQSampleConsumer<T> inputPipeline;
	private final RCLowPass basicStrengthFilter;
	private final RCLowPass synchronousStrengthFilter;
	private final RCHighPass audioHighPassFilter;
	private final FloatConsumer<T> audioSink;
	private final PhaseLock synchronousTuner;
	private final IQSample synchronousTunedSample = new IQSample();

	public AM(float radioSampleRate, float radioTuningOffsetFrequency, float radioAFTRange, FloatConsumer<T> audioOutput, float audioSampleRate, float bandwidth, boolean debug) {
		if (debug && enableDebug) {
			vis = new IQVisualizer(0.2f);
			vis.syncOnColor(Color.green);
		} else {
			vis = null;
		}

		final float pipelineSampleRate = Math.max(audioSampleRate, bandwidth * 2);

		if (pipelineSampleRate > radioSampleRate) {
			throw new IllegalArgumentException("Radio radioSampleRate=" + radioSampleRate + " is too low for audioSampleRate=" + audioSampleRate + " or bandwidth=" + bandwidth);
		}

		IQSampleConsumer<T> sink = this::processSample;
		final boolean needBandwidthFilter = pipelineSampleRate > bandwidth * 2;

		if (needBandwidthFilter) {
			// Bandwidth filter before audio decoding
			sink = new SingleFilterIQ((float) pipelineSampleRate, new LowPass(LanczosTable.of(6), bandwidth)).asIQSampleConsumer(sink);
			if (debug && enableDebug) {
				System.out.println("Using IQ bandwidth filter");
			}
		}

		if (radioSampleRate > pipelineSampleRate) {
			// Downsample before bandwidth filter
			sink = new DownsamplerIQ<>(needBandwidthFilter ? LanczosTable.of(3) : LanczosTable.of(6), radioSampleRate, pipelineSampleRate,
					new IQSampleBufferThread<>(2048, "AM tuner radio downsample " + radioSampleRate + " to " + pipelineSampleRate, sink));
			if (debug && enableDebug) {
				System.out.println("Using IQ downsample");
			}
		}

		if (Math.abs(radioTuningOffsetFrequency) > 1) {
			// Frequency shift before downsample
			sink = new FrequencyShift(radioSampleRate, radioTuningOffsetFrequency).asConsumer(sink);
			if (debug && enableDebug) {
				System.out.println("Frequency shift");
			}
		}

		inputPipeline = sink;

		basicStrengthFilter = new RCLowPass(pipelineSampleRate, carrierAGCStrengthRc);
		basicStrengthFilter.setValue(1);
		synchronousStrengthFilter = new RCLowPass(pipelineSampleRate, carrierAGCStrengthRc);
		synchronousStrengthFilter.setValue(1);

		final boolean needAudioDownsample = audioSampleRate < pipelineSampleRate;

		audioHighPassFilter = new RCHighPass(pipelineSampleRate, carrierAGCStrengthRc); // DC block, just in case the signal is distorted
		audioSink = needAudioDownsample ? new Downsampler<>(LanczosTable.of(3), pipelineSampleRate, audioSampleRate, audioOutput) : audioOutput;
		synchronousTuner = new PhaseLock(pipelineSampleRate, 0.1f, radioAFTRange, 0, debug && enableDebug);

		if (debug && enableDebug) {
			System.out.println("radioSampleRate=" + radioSampleRate + " pipelineSampleRate=" + pipelineSampleRate);
			System.out.println("radioTuningOffsetFrequency=" + radioTuningOffsetFrequency);
			System.out.println("radioAFTRange=" + radioAFTRange);
			System.out.println("audioSampleRate=" + audioSampleRate);

			System.out.println("bandwidth=" + bandwidth);
			System.out.println("needDownsample=" + needAudioDownsample);
		}
	}

	private void processSample(IQSample iq) throws T {
		float basicSignal = iq.magnitude();
		float basicStrength = basicStrengthFilter.applyMinClamped(basicSignal, minimumAGCSignal);

		iq.multiply(1 / basicStrength); // AGC for synchronous tuner
		synchronousTuner.accept(iq, synchronousTunedSample);
		float tunedSignal =  synchronousTunedSample.in;

		float synchronousStrength = synchronousStrengthFilter.applyMinClamped(tunedSignal, minimumAGCSignal);
		float lockQuality = synchronousTuner.getLockQuality();
		float synchronousAudio = (tunedSignal - synchronousStrength) / synchronousStrength; // AGC and offset correction
		float basicAudio = (basicSignal - basicStrength) / basicStrength; // AGC and offset correction
		float blendedAudio = 0.5f * ((basicAudio * (1 - lockQuality) + synchronousAudio * lockQuality)); // Blend average and synchronous based on PLL quality. Reduce gain.

		float audio = audioHighPassFilter.apply(blendedAudio);

		if (vis != null) {
			// Reuse this IQSample during debugging
			synchronousTunedSample.in = synchronousAudio;
			synchronousTunedSample.quad = synchronousTunedSample.quad / synchronousStrength;

			vis.markCenter();
			vis.drawAnalog(Color.DARK_GRAY, 1);
			vis.drawAnalog(Color.LIGHT_GRAY, 0);
			vis.drawAnalog(Color.red, 1 * lockQuality);
			vis.drawAnalog(Color.green, audio);
			vis.drawAnalog(Color.pink, synchronousStrength);
			vis.drawIQ(Color.orange, synchronousTunedSample);
		}

		audioSink.accept(audio);
	}

	@Override
	public void accept(IQSample iq) throws T {
		inputPipeline.accept(iq);
	}

}
