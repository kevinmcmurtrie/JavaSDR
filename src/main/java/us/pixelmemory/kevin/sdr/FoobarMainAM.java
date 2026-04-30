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

import us.pixelmemory.kevin.sdr.receivers.AM;
import us.pixelmemory.kevin.sdr.sampleformat.SampleConverters;
import us.pixelmemory.kevin.sdr.sampleformat.SampleConverters.SampleReader;

public class FoobarMainAM {
	private static final boolean enableDebug = true;

	public static void main(String[] args) throws InterruptedException, UnsupportedAudioFileException, IOException, LineUnavailableException {
		final float IQGain = 30f;
		final IQVisualizer vis;
		if (enableDebug) {
			vis= new IQVisualizer(0.2f);
			vis.syncOnColor(Color.green);
		} else {
			vis= null;
		}
		
		final String am= "/home/mcmurtri/SDR/SDRconnect_IQ_20250322_173407_741000HZ.wav";
		final float srckHz= 741;
		
		final float audioSampleRate= 44100f;	
		final String theFile= am;
		

		AudioFormat outputFormat= new AudioFormat(audioSampleRate, 16, 1, true, false); 
		
		try (SampleReader<IOException> sr = SampleConverters.createPcmSigned16BitLeReader(AudioSystem.getAudioInputStream(new File(theFile)), IQGain);
				SourceDataLine line= AudioSystem.getSourceDataLine(outputFormat)) {
			
			line.open();
			line.start();
			
			FloatConsumer<RuntimeException> audioOutput= SampleConverters.createPcmSignedMono16BitLe(line::write);
			
			
			final float wantedRadiokHz = 740;
			final float radioAFTRange = 1000;
			final float audioBandwidth = 10200f;
			
			final AM<RuntimeException> amRadio= new AM<>(sr.getSampleRateHz(), 1000f*(srckHz-wantedRadiokHz), radioAFTRange, audioOutput, audioSampleRate, audioBandwidth, enableDebug);

			IQSample iq = new IQSample();
			while (sr.read(iq)) {
				amRadio.accept(iq);
			}
			
			line.drain();
		}
	}
}
