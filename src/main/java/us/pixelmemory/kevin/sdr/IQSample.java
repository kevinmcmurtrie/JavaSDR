package us.pixelmemory.kevin.sdr;

public final class IQSample {
	private static final IQSample ZERO_PHASE_1 = new IQSample(0);

	public double in; // real, in-phase
	public double quad; // Imaginary, quadrature

	public IQSample(final double in, final double quad) {
		set(in, quad);
	}

	public IQSample() {
		set(ZERO_PHASE_1);
	}

	public IQSample(final double moment) {
		setMoment(moment);
	}

	public IQSample(final IQSample o) {
		set(o);
	}

	public void set(final IQSample o) {
		this.in = o.in;
		this.quad = o.quad;
	}

	public void set(final double in, final double quad) {
		this.in = in;
		this.quad = quad;
	}

	public void setMoment(final double moment) {
		in = Math.cos(moment);
		quad = Math.sin(moment);
	}

	public void multiply(final double x) {
		in *= x;
		quad *= x;
	}

	public void divide(final double x) {
		in /= x;
		quad /= x;
	}

	public void rotateRight() {
		final double t = -quad;
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
		quad = in * rquad + quad * rin;
		in = t;
	}

	public void multiply(final IQSample s) {
		final double t = in * s.in - quad * s.quad;
		quad = in * s.quad + quad * s.in;
		in = t;
	}

	public double phase() {
		return Math.atan2(quad, in);
	}

	public double magnitude() {
		return Math.hypot(in, quad);
	}

	@Override
	public String toString() {
		return in + " + " + quad + "i, phase=" + (float) phase() + " magnitude=" + (float) magnitude();
	}

	public static void main(final String args[]) {
		final IQSample s = new IQSample();
		System.out.println(s);
		s.setMoment(0.1);
		System.out.println(s);
		s.setMoment(0.2);
		System.out.println(s);
		
		s.rotate(0.01);
		for (int i = 0; i < 100; ++i) {
			System.out.println(s);
			s.rotate(0.1);
		}
	}
}
