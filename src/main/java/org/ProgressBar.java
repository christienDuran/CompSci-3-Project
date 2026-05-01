package org;

/*
An abstract class for creating Goals and Budgets, includes attributes and methods common to both.
 */
public abstract class ProgressBar {
	protected double currentValue;
	protected double targetValue;

	protected ProgressBar(double currentValue, double targetValue) {
		if (targetValue <= 0) {
			throw new IllegalArgumentException("Target value must be greater than zero.");
		}
		this.targetValue = targetValue;
		this.currentValue = clamp(currentValue, 0, targetValue);
	}

	public double getCurrentValue() {
		return currentValue;
	}

	public double getTargetValue() {
		return targetValue;
	}

	public void setCurrentValue(double currentValue) {
		this.currentValue = clamp(currentValue, 0, targetValue);
	}

	public void setTargetValue(double targetValue) {
		if (targetValue <= 0) {
			throw new IllegalArgumentException("Target value must be greater than zero.");
		}
		this.targetValue = targetValue;
		this.currentValue = clamp(this.currentValue, 0, targetValue);
	}

	public void increment(double amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Increment amount cannot be negative.");
		}
		setCurrentValue(currentValue + amount);
	}

	public void decrement(double amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Decrement amount cannot be negative.");
		}
		setCurrentValue(currentValue - amount);
	}

	public double progressFraction() {
		if (targetValue <= 0) {
			return 0;
		}
		return currentValue / targetValue;
	}

	public int progressPercent() {
		return (int) Math.round(progressFraction() * 100.0);
	}

	private double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
