package com.tickonomics.computation.transport

import org.apache.arrow.memory.RootAllocator
import spock.lang.Specification

class ArrowIpcTransportSpec extends Specification {

  ArrowIpcTransport transport = new ArrowIpcTransport()

  def "time series round trip"() {
    given:
        double[] values = [100.5, 101.2, 99.8, 102.3]
        long[] timestamps = [1000L, 2000L, 3000L, 4000L]

    when:
        byte[] serialized = transport.serializeTimeSeries("SPY", values, timestamps)
        def batch = transport.deserializeTimeSeries(serialized)

    then:
        serialized != null
        serialized.length > 0
        batch.values() == values
        batch.timestamps() == timestamps
  }

  def "single data point round trip"() {
    given:
        double[] values = [42.0]
        long[] timestamps = [9999L]

    when:
        byte[] data = transport.serializeTimeSeries("AAPL", values, timestamps)
        def batch = transport.deserializeTimeSeries(data)

    then:
        batch.values().length == 1
        batch.values()[0] == 42.0D
        batch.timestamps()[0] == 9999L
  }

  def "mismatched lengths when serialize throws exception"() {
    when:
        transport.serializeTimeSeries("SPY", [1, 2] as double[], [1] as long[])

    then:
        thrown(IllegalArgumentException)
  }

  def "analysis result serialization with multiple columns"() {
    given:
        def results = [
            "correlation": [0.95, 0.87, 0.91] as double[],
            "beta"       : [1.2, 0.8, 1.05] as double[]
        ]

    when:
        byte[] data = transport.serializeAnalysisResult(results)

    then:
        data != null
        data.length > 0
  }

  def "large dataset serialization size is reasonable"() {
    given:
        double[] large = new double[10_000]
        Random rnd = new Random()
        (0..<large.length).each { large[it] = rnd.nextDouble() }

    when:
        byte[] data = transport.serializeAnalysisResult(["values": large])

    then:
        data.length < large.length * 16
  }

  def "time series batch validation"() {
    when:
        new ArrowIpcTransport.TimeSeriesBatch(values, timestamps)

    then:
        thrown(exception)

    where:
        values                 | timestamps     || exception
        null                   | [1L] as long[] || NullPointerException
        [1.0] as double[]      | null           || NullPointerException
        [1.0, 2.0] as double[] | [1L] as long[] || IllegalArgumentException
  }

  def "custom allocator works"() {
    given:
        def allocator = new RootAllocator()
        def customTransport = new ArrowIpcTransport(allocator)
        double[] values = [1.0, 2.0]
        long[] ts = [100L, 200L]

    when:
        byte[] data = customTransport.serializeTimeSeries("TEST", values, ts)
        def batch = customTransport.deserializeTimeSeries(data)

    then:
        batch.values() == values
        batch.timestamps() == ts

    cleanup:
        allocator.close()
  }
}
