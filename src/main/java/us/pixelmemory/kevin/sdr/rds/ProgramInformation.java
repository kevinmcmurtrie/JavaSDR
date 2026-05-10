package us.pixelmemory.kevin.sdr.rds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import us.pixelmemory.kevin.sdr.rds.groups.GroupHandler;
import us.pixelmemory.kevin.sdr.rds.groups.GroupType;
import us.pixelmemory.kevin.sdr.rds.groups.T0X;
import us.pixelmemory.kevin.sdr.rds.groups.T2X;
import us.pixelmemory.kevin.sdr.rds.groups.TXX;

public class ProgramInformation implements GroupHandler {
	private final ConcurrentHashMap<GroupType, Set<GroupHandler>> handlers = new ConcurrentHashMap<>();
	private int programIdentification;
	private ProgramType programType = ProgramTypeUS.None;
	private final String countryCode = "US";
	private boolean trafficProgram;

	public ProgramInformation() {
		configGroupHandler(new T0X());
		configGroupHandler(new T2X());
	}

	private void configGroupHandler(final GroupHandler gh) {
		final Set<GroupType> gts = gh.acceptanceTypes();
		gts.forEach(gt -> handlers.computeIfAbsent(gt, x -> new HashSet<>()).add(gh));
	}

	@Override
	public void accept(final int a, final int b, final int c, final int d) {
		programIdentification = a;
		programType = ProgramType.ofCode((b >>> 5) & 0b11111, "US".equalsIgnoreCase(countryCode)); // 5..9
		trafficProgram = ((b >>> 10) & 1) != 0;

		final GroupType gt = GroupType.fromBlockB(b);
		final Set<GroupHandler> hlist = handlers.computeIfAbsent(gt, x -> Set.of(new TXX()));
		hlist.forEach(gh -> gh.accept(a, b, c, d));

	}

	@Override
	public String toString() {
		final Set<GroupHandler> handlerSet= handlers.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
		return "ProgramInformation [programIdentification=" + programIdentification + ", programType=" + programType + ", trafficProgram=" + trafficProgram + "\n" + handlerSet;
	}

	@Override
	public Set<GroupType> acceptanceTypes() {
		return Set.copyOf(handlers.keySet());
	}
}
