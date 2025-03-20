package us.pixelmemory.kevin.sdr.rds.groups;

public enum GroupType {
	//Ordinal is the code
	T0A ("Basic tuning and switching information only"), /* */
	T0B ("Basic tuning and switching information only"), /* */
	T1A ("Programme Item Number and slow labelling codes only"), /* */
	T1B ("Programme Item Number"), /* */
	T2A ("RadioText only"), /* */
	T2B ("RadioText only"), /* */
	T3A ("Applications Identification for ODA only"), /* */
	T3B ("Open Data Applications"), /* */
	T4A ("Clock-time and date only"), /* */
	T4B ("Open Data Applications"), /* */
	T5A ("Transparent Data Channels (32 channels) or ODA"), /* */
	T5B ("Transparent Data Channels (32 channels) or ODA"), /* */
	T6A ("In House applications or ODA"), /* */
	T6B ("In House applications or ODA"), /* */
	T7A ("Radio Paging or ODA"), /* */
	T7B ("Open Data Applications"), /* */
	T8A ("Traffic Message Channel or ODA"), /* */
	T8B ("Open Data Applications"), /* */
	T9A ("Emergency Warning System or ODA"), /* */
	T9B ("Open Data Applications"), /* */
	T10A ("Programme Type Name"), /* */
	T10B ("Open Data Applications"), /* */
	T11A ("Open Data Applications"), /* */
	T11B ("Open Data Applications"), /* */
	T12A ("Open Data Applications"), /* */
	T12B ("Open Data Applications"), /* */
	T13A ("Enhanced Radio Paging or ODA"), /* */
	T13B("Open Data Applications"), /* */
	T14A("Enhanced Other Networks information only"), /* */
	T14B ("Enhanced Other Networks information only"), /* */
	T15A ("Defined in RBDS only"), /* */
	T15B ("Fast switching information only"); /* */
	
	public final String title;

	public int code () {
		return ordinal();
	}
	
	public static GroupType ofCode (int code) {
		return values[code & 0b11111];
	}
	
	public static GroupType fromBlockB (int b) {
		return ofCode(b >>> 11);
	}
	
	private static final GroupType values[]= GroupType.values();
	private GroupType(String title) {
		this.title =  name().substring(1) + " " + title ;
	}
}
