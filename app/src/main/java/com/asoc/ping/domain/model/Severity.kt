package com.asoc.ping.domain.model

/** Human-assigned severity of an incident. Ordered low -> high for comparisons. */
enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}
