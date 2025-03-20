package us.pixelmemory.kevin.sdr.rds.groups;

import java.util.Set;

public interface GroupHandler {
	void accept(int a, int b, int c, int d);
	Set<GroupType> acceptanceTypes ();
}
