package com.tickonomics.ingestion.datahub

import com.tickonomics.ingestion.writer.TimescaleDbWriter
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class DataHubBackfillClientSpec extends Specification {

  TimescaleDbWriter writer = Mock()

  @Subject
  DataHubBackfillClient client

  def setup() {
    client = new DataHubBackfillClient(null, writer)
    client.baseUrl = "https://datahub.io"
  }

  def "given valid Shiller CSV, when parseShillerCsv, then return rows"() {
    given:
      def csv = """Date,SP500,Dividend,Earnings,CPI,Long Interest Rate,Real Price,Real Dividend,Real Earnings,PE10
1871-01,4.44,0.26,0.4,12.46,5.32,4.44,0.26,0.4,0.0
1871-02,4.50,0.26,0.4,12.84,5.33,4.49,0.26,0.4,0.0
1871-03,4.61,0.26,0.4,13.03,5.32,4.58,0.26,0.4,0.0"""

    when:
      def results = client.parseShillerCsv(csv.bytes)

    then:
      results.size() == 3
      results[0].time() == YearMonth.of(1871, 1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
      results[0].price() == 4.44
      results[0].dividend() == 0.26
      results[0].cape() == 0.0
      results[2].price() == 4.61
  }

  def "given Shiller CSV with non-numeric price, when parseShillerCsv, then skip row"() {
    given:
      def csv = """Date,SP500,Dividend,Earnings,CPI
1871-01,4.44,0.26,0.4,12.46
1871-02,invalid,0.26,0.4,12.84
1871-03,4.61,0.26,0.4,13.03"""

    when:
      def results = client.parseShillerCsv(csv.bytes)

    then:
      results.size() == 2
      results[0].price() == 4.44
      results[1].price() == 4.61
  }

  def "given empty Shiller CSV, when parseShillerCsv, then return empty"() {
    given:
      def csv = "Date,SP500,Dividend,Earnings,CPI\n"

    when:
      def results = client.parseShillerCsv(csv.bytes)

    then:
      results.isEmpty()
  }

  def "given valid VIX CSV, when parsePriceCsv, then return rows"() {
    given:
      def csv = """DATE,VIX_HIGH,VIX_LOW,VIX_CLOSE
2004-01-02,17.96,16.78,17.49
2004-01-05,18.49,17.44,17.75
2004-01-06,18.28,17.13,17.57"""

    when:
      def results = client.parsePriceCsv(csv.bytes, "VIX")

    then:
      results.size() == 3
      results[0].value() == 17.96
      results[1].value() == 18.49
  }

  def "given price CSV with zero value, when parsePriceCsv, then skip"() {
    given:
      def csv = """DATE,PRICE
2004-01-02,17.49
2004-01-05,0.0
2004-01-06,17.57"""

    when:
      def results = client.parsePriceCsv(csv.bytes, "TEST")

    then:
      results.size() == 2
      results[0].value() == 17.49
      results[1].value() == 17.57
  }

  def "given oil CSV, when parsePriceCsv, then return rows"() {
    given:
      def csv = """DATE,PRICE
1986-01-02,25.56
1986-01-03,24.68"""

    when:
      def results = client.parsePriceCsv(csv.bytes, "OIL_WTI")

    then:
      results.size() == 2
      results[0].value() == 25.56
  }

  def "given gold monthly CSV, when parsePriceCsv, then return rows"() {
    given:
      def csv = """Date,Price
1833-01,18.93
1833-02,18.93"""

    when:
      def results = client.parsePriceCsv(csv.bytes, "GOLD")

    then:
      results.size() == 2
      results[0].time() == YearMonth.of(1833, 1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
      results[0].value() == 18.93
  }

  def "given malformed CSV, when parsePriceCsv, then return empty without exception"() {
    given:
      def csv = "not,a,valid,csv\n\n\n".bytes

    when:
      def results = client.parsePriceCsv(csv, "TEST")

    then:
      noExceptionThrown()
      results.isEmpty()
  }
}
