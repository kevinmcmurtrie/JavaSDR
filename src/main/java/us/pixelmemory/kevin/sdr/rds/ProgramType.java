package us.pixelmemory.kevin.sdr.rds;

public interface ProgramType {
	String title();

	int code();

	static ProgramType ofCode(final int code, final boolean us) {
		return us ? ProgramTypeUS.ofCode(code) : ProgramTypeEU.ofCode(code);
	}
}
