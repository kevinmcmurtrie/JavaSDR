package us.pixelmemory.kevin.sdr;

/**
 * Class representing an IQ (in-phase, quadrature phase) sample.
 * <br>
 * Float precision is used even though Java is optimized for double precision.
 * These IQSample values end up in arrays, so smaller is better for CPU caching.
 * This is an excellent candidate for a value class later on.  Two floats could be a primitive 64 bit value.
 * <br>
 * Precision is up to 2^24 bits, which is plenty for noisy RF.
 */
public final class IQSample {
	private static final IQSample ZERO_PHASE_1 = new IQSample(0f);

	public float in; // real, in-phase
	public float quad; // Imaginary, quadrature

	public IQSample(final float in, final float quad) {
		set(in, quad);
	}

	public IQSample() {
		set(ZERO_PHASE_1);
	}

	public IQSample(final float moment) {
		setMoment(moment);
	}

	public IQSample(final IQSample o) {
		set(o);
	}

	public void set(final IQSample o) {
		this.in = o.in;
		this.quad = o.quad;
	}

	public void set(final float in, final float quad) {
		this.in = in;
		this.quad = quad;
	}

	public void setMoment(final double moment) {
		in = (float)Math.cos(moment);
		quad = (float)Math.sin(moment);
	}

	public void multiply(final float x) {
		in *= x;
		quad *= x;
	}

	public void divide(final float x) {
		in /= x;
		quad /= x;
	}

	public void rotateRight() {
		final float t = -quad;
		quad = in;
		in = t;
	}

	public void conjugate() {
		quad = -quad;
	}

	public void rotate(final double moment) {
		final double rin = Math.cos(moment);
		final double rquad = Math.sin(moment);
		final double t = in * rin - quad * rquad;
		quad = (float)(in * rquad + quad * rin);
		in = (float)t;
	}

	public void multiply(final IQSample s) {
		final float t = in * s.in - quad * s.quad;
		quad = in * s.quad + quad * s.in;
		in = t;
	}

	public float phase() {
		return (float)Math.atan2(quad, in);
	}

	public float magnitude() {
		return (float)Math.hypot(in, quad);
	}

	@Override
	public String toString() {
		return in + " + " + quad + "i, phase=" + phase() + " magnitude=" + magnitude();
	}

	public static void main(final String args[]) {
		final IQSample s = new IQSample();
		System.out.println(s);
		s.setMoment(0.1f);
		System.out.println(s);
		s.setMoment(0.2f);
		System.out.println(s);
		
		s.rotate(0.01f);
		for (int i = 0; i < 100; ++i) {
			System.out.println(s);
			s.rotate(0.1f);
		}
		
		IQSample test= new IQSample ();
		for (int i= 0; i < 1000000; ++i) {
			float c= i/1000f;
			test.setMoment(c);
			double in= Math.cos(c);
			double quad= Math.sin(c);
			double phase = Math.atan2(quad, in);
			double magnitude = Math.hypot(in, quad);
			
			if (Math.abs(test.in - in) > 0.01d) {
				System.out.println("In error " + Math.abs(test.in - in) + " at " + i);
			}
			
			if (Math.abs(test.quad - quad) > 0.01d) {
				System.out.println("Quad error " + Math.abs(test.quad - quad) + " at " + i);
			}
			
			if (Math.abs(test.phase() - phase) > 0.01d) {
				System.out.println("Phase error " + Math.abs(test.phase() - phase) + " at " + i);
			}
			
			if (Math.abs(test.magnitude() - magnitude) > 0.01d) {
				System.out.println("Magnitude error " + Math.abs(test.magnitude() - magnitude) + " at " + i);
			}
		}
		
	}
	
	

	
}
