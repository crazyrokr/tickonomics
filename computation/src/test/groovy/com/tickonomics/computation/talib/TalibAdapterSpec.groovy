package com.tickonomics.computation.talib

import spock.lang.Shared
import spock.lang.Specification

class TalibAdapterSpec extends Specification {

  @Shared
  TalibAdapter adapter = new TalibAdapter()

  def "compute SMA matches expected"() {
    given:
        double[] data = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]

    when:
        double[] sma = adapter.computeSma(data, 3)

    then:
        sma.length == 8
        sma[0] == 2.0D
        sma[1] == 3.0D
        sma[7] == 9.0D
  }

  def "compute SMA with period equals length returns single value"() {
    given:
        double[] data = [2.0, 4.0, 6.0]

    when:
        double[] sma = adapter.computeSma(data, 3)

    then:
        sma.length == 1
        sma[0] == 4.0D
  }

  def "compute StdDev for constant data is zero"() {
    given:
        double[] data = [5.0, 5.0, 5.0, 5.0, 5.0]

    when:
        double[] std = adapter.computeStdDev(data, 3)

    then:
        std.every { it == 0.0 }
  }

  def "compute StdDev for known data is positive"() {
    given:
        double[] data = [1.0, 2.0, 3.0, 4.0, 5.0]

    when:
        double[] std = adapter.computeStdDev(data, 3)

    then:
        std.length > 0
        std[0] > 0
  }

  def "compute Correl for identical series is one"() {
    given:
        double[] data = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]

    when:
        double[] corr = adapter.computeCorrel(data, data, 5)

    then:
        corr.every { Math.abs(it - 1.0) < 1e-6 }
  }

  def "compute Correl for opposite series is minus one"() {
    given:
        double[] x = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]
        double[] y = [10.0, 9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0]

    when:
        double[] corr = adapter.computeCorrel(x, y, 5)

    then:
        corr.every { Math.abs(it - (-1.0)) < 1e-6 }
  }

  def "compute Correl with mismatched lengths throws exception"() {
    when:
        adapter.computeCorrel([1, 2] as double[], [1, 2, 3] as double[], 2)

    then:
        thrown(IllegalArgumentException)
  }

  def "compute Beta for identical series is one"() {
    given:
        double[] data = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]

    when:
        double[] beta = adapter.computeBeta(data, data, 5)

    then:
        beta.every { Math.abs(it - 1.0) < 1e-6 }
  }

  def "compute Beta with mismatched lengths throws exception"() {
    when:
        adapter.computeBeta([1] as double[], [1, 2] as double[], 1)

    then:
        thrown(IllegalArgumentException)
  }

  def "compute linear regression slope for linear data"() {
    given:
        double[] data = [1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0, 19.0]

    when:
        double[] slopes = adapter.computeLinearRegSlope(data, 5)

    then:
        slopes.length > 0
        slopes.every { Math.abs(it - 2.0) < 1e-6 }
  }

  def "compute linear regression slope for constant data is zero"() {
    given:
        double[] data = [5.0, 5.0, 5.0, 5.0, 5.0, 5.0]

    when:
        double[] slopes = adapter.computeLinearRegSlope(data, 3)

    then:
        slopes.every { Math.abs(it - 0.0) < 1e-9 }
  }

  def "compute Bollinger Bands middle equals SMA"() {
    given:
        double[] data = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0]

    when:
        BBandsResult bands = adapter.computeBollingerBands(data, 5, 2.0, 2.0)
        double[] sma = adapter.computeSma(data, 5)

    then:
        bands.validMiddle() == sma
  }

  def "compute Bollinger Bands upper above lower"() {
    given:
        double[] data = [10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0]

    when:
        BBandsResult bands = adapter.computeBollingerBands(data, 4, 2.0, 2.0)
        double[] upper = bands.validUpper()
        double[] lower = bands.validLower()

    then:
        (0..<upper.length).every { upper[it] >= lower[it] }
  }

  def "compute ROC matches expected"() {
    given:
        double[] data = [100.0, 105.0, 110.0, 99.0, 115.0]

    when:
        double[] roc = adapter.computeRoc(data, 1)

    then:
        roc.length == 4
        Math.abs(roc[0] - 5.0) < 1e-6
        Math.abs(roc[1] - 4.761904761) < 1e-6
        Math.abs(roc[2] - (-10.0)) < 1e-6
        Math.abs(roc[3] - 16.16161616) < 1e-6
  }

  def "compute ROC with period larger than data returns empty"() {
    given:
        double[] data = [1.0, 2.0]

    when:
        double[] roc = adapter.computeRoc(data, 5)

    then:
        roc.length == 0
  }

  def "compute RSI for monotonically increasing data is high"() {
    given:
        double[] data = (1..30).collect { it as double }

    when:
        double[] rsi = adapter.computeRsi(data, 14)

    then:
        rsi.length > 0
        rsi[-1] > 90.0
  }

  def "compute RSI for monotonically decreasing data is low"() {
    given:
        double[] data = (30..1).collect { it as double }

    when:
        double[] rsi = adapter.computeRsi(data, 14)

    then:
        rsi.length > 0
        rsi[-1] < 10.0
  }

  def "input validation for SMA"() {
    when:
        adapter.computeSma(data, 5)

    then:
        thrown(IllegalArgumentException)

    where:
        data << [null, new double[0]]
  }

  def "input validation for Correl"() {
    when:
        adapter.computeCorrel(null, [1] as double[], 5)

    then:
        thrown(IllegalArgumentException)
  }

  def "input validation for Beta"() {
    when:
        adapter.computeBeta(null, [1] as double[], 5)

    then:
        thrown(IllegalArgumentException)
  }
}
