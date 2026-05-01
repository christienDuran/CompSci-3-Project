package org;/*
For creating Goals that will appear in the calendar as progress bars, with a name, a current value, and a target value.
The target value can be incremented or decremented by any amount but cannot exceed the target value or fall below zero.
 */

public class Goal extends ProgressBar {
	private int goalId;
	private String name;

	public Goal(int goalId, String name, double currentValue, double targetValue) {
		super(currentValue, targetValue);
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Goal name is required.");
		}
		this.goalId = goalId;
		this.name = name.trim();
	}

	public int getGoalId() {
		return goalId;
	}

	public void setGoalId(int goalId) {
		this.goalId = goalId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Goal name is required.");
		}
		this.name = name.trim();
	}

	public String progressLabel() {
		return String.format("%.0f / %.0f (%d%%)", currentValue, targetValue, progressPercent());
	}
}
