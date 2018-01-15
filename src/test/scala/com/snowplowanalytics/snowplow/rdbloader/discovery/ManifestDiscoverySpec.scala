/*
 * Copyright (c) 2012-2018 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.rdbloader
package discovery

import java.time.Instant
import java.util.UUID

import scala.util.Random.shuffle
import cats._
import cats.data.{State => _, _}
import cats.implicits._
import com.snowplowanalytics.manifest.core.ProcessingManifest
import com.snowplowanalytics.manifest.core.ProcessingManifest._
import com.snowplowanalytics.manifest.core.ProcessingManifest.ManifestError.{Corrupted, ParseError}
import com.snowplowanalytics.snowplow.rdbloader.LoaderError.{DiscoveryError, ManifestFailure}
import com.snowplowanalytics.snowplow.rdbloader.config.Semver
import org.specs2.Specification

class ManifestDiscoverySpec extends Specification { def is = s2"""
  Return successful empty list for empty manifest $e1
  Return successful full discovery without shredded types $e2
  Return combined failure for invalid base path and invalid shredded type $e3
  Return multiple successfully discovered shredded types $e4
  Return multiple successfully discovered discoveries $e5
  """

  def e1 = {
    val action = ManifestDiscovery.discover("test-storage", "us-east-1", None)
    val result = action.value.foldMap(ManifestDiscoverySpec.interpreter(Nil))
    result must beRight(List.empty[Item])
  }

  def e2 = {
    val time = Instant.now()
    val base = S3.Folder.coerce("s3://folder")
    val records = List(
      Record(base, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00000"), StepState.New), time, "", None),
      Record(base, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processing), time.plusSeconds(10), "", None),
      Record(base, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processed), time.plusSeconds(20), "", None)
    )

    val action = ManifestDiscovery.discover("test-storage", "us-east-1", None)
    val result = action.value.foldMap(ManifestDiscoverySpec.interpreter(records))
    result must beRight(List(
      DataDiscovery.FullDiscovery(base, 0, Nil)
    ))
  }

  def e3 = {
    val time = Instant.now()
    val payload = Some(Payload.empty.copy(set = Map("processed:shredder:types" -> Set("iglu:com.acme/event/jsonschema/0-0-1"))))
    val records = List(
      Record("invalidFolder", Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00000"), StepState.New), time, "", None),
      Record("invalidFolder", Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processing), time.plusSeconds(10), "", None),
      Record("invalidFolder", Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processed), time.plusSeconds(20), "", payload)
    )

    val action = ManifestDiscovery.discover("test-storage", "us-east-1", None)
    val result = action.value.foldMap(ManifestDiscoverySpec.interpreter(records))
    result must beLeft.like {
      case DiscoveryError(List(ManifestFailure(Corrupted(ParseError(error))))) =>
        error must endingWith("Key [iglu:com.acme/event/jsonschema/0-0-1] is invalid Iglu URI, Path [invalidFolder] is not valid base for shredded type. Bucket name must start with s3:// prefix")
    }
  }

  def e4 = {
    val time = Instant.now()
    val base1 = S3.Folder.coerce("s3://snowplow-enriched-archive/shredded/good/run=2018-01-12-03-10-30")
    val base2 = S3.Folder.coerce("s3://snowplow-enriched-archive/shredded/good/run=2018-01-12-03-20-30")
    val payload1 = Some(Payload.empty.copy(set = Map("processed:shredder:types" -> Set("iglu:com.acme/event/jsonschema/1-0-1"))))
    val payload2 = Some(Payload.empty.copy(set = Map("processed:shredder:types" -> Set("iglu:com.acme/context/jsonschema/1-0-0", "iglu:com.acme/context/jsonschema/1-0-1"))))
    val records = List(
      Record(base1, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00000"), StepState.New), time, "", None),
      Record(base1, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processing), time.plusSeconds(10), "", None),
      Record(base1, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processed), time.plusSeconds(20), "", payload1),
      Record(base1, Application("snowplow-rdb-loader", "0.13.0", Some("id"), None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00002"), StepState.Processing), time.plusSeconds(30), "", None),
      Record(base1, Application("snowplow-rdb-loader", "0.13.0", Some("id"), None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00002"), StepState.Processed), time.plusSeconds(30), "", None),

      Record(base2, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00003"), StepState.New), time, "", None),
      Record(base2, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00004"), StepState.Processing), time.plusSeconds(50), "", None),
      Record(base2, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00004"), StepState.Processed), time.plusSeconds(60), "", payload2)
    )

    val action = ManifestDiscovery.discover("id", "us-east-1", None)
    val result = action.value.foldMap(ManifestDiscoverySpec.interpreter(records))
    result must beRight(List(
      DataDiscovery.FullDiscovery(base2, 0, List(
        ShreddedType(
          ShreddedType.Info(base2, "com.acme", "context", 1, Semver(0,13,0)),
          S3.Key.coerce("s3://jsonpaths-assets/com.acme/context_1.json")
        )
      ))
    ))
  }

  def e5 = {
    val time = Instant.now()
    val base1 = S3.Folder.coerce("s3://snowplow-enriched-archive/shredded/good/run=2018-01-12-03-10-30")
    val base2 = S3.Folder.coerce("s3://snowplow-enriched-archive/shredded/good/run=2018-01-12-03-20-30")
    val base3 = S3.Folder.coerce("s3://snowplow-enriched-archive/shredded/good/run=2018-01-12-03-30-30")
    val payload1 = Some(Payload.empty.copy(set = Map("processed:shredder:types" -> Set("iglu:com.acme/event/jsonschema/1-0-1"))))
    val payload2 = Some(Payload.empty.copy(set = Map("processed:shredder:types" -> Set("iglu:com.acme/context/jsonschema/1-0-0", "iglu:com.acme/context/jsonschema/1-0-1"))))
    val records = List(
      Record(base1, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00000"), StepState.New), time, "", None),
      Record(base1, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processing), time.plusSeconds(10), "", None),
      Record(base1, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00001"), StepState.Processed), time.plusSeconds(20), "", payload1),

      Record(base2, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00002"), StepState.New), time, "", None),
      Record(base2, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00003"), StepState.Processing), time.plusSeconds(10), "", None),
      Record(base2, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00003"), StepState.Processed), time.plusSeconds(20), "", payload1),

      Record(base3, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00004"), StepState.New), time, "", None),
      Record(base3, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00005"), StepState.Processing), time.plusSeconds(50), "", None),
      Record(base3, Application("snowplow-rdb-shredder", "0.13.0", None, None), State(UUID.fromString("7c96c841-fc38-437d-bfec-4c1cd9b00005"), StepState.Processed), time.plusSeconds(60), "", payload2)
    )

    val expected = List(
      DataDiscovery.FullDiscovery(base1, 0, List(
        ShreddedType(
          ShreddedType.Info(base1, "com.acme", "event", 1, Semver(0,13,0)),
          S3.Key.coerce("s3://jsonpaths-assets-other/com.acme/event_1.json")
        )
      )),
      DataDiscovery.FullDiscovery(base2, 0, List(
        ShreddedType(
          ShreddedType.Info(base2, "com.acme", "event", 1, Semver(0,13,0)),
          S3.Key.coerce("s3://jsonpaths-assets-other/com.acme/event_1.json")
        )
      )),
      DataDiscovery.FullDiscovery(base3, 0, List(
        ShreddedType(
          ShreddedType.Info(base3, "com.acme", "context", 1, Semver(0,13,0)),
          S3.Key.coerce("s3://jsonpaths-assets/com.acme/context_1.json")
        )
      ))
    )

    val action = ManifestDiscovery.discover("id", "us-east-1", None)
    val result = action.value.foldMap(ManifestDiscoverySpec.interpreter(records))
    result must beRight.like {
      case list => list must containTheSameElementsAs(expected)
    }
  }
}

object ManifestDiscoverySpec {

  type F[A] = Either[ManifestError, A]

  def interpreter(records: List[Record]): LoaderA ~> Id = new (LoaderA ~> Id) {
    val manifest = ManifestDiscoverySpec.InMemoryManifest(records)
    def apply[A](effect: LoaderA[A]): Id[A] = {
      effect match {
        case LoaderA.ManifestDiscover(predicate) =>
          manifest.unprocessed(predicate).leftMap(LoaderError.fromManifestError)

        case LoaderA.Get("com.acme/context_1.json") =>
          S3.Key.coerce("s3://jsonpaths-assets/com.acme/context_1.json").some.some

        case LoaderA.Get("com.acme/event_1.json") =>
          S3.Key.coerce("s3://jsonpaths-assets-other/com.acme/event_1.json").some.some

        case action =>
          throw new RuntimeException(s"Unexpected Action [$action]")
      }
    }
  }

  case class InMemoryManifest(records: List[Record]) extends ProcessingManifest[F] {

    val stateBuffer = collection.mutable.ListBuffer(records: _*)

    def mixed: List[ProcessingManifest.Record] = shuffle(stateBuffer.toList)

    def getItem(id: ItemId): Either[ManifestError, Option[Item]] = {
      val map = mixed.groupBy(_.id).map { case (i, r) => (i, Item(NonEmptyList.fromListUnsafe(r))) }
      Right(map.get(id))
    }

    def put(id: ItemId, app: Application, state: State, payload: Option[Payload]): Either[ManifestError, Instant] =
      Right {
        val time = Instant.now()
        stateBuffer += Record(id, app, state, time, "0.1.0", payload)
        time
      }

    def list: Either[ManifestError, List[Record]] = Right(mixed)
  }
}
