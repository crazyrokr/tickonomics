package com.tickonomics.computation.talib

import spock.lang.Specification

class BBandsResultSpec extends Specification {

  def "given valid bands when construct then fields set"() {
    given:
        double[] upper = [11, 10, 9]
        double[] middle = [10, 9, 8]
        double[] lower = [9, 8, 7]
        int begIdx = 0
        int nbElement = 3

    when:
        def result = new BBandsResult(upper, middle, lower, begIdx, nbElement)

    then:
        result.upper() == upper
        result.middle() == middle
        result.lower() == lower
        result.begIdx() == begIdx
        result.nbElement() == nbElement
  }

  def "given bands with offset when valid methods called then return truncated arrays"() {
    given:
        def result = new BBandsResult(
            [11, 10, 0, 0] as double[],
            [10, 9, 0, 0] as double[],
            [9, 8, 0, 0] as double[],
            2, 2
        )

    expect:
        result.validUpper() == [11, 10] as double[]
        result.validMiddle() == [10, 9] as double[]
        result.validLower() == [9, 8] as double[]
  }

  def "given null upper band when construct then throws"() {
    when:
        new BBandsResult(null, [1] as double[], [1] as double[], 0, 1)

    then:
        thrown(NullPointerException)
  }

  def "given null middle band when construct then throws"() {
    when:
        new BBandsResult([1] as double[], null, [1] as double[], 0, 1)

    then:
        thrown(NullPointerException)
  }

  def "given null lower band when construct then throws"() {
    when:
        new BBandsResult([1] as double[], [1] as double[], null, 0, 1)

    then:
        thrown(NullPointerException)
  }

  def "given negative nbElement when construct then throws"() {
    when:
        new BBandsResult([] as double[], [] as double[], [] as double[], 0, -1)

    then:
        thrown(IllegalArgumentException)
  }
}
