package com.tickonomics.contracts

import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class ApiContractsSpec extends Specification {

  private Map<String, Object> loadYaml(String name) {
    def stream = getClass().classLoader.getResourceAsStream(name)
    assert stream != null : "missing contract resource $name"
    new Yaml().load(stream) as Map<String, Object>
  }

  def "openapi spec is version 3.1 and exposes the full planned REST surface"() {
    given: "the bundled OpenAPI document"
    def document = loadYaml("openapi.yaml")

    when: "the version and paths are inspected"
    def version = document["openapi"]
    def paths = document["paths"] as Map<String, Object>

    then: "it targets OpenAPI 3.1"
    version == "3.1.0"

    and: "the planned endpoint families are all present"
    paths.containsKey("/api/v1/kpi/ili")
    paths.containsKey("/api/v1/signals")
    paths.containsKey("/api/v1/config")
    paths.containsKey("/api/v1/anomaly/detect")
    paths.containsKey("/api/v1/regime/compare")
    paths.containsKey("/api/v1/optimization/run")
    paths.containsKey("/api/v1/sentiment/analyze")
    paths.containsKey("/api/v1/leverage/status")
    paths.containsKey("/api/v1/pairs/active")
    paths.containsKey("/api/v1/quant/audit/intersubjective-reproducibility/{id}")

    and: "the planned surface is comprehensively covered"
    paths.size() >= 40
  }

  def "every openapi operation exposes an operationId for client generation"() {
    given: "the bundled OpenAPI document"
    def document = loadYaml("openapi.yaml")

    when: "all path operations are collected"
    def paths = document["paths"] as Map<String, Object>
    def operationIds = []
    paths.values().each { item ->
      (item as Map<String, Object>).each { method, op ->
        if (method in ["get", "post", "put", "delete", "patch"]) {
          operationIds << ((op as Map<String, Object>)["operationId"])
        }
      }
    }

    then: "no operation is missing an operationId"
    operationIds.every { it != null && !it.toString().isBlank() }
  }

  def "asyncapi spec declares the price and signal WebSocket channels"() {
    given: "the bundled AsyncAPI document"
    def document = loadYaml("asyncapi.yaml")

    when: "the version and channels are inspected"
    def version = document["asyncapi"] as String
    def channels = document["channels"] as Map<String, Object>

    then: "it targets AsyncAPI 2.x"
    version.startsWith("2.")

    and: "both real-time channels are declared"
    channels.containsKey("/ws/prices")
    channels.containsKey("/ws/signals")
  }
}
