package com.usujiotarako.client;

final class VirtualHotbarConfig {
	String context;
	int activeProfile;
	String[][] assignments = new String[3][9];
	String[][] assignmentSignatures = new String[3][9];
	String[] offhandAssignments = new String[3];
	boolean[] offhandAssignmentExplicit = new boolean[3];
	boolean[][] locked = new boolean[3][9];
	String[][] vanillaAssignments = new String[3][9];
	String[][] vanillaAssignmentSignatures = new String[3][9];
	String[] vanillaOffhandAssignments = new String[3];
	String[] vanillaOffhandAssignmentSignatures = new String[3];

	void validate() {
		if (assignments == null || assignments.length != 3) assignments = new String[3][9];
		for (int profile = 0; profile < 3; profile++) {
			if (assignments[profile] == null || assignments[profile].length != 9) assignments[profile] = new String[9];
		}
		if (assignmentSignatures == null || assignmentSignatures.length != 3) assignmentSignatures = new String[3][9];
		for (int profile = 0; profile < 3; profile++) {
			if (assignmentSignatures[profile] == null || assignmentSignatures[profile].length != 9) assignmentSignatures[profile] = new String[9];
		}
		if (offhandAssignments == null || offhandAssignments.length != 3) offhandAssignments = new String[3];
		if (offhandAssignmentExplicit == null || offhandAssignmentExplicit.length != 3) offhandAssignmentExplicit = new boolean[3];
		if (locked == null || locked.length != 3) locked = new boolean[3][9];
		for (int profile = 0; profile < 3; profile++) {
			if (locked[profile] == null || locked[profile].length != 9) locked[profile] = new boolean[9];
		}
		if (vanillaAssignments == null || vanillaAssignments.length != 3) vanillaAssignments = new String[3][9];
		if (vanillaAssignmentSignatures == null || vanillaAssignmentSignatures.length != 3) vanillaAssignmentSignatures = new String[3][9];
		for (int profile = 0; profile < 3; profile++) {
			if (vanillaAssignments[profile] == null || vanillaAssignments[profile].length != 9) vanillaAssignments[profile] = new String[9];
			if (vanillaAssignmentSignatures[profile] == null || vanillaAssignmentSignatures[profile].length != 9) vanillaAssignmentSignatures[profile] = new String[9];
		}
		if (vanillaOffhandAssignments == null || vanillaOffhandAssignments.length != 3) vanillaOffhandAssignments = new String[3];
		if (vanillaOffhandAssignmentSignatures == null || vanillaOffhandAssignmentSignatures.length != 3) vanillaOffhandAssignmentSignatures = new String[3];
		activeProfile = Math.floorMod(activeProfile, 3);
	}
}
