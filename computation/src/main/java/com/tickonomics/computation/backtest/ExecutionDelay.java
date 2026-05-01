package com.tickonomics.computation.backtest;

public enum ExecutionDelay {
  DELAY_0(0),
  DELAY_1(1);

  private final int days;

  ExecutionDelay(int days) {
    this.days = days;
  }

  public int days() {
    return days;
  }
}
