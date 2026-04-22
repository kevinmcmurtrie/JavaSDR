package us.pixelmemory.kevin.sdr;

import java.awt.Color;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
//import java.io.InputStream;

//import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
//import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import us.pixelmemory.kevin.sdr.firfilters.BandPass;
import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.firfilters.LowPass;
import us.pixelmemory.kevin.sdr.firfilters.LowPassAlpha;
import us.pixelmemory.kevin.sdr.firfilters.SingleAlphaFilter;
import us.pixelmemory.kevin.sdr.firfilters.SingleFilter;
import us.pixelmemory.kevin.sdr.iirfilters.RCLowPass;
import us.pixelmemory.kevin.sdr.pipeline.IQSampleBufferThread;
import us.pixelmemory.kevin.sdr.rds.RDSDecoder;
import us.pixelmemory.kevin.sdr.receivers.FMBroadcast;
import us.pixelmemory.kevin.sdr.resamplers.Downsampler;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerIQ;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerMasked;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerStereo;
import us.pixelmemory.kevin.sdr.sampleformat.MonoToStereo;
import us.pixelmemory.kevin.sdr.sampleformat.SampleConverters;
import us.pixelmemory.kevin.sdr.sampleformat.SampleConverters.SampleReader;
import us.pixelmemory.kevin.sdr.tuners.PhaseLock;

public class FoobarMainAM {
	private static final boolean enableDebug = false;

	public static void main(String[] args) throws InterruptedException, UnsupportedAudioFileException, IOException, LineUnavailableException {
		final float IQGain = 30f;
		final IQVisualizer vis;
		if (enableDebug) {
			vis= new IQVisualizer(0.2f);
			vis.syncOnColor(Color.green);
		} else {
			vis= null;
		}
		
		final String am= "/home/mcmurtri/SDR/SDRconnect_IQ_20250322_173407_741000HZ.wav";	//Off by 1kHz
		
		final float audioSampleRate= 16000f;
		final float targetSampleRate= 100000;
		final float carrierStrengthRc =0.5f;		
		final String theFile= am;
		

		AudioFormat outputFormat= new AudioFormat(audioSampleRate, 16, 2, true, false); 
		
		try (SampleReader<IOException> sr = SampleConverters.createPcmSigned16BitLeReader(AudioSystem.getAudioInputStream(new File(theFile)), IQGain);
				SourceDataLine line= AudioSystem.getSourceDataLine(outputFormat)) {
			
			line.open();
			line.start();
			
			final ByteArrayConsumer<RuntimeException> out= line::write;

			final float rawSampleRate= sr.getSampleRateHz();
			boolean intermediateResample = rawSampleRate > targetSampleRate;
			final float sampleRate = intermediateResample ? targetSampleRate : rawSampleRate;

			Downsampler<RuntimeException> audioSampler= new Downsampler<>(LanczosTable.of(3), sampleRate, audioSampleRate, new MonoToStereo<>(SampleConverters.createPcmSignedStereo16BitLe(out)));

			RCLowPass carrierStrengthFilter = new RCLowPass(sampleRate, carrierStrengthRc);
			carrierStrengthFilter.setValue(1);
			RCLowPass basicStrengthFilter = new RCLowPass(sampleRate, carrierStrengthRc);
			basicStrengthFilter.setValue(1);
			
			PhaseLock aft= new PhaseLock(sampleRate, 0.1, 1200d, enableDebug);
			IQSample tuned = new IQSample();
			SingleFilter audioPass= new SingleFilter(sampleRate, new LowPass(LanczosTable.of(10), 4300));
			IQSample t2 = new IQSample();
			
			final IQSampleConsumer<RuntimeException> tuner = iq -> {
				float basicSignal= (float)iq.magnitude();
				float basicStrength= basicStrengthFilter.apply(basicSignal);
				
				iq.multiply(1/basicStrength);
				aft.accept(iq, tuned);
				float tunedSignal= (float)tuned.in;
				
				
				
				float tunedStrength= carrierStrengthFilter.apply(tunedSignal);
				float lockStrength= aft.getLockQuality();
		
				float pllAudio= (tunedSignal - tunedStrength)/tunedStrength;
				float basicAudio= (basicSignal - basicStrength)/basicStrength;
				float mixAudio = (basicAudio * (1 - lockStrength) + pllAudio * lockStrength);
				
				
				float audio= audioPass.apply(0.5f * mixAudio);

				
				if (enableDebug) {
					
					t2.in= pllAudio;
					t2.quad= (float)tuned.quad/tunedStrength;

					vis.markCenter();
					vis.drawAnalog(Color.DARK_GRAY, 1);
					vis.drawAnalog(Color.LIGHT_GRAY, 0);
					vis.drawAnalog(Color.red, 1*lockStrength);
					vis.drawAnalog(Color.green, audio);
					vis.drawAnalog(Color.pink, 10*basicStrength);
					vis.drawIQ(Color.orange, t2);
				}
				
				audioSampler.accept(audio);
			};
			
			IQSampleConsumer<RuntimeException> sink= intermediateResample ? new DownsamplerIQ<>(LanczosTable.of(3), rawSampleRate, sampleRate, new IQSampleBufferThread<>(2048, "First DS", tuner)) : tuner;


			IQSample iq = new IQSample();
			
			while (sr.read(iq)) {
				sink.accept(iq);
			}
			
			
			
			line.drain();
		}
		

	}
}
