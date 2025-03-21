package us.pixelmemory.kevin.sdr.sampleformat;

import java.io.Closeable;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;

import us.pixelmemory.kevin.sdr.ByteArrayConsumer;
import us.pixelmemory.kevin.sdr.FloatConsumer;
import us.pixelmemory.kevin.sdr.FloatPairConsumer;
import us.pixelmemory.kevin.sdr.IQSample;
import us.pixelmemory.kevin.sdr.SimplerMath;

// TODO - refactor this junk drawer
public class SampleConverters {
	private SampleConverters() {
	}

	public interface SampleReader<T extends Throwable> extends Closeable {
		boolean read(IQSample sample) throws T;

		float getSampleRateHz();
	}

	// Turns floats into bytes of PCM Signed Mono 16Bit Little-Endian for sound output.
	public static <T extends Throwable> FloatConsumer<T> createPcmSignedMono16BitLe(final ByteArrayConsumer<T> out) {
		return new FloatConsumer<>() {
			// TODO - Dither or is there already enough noise?
			final byte buffer[] = new byte[2];

			@Override
			public void accept(float f) throws T {
				if (f > 1f) {
					f = 1f;
				} else if (f < -1f) {
					f = -1f;
				}
				final int i = Math.round(f * 32767);
				buffer[0] = (byte) (i & 0xff);
				buffer[1] = (byte) (i >> 8);
				out.accept(buffer, 0, 2);
			}
		};
	}

	public static <T extends Throwable> FloatPairConsumer<T> createPcmSignedStereo16BitLe(final ByteArrayConsumer<T> out) {
		return new FloatPairConsumer<>() {
			final byte buffer[] = new byte[4 * 64];
			int pos = 0;

			@Override
			public void accept(final float left, final float right) throws T {
				final int l = Math.round(SimplerMath.clamp(32767 * left, -32768, 32767));
				final int r = Math.round(SimplerMath.clamp(32767 * right, -32768, 32767));
				buffer[pos++] = (byte) (l & 0xff);
				buffer[pos++] = (byte) (l >> 8);
				buffer[pos++] = (byte) (r & 0xff);
				buffer[pos++] = (byte) (r >> 8);
				if (pos == buffer.length) {
					pos = 0;
					out.accept(buffer, 0, buffer.length);
				}
			}
		};
	}

	public static SampleReader<IOException> createPcmSigned16BitLeReader(final AudioInputStream in /* IQIQIQ */, final float gain) {
		System.out.println(in.getFormat());// DEBUG
		return new SampleReader<>() {
			private final byte buf[] = new byte[65536];
			private int size = 0;
			private int offset = 0;
			private final float intGain = gain / 32768f;

			private boolean fill() throws IOException {
				while (size < 4) {
					// Compact
					for (int i = 0; (size > 0) && (offset > 0); ++i, ++offset, --size) {
						buf[i] = buf[offset];
					}
					offset = 0;

					// Fill
					final int len = in.read(buf, size, buf.length - size);
					if (len < 0) {
						return false;
					}
					size += len;
				}
				return true;
			}

			@Override
			public boolean read(final IQSample sample) throws IOException {
				if (!fill()) {
					return false;
				}

				sample.in = intGain * ((buf[offset] & 0xFF) + (buf[offset + 1] << 8));
				sample.quad = intGain * ((buf[offset + 2] & 0xFF) + (buf[offset + 3] << 8));
				size -= 4;
				offset += 4;
				return true;
			}

			@Override
			public float getSampleRateHz() {
				return in.getFormat().getSampleRate();
			}

			@Override
			public void close() throws IOException {
				in.close();
			}
		};
	}

}
