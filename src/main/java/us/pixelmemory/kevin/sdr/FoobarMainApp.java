package us.pixelmemory.kevin.sdr;

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

import us.pixelmemory.kevin.sdr.firfilters.LanczosTable;
import us.pixelmemory.kevin.sdr.pipeline.IQSampleBufferThread;
import us.pixelmemory.kevin.sdr.rds.RDSDecoder;
import us.pixelmemory.kevin.sdr.receivers.FMBroadcast;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerIQ;
import us.pixelmemory.kevin.sdr.resamplers.DownsamplerStereo;
import us.pixelmemory.kevin.sdr.sampleformat.SampleConverters;
import us.pixelmemory.kevin.sdr.sampleformat.SampleConverters.SampleReader;
import us.pixelmemory.kevin.sdr.tuners.FrequencyLock;

public class FoobarMainApp {

	public static void main(String[] args) throws InterruptedException, UnsupportedAudioFileException, IOException, LineUnavailableException {
		final float IQGain = 10f;
		final float targetSampleRate= 400000; //FIXME
		final String music= "/home/mcmurtri/SDR/SDRconnect_IQ_20250211_155243_98500000HZ.wav"; //Trash pilot
		final String talk= "/home/mcmurtri/SDR/SDRconnect_IQ_20250214_122829_105700000HZ.wav"; //Super clean.  PI=0x499B
		final String demo= "/home/mcmurtri/SDR/SDRuno_20200907_184033Z_88110kHz.wav"; //Off-center pilot
		final String bbc= "/home/mcmurtri/SDR/SDRconnect_IQ_20250222_223902_88500000HZ.wav";//Pilot phase jumps
		final String wild= "/home/mcmurtri/SDR/SDRconnect_IQ_20250222_221653_94900000HZ.wav";
		final String talk2= "/home/mcmurtri/SDR/SDRconnect_IQ_20250222_224030_88500000HZ.wav"; //Pilot phase jumps
		final String needle= "/home/mcmurtri/SDR/SDRconnect_IQ_20250222_232117_97300000HZ.wav"; //fast
		final String highStereo = "/home/mcmurtri/SDR/SDRconnect_IQ_20250222_232434_99700000HZ.wav";
		final String punk = "/home/mcmurtri/SDR/SDRconnect_IQ_20250227_225432_90500000HZ.wav"; //Very weak
		final String rdsTest = "/home/mcmurtri/SDR/SDRuno_20200907_184033Z_88110kHz.wav"; // From SDR

		
		final float audioSampleRate= 41000f;
		
		
		
		final String theFile= music;
		
		
//		class BAOS extends ByteArrayOutputStream {
//			InputStream toInput () {
//				return new ByteArrayInputStream(buf, 0, count);
//			}
//		}
//		BAOS audioBuffer= new BAOS();
		
		AudioFormat outputFormat= new AudioFormat(audioSampleRate, 16, 2, true, false); 
		
		try (SampleReader<IOException> sr = SampleConverters.createPcmSigned16BitLeReader(AudioSystem.getAudioInputStream(new File(theFile)), IQGain);
				SourceDataLine line= AudioSystem.getSourceDataLine(outputFormat)) {
			
			line.open();
			line.start();
			
			final ByteArrayConsumer<RuntimeException> out= (byte buffer[], int offset, int length) -> {
				line.write(buffer, offset, length);
				//audioBuffer.write(buffer, offset, length);
			};

			final float rawSampleRate= sr.getSampleRateHz();
			boolean intermediateResample = rawSampleRate > targetSampleRate;
			final float sampleRate = intermediateResample ? targetSampleRate : rawSampleRate;
			
			DownsamplerStereo<RuntimeException> audioSampler= new DownsamplerStereo<>(LanczosTable.of(3), sampleRate, audioSampleRate, SampleConverters.createPcmSignedStereo16BitLe(out));
			RDSDecoder rds= new RDSDecoder (sampleRate);
			FMBroadcast<RuntimeException> stereo= new FMBroadcast<>(sampleRate, audioSampler, rds);
			
			final FrequencyLock aft= new FrequencyLock(sampleRate, 10, 1000d, true);

			IQSample tuned = new IQSample();
			final float gain= sampleRate/(2*targetSampleRate);
			IQSampleConsumer<RuntimeException> fmDemod = iq -> {aft.accept(iq, tuned);stereo.accept(aft.getPhase()*gain);};
			
			//FIXME: Thread leak
			IQSampleConsumer<RuntimeException> sink= intermediateResample ? new DownsamplerIQ<>(LanczosTable.of(3), rawSampleRate, sampleRate, new IQSampleBufferThread<>(2048, "First DS", fmDemod)) : fmDemod;
		

			IQSample iq = new IQSample();
			while (sr.read(iq)) {
				sink.accept(iq);
			}
			
			line.drain();
		}
		
//		AudioInputStream recordingAdaptor= new AudioInputStream(audioBuffer.toInput(), outputFormat, audioBuffer.size());
//		AudioSystem.write(recordingAdaptor, Type.WAVE, new File(theFile + "to.wav"));
	}
}
