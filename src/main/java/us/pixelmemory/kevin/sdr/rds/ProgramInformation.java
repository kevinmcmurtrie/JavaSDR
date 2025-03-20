package us.pixelmemory.kevin.sdr.rds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import us.pixelmemory.kevin.sdr.rds.groups.GroupHandler;
import us.pixelmemory.kevin.sdr.rds.groups.GroupType;
import us.pixelmemory.kevin.sdr.rds.groups.T0X;

public class ProgramInformation implements GroupHandler {
	private final ConcurrentHashMap<GroupType, List<GroupHandler>> handlers = new ConcurrentHashMap<>();
	private int programIdentification;
	private ProgramType programType = ProgramTypeUS.None;
	private final String countryCode = "US";
	private boolean trafficProgram;

	private final T0X t0x = new T0X();

	public ProgramInformation() {
		configGroupHandler(t0x);
	}

	private void configGroupHandler(final GroupHandler gh) {
		final Set<GroupType> gts = gh.acceptanceTypes();
		gts.forEach(gt -> handlers.computeIfAbsent(gt, x -> new ArrayList<>()).add(gh));
	}

	@Override
	public void accept(final int a, final int b, final int c, final int d) {
		programIdentification = a;
		programType = ProgramType.ofCode((b >>> 5) & 0b11111, "US".equalsIgnoreCase(countryCode)); // 5..9
		trafficProgram = ((b >>> 10) & 1) != 0;

		final GroupType gt = GroupType.fromBlockB(b);
		final List<GroupHandler> hlist = handlers.get(gt);
		if (hlist != null) {
			hlist.forEach(gh -> gh.accept(a, b, c, d));
		}
	}

	@Override
	public String toString() {
		return "ProgramInformation [programIdentification=" + programIdentification + ", programType=" + programType + ", trafficProgram=" + trafficProgram + ", t0x=" + t0x + "]";
	}

	@Override
	public Set<GroupType> acceptanceTypes() {
		return Set.copyOf(handlers.keySet());
	}
}
