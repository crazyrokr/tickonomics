package com.tickonomics.computation.fixedincome;

import java.util.List;

public class FixedIncomePortfolioBuilder {

  public FixedIncomePortfolio buildBullet(double targetDuration, List<BondPosition> availableBonds) {
    BondPosition closest = null;
    double minDiff = Double.MAX_VALUE;

    for (var bond : availableBonds) {
      double diff = Math.abs(bond.duration() - targetDuration);
      if (diff < minDiff) {
        minDiff = diff;
        closest = bond;
      }
    }

    if (closest == null) {
      throw new IllegalArgumentException("No bonds available");
    }

    List<BondPosition> positions = List.of(new BondPosition(closest.tenor(), closest.duration(), closest.yield(), 1.0));

    return new FixedIncomePortfolio(
        FixedIncomeStrategyType.BULLET_PORTFOLIO,
        positions,
        closest.duration(),
        closest.yield(),
        closest.duration() * closest.duration());
  }

  public FixedIncomePortfolio buildBarbell(
      double shortDuration,
      double longDuration,
      List<BondPosition> availableBonds) {
    BondPosition shortBond = findClosestByDuration(availableBonds, shortDuration);
    BondPosition longBond = findClosestByDuration(availableBonds, longDuration);

    double totalDuration = shortBond.duration() + longBond.duration();
    double weightShort = longBond.duration() / totalDuration;
    double weightLong = shortBond.duration() / totalDuration;

    List<BondPosition> positions = List.of(
        new BondPosition(
            shortBond.tenor(),
            shortBond.duration(),
            shortBond.yield(),
            weightShort),
        new BondPosition(longBond.tenor(), longBond.duration(), longBond.yield(), weightLong));

    double portfolioDuration = weightShort * shortBond.duration() + weightLong * longBond.duration();
    double portfolioYield = weightShort * shortBond.yield() + weightLong * longBond.yield();
    double convexity = weightShort * Math.pow(shortBond.duration(), 2) + weightLong * Math.pow(longBond.duration(), 2);

    return new FixedIncomePortfolio(
        FixedIncomeStrategyType.BARBELL_PORTFOLIO,
        positions,
        portfolioDuration,
        portfolioYield,
        convexity);
  }

  public FixedIncomePortfolio buildDurationNeutral(
      double shortDuration,
      double midDuration,
      double longDuration,
      List<BondPosition> availableBonds) {
    BondPosition shortBond = findClosestByDuration(availableBonds, shortDuration);
    BondPosition midBond = findClosestByDuration(availableBonds, midDuration);
    BondPosition longBond = findClosestByDuration(availableBonds, longDuration);

    double dS = shortBond.duration();
    double dM = midBond.duration();
    double dL = longBond.duration();

    if (dL <= dS) {
      throw new IllegalArgumentException(
          "longDuration must exceed shortDuration for a duration-neutral butterfly");
    }

    double wMid = -1.0;
    double wShort = (dL - dM) / (dL - dS);
    double wLong = (dM - dS) / (dL - dS);

    double netDuration = wShort * dS + wMid * dM + wLong * dL;

    List<BondPosition> positions = List.of(
        new BondPosition(shortBond.tenor(), shortBond.duration(), shortBond.yield(), wShort),
        new BondPosition(midBond.tenor(), midBond.duration(), midBond.yield(), wMid),
        new BondPosition(longBond.tenor(), longBond.duration(), longBond.yield(), wLong));

    double portfolioYield = wShort * shortBond.yield() + wMid * midBond.yield() + wLong * longBond.yield();

    return new FixedIncomePortfolio(
        FixedIncomeStrategyType.DURATION_NEUTRAL,
        positions,
        netDuration,
        portfolioYield,
        0.0);
  }

  private BondPosition findClosestByDuration(List<BondPosition> bonds, double targetDuration) {
    BondPosition closest = null;
    double minDiff = Double.MAX_VALUE;

    for (var bond : bonds) {
      double diff = Math.abs(bond.duration() - targetDuration);
      if (diff < minDiff) {
        minDiff = diff;
        closest = bond;
      }
    }

    if (closest == null) {
      throw new IllegalArgumentException("No bond found for duration: " + targetDuration);
    }
    return closest;
  }
}
