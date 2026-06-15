package com.tickonomics.ingestion.factor

import com.tickonomics.cdm.adapter.FrenchFactorCdmAdapter
import com.tickonomics.cdm.enums.FactorSet
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FrenchFactorClientSpec extends Specification {

  FrenchFactorCdmAdapter cdmAdapter = new FrenchFactorCdmAdapter()

  @Subject
  FrenchFactorClient client

  def setup() {
    client = new FrenchFactorClient(null, cdmAdapter)
  }

  def "given valid 3-factor CSV, when parseZipCsv, then return rows"() {
    given:
      def csv = """
      This file was created by Professor Kenneth R. French
      192607,    2.62,   -0.21,   -0.31,    0.22
      192608,    2.56,   -0.33,    0.03,    0.25
      """.stripIndent().trim()
      def zipBytes = createZip(csv)

    when:
      def results = client.parseZipCsv(zipBytes, FactorSet.FACTOR_3, "MONTHLY")

    then:
      results.size() == 2
      results[0].time() == YearMonth.of(1926, 7).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
      results[0].rmRf() == 2.62
      results[0].smb() == -0.21
      results[0].hml() == -0.31
  }

  def "given valid 5-factor CSV, when parseZipCsv, then return rows"() {
    given:
      def csv = """
      This file was created by Professor Kenneth R. French
      196307,   -0.39,   -0.54,   -0.68,    0.25,   -0.30,    0.27
      196308,    0.51,    0.24,   -0.73,    0.49,    0.24,    0.26
      """.stripIndent().trim()
      def zipBytes = createZip(csv)

    when:
      def results = client.parseZipCsv(zipBytes, FactorSet.FACTOR_5, "MONTHLY")

    then:
      results.size() == 2
      results[0].rmRf() == -0.39
      results[0].rmw() == 0.25
      results[0].cma() == -0.30
      results[0].rf() == 0.27
  }

  def "given valid momentum CSV, when parseZipCsv, then return rows"() {
    given:
      def csv = """
      Momentum Factor
      192701,    0.37
      192702,    0.56
      """.stripIndent().trim()
      def zipBytes = createZip(csv)

    when:
      def results = client.parseZipCsv(zipBytes, FactorSet.MOMENTUM, "MONTHLY")

    then:
      results.size() == 2
      results[0].mom() == 0.37
      results[1].mom() == 0.56
  }

  def "given valid ST reversal CSV, when parseZipCsv, then stRev carries data"() {
    given:
      def csv = """
      Short-Term Reversal Factor
      192701,    0.37
      192702,    0.56
      """.stripIndent().trim()
      def zipBytes = createZip(csv)

    when:
      def results = client.parseZipCsv(zipBytes, FactorSet.ST_REVERSAL, "MONTHLY")

    then:
      results.size() == 2
      results[0].stRev() == 0.37
      results[1].stRev() == 0.56
      Double.isNaN(results[0].ltRev())
  }

  def "given valid LT reversal CSV, when parseZipCsv, then ltRev carries data"() {
    given:
      def csv = """
      Long-Term Reversal Factor
      192701,    0.21
      192702,    0.09
      """.stripIndent().trim()
      def zipBytes = createZip(csv)

    when:
      def results = client.parseZipCsv(zipBytes, FactorSet.LT_REVERSAL, "MONTHLY")

    then:
      results.size() == 2
      results[0].ltRev() == 0.21
      results[1].ltRev() == 0.09
      Double.isNaN(results[0].stRev())
  }

  def "given ST reversal row, when adapted to CDM, then stRev is preserved (not NaN)"() {
    given:
      def time = YearMonth.of(1926, 7).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
      def row = new com.tickonomics.cdm.adapter.raw.FrenchFactorRow(
          time, FactorSet.ST_REVERSAL, "MONTHLY",
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
          1.35d, Double.NaN)

    when:
      def result = cdmAdapter.toCdm(row)

    then:
      Math.abs(result.stRev() - 0.0135d) < 1e-9
      Double.isNaN(result.ltRev())
  }

  def "given missing value sentinel -99.99, when parseRow, then return NaN"() {
    given:
      def csv = "192607,    2.62,   -99.99,   -0.31,    0.22"
      def zipBytes = createZip(csv)

    when:
      def results = client.parseZipCsv(zipBytes, FactorSet.FACTOR_3, "MONTHLY")

    then:
      results.size() == 1
      results[0].smb() == -99.99
      when:
        def cdm = cdmAdapter.toCdm(results[0])
      then:
        Double.isNaN(cdm.smb())
  }

  def "given empty CSV, when parseZipCsv, then return empty"() {
    given:
      def zipBytes = createZip("")

    when:
      def results = client.parseZipCsv(zipBytes, FactorSet.FACTOR_3, "MONTHLY")

    then:
      results.isEmpty()
  }

  def "given invalid date format, when parseRow, then skip row"() {
    when:
      def row = client.parseRow("invalid,1.0,2.0,3.0", FactorSet.FACTOR_3, "MONTHLY")

    then:
      row == null
  }

  def "given row with too few columns, when parseRow, then return null"() {
    when:
      def row = client.parseRow("192607,1.0", FactorSet.FACTOR_3, "MONTHLY")

    then:
      row == null
  }

  def "given CDM adapter, when toCdm, then divide by 100 for percentage conversion"() {
    given:
      def time = YearMonth.of(1926, 7).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
      def row = new com.tickonomics.cdm.adapter.raw.FrenchFactorRow(
          time, FactorSet.FACTOR_3, "MONTHLY", 2.62, -0.21, -0.31,
          Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN)

    when:
      def result = cdmAdapter.toCdm(row)

    then:
      result.rmRf() == 0.0262
      result.smb() == -0.0021
      result.hml() == -0.0031
      Double.isNaN(result.rmw())
      Double.isNaN(result.mom())
  }

  private byte[] createZip(String content) {
    def baos = new ByteArrayOutputStream()
    def zos = new ZipOutputStream(baos)
    zos.putNextEntry(new ZipEntry("data.csv"))
    zos.write(content.bytes)
    zos.closeEntry()
    zos.close()
    return baos.toByteArray()
  }
}
