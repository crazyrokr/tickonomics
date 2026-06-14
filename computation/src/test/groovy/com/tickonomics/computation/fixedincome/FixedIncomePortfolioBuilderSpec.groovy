package com.tickonomics.computation.fixedincome

import spock.lang.Specification
import spock.lang.Subject

class FixedIncomePortfolioBuilderSpec extends Specification {

  @Subject
  FixedIncomePortfolioBuilder builder = new FixedIncomePortfolioBuilder()

  List<BondPosition> bonds

  def setup() {
    bonds = [
        new BondPosition("2Y", 1.9d, 4.5d, 0.0d),
        new BondPosition("5Y", 4.7d, 4.3d, 0.0d),
        new BondPosition("10Y", 8.9d, 4.5d, 0.0d),
        new BondPosition("30Y", 19.0d, 4.8d, 0.0d)
    ]
  }

  def "Given target duration 5, when buildBullet, then selects closest bond"() {
    when:
        FixedIncomePortfolio portfolio = builder.buildBullet(5.0d, bonds)

    then:
        portfolio.type() == FixedIncomeStrategyType.BULLET_PORTFOLIO
        portfolio.positions().size() == 1
        Math.abs(portfolio.positions().first().duration() - 4.7d) < 0.1d
  }

  def "Given short and long tenors, when buildBarbell, then duration-weighted allocation"() {
    when:
        FixedIncomePortfolio portfolio = builder.buildBarbell(2.0d, 20.0d, bonds)

    then:
        portfolio.type() == FixedIncomeStrategyType.BARBELL_PORTFOLIO
        portfolio.positions().size() == 2
        portfolio.convexity() > 0
  }

  def "Given three tenors, when buildDurationNeutral, then net duration is exactly zero"() {
    when:
        FixedIncomePortfolio portfolio = builder.buildDurationNeutral(2.0d, 5.0d, 20.0d, bonds)

    then:
        portfolio.type() == FixedIncomeStrategyType.DURATION_NEUTRAL
        portfolio.positions().size() == 3
        Math.abs(portfolio.portfolioDuration()) < 1e-9
  }

  def "Given arbitrary dS<dM<dL, when buildDurationNeutral, then net duration is zero"() {
    given:
        def custom = [
            new BondPosition("1Y", 0.9d, 4.0d, 0.0d),
            new BondPosition("7Y", 6.3d, 4.4d, 0.0d),
            new BondPosition("30Y", 19.0d, 4.8d, 0.0d)
        ]

    when:
        FixedIncomePortfolio portfolio = builder.buildDurationNeutral(1.0d, 6.0d, 20.0d, custom)

    then:
        Math.abs(portfolio.portfolioDuration()) < 1e-9
  }

  def "Given long duration not greater than short, when buildDurationNeutral, then throws"() {
    when:
        builder.buildDurationNeutral(10.0d, 5.0d, 5.0d, bonds)

    then:
        thrown(IllegalArgumentException)
  }

  def "Given empty bond list, when buildBullet, then throws IllegalArgumentException"() {
    when:
        builder.buildBullet(5.0d, [])

    then:
        thrown(IllegalArgumentException)
  }
}
