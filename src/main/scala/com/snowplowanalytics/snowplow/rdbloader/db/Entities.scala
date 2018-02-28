package com.snowplowanalytics.snowplow.rdbloader.db

import java.time.Instant

import com.snowplowanalytics.snowplow.rdbloader.config.Semver

/** Different entities that are queried from database using `Decoder`s */
object Entities {

  /** Count query */
  case class Count(count: Long)

  case class Timestamp(etlTstamp: Instant)

  case class LoadManifestItem(etlTstamp: Instant,
                              commitTstamp: Instant,
                              eventCount: Int,
                              shreddedCardinality: Int)

  case class AtomicEventsDescription(version: Semver)
}
