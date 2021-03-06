/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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

import cats.{Id, ~>}
import cats.data.State

import org.specs2.Specification

import DataDiscovery._
import ShreddedType._
import LoaderError._
import S3.Key.{coerce => s3key}
import S3.Folder.{coerce => dir}
import config.Semver


class DataDiscoverySpec extends Specification { def is = s2"""
  Successfully discover two run folders at once $e1
  Successfully do eventual consistency check $e2
  Fail to proceed with empty target folder $e3
  Do not fail to proceed with empty shredded good folder $e4
  Successfully discover data in run folder $e5
  """

  def e1 = {
    def interpreter: LoaderA ~> Id = new (LoaderA ~> Id) {

      private val cache = collection.mutable.HashMap.empty[String, Option[S3.Key]]

      def apply[A](effect: LoaderA[A]): Id[A] = {
        effect match {
          case LoaderA.ListS3(bucket) =>
            Right(List(
              S3.Key.coerce(bucket + "run=2017-05-22-12-20-57/atomic-events/part-0000"),
              S3.Key.coerce(bucket + "run=2017-05-22-12-20-57/atomic-events/part-0001"),
              S3.Key.coerce(bucket + "run=2017-05-22-12-20-57/com.mailchimp/email_address_change/jsonschema/1-0-0/part-00001"),
              S3.Key.coerce(bucket + "run=2017-05-22-12-20-57/com.mailchimp/email_address_change/jsonschema/1-0-0/part-00002"),
              S3.Key.coerce(bucket + "run=2017-05-22-12-20-57/com.mailchimp/email_address_change/jsonschema/2-0-0/part-00001"),

              S3.Key.coerce(bucket + "run=2017-05-22-16-00-57/atomic-events/part-0000"),
              S3.Key.coerce(bucket + "run=2017-05-22-16-00-57/atomic-events/part-0001"),
              S3.Key.coerce(bucket + "run=2017-05-22-16-00-57/com.snowplowanalytics.snowplow/add_to_cart/jsonschema/1-0-0/part-00000"),
              S3.Key.coerce(bucket + "run=2017-05-22-16-00-57/com.snowplowanalytics.snowplow/add_to_cart/jsonschema/1-0-0/part-00001")
            ))

          case LoaderA.Get(key: String) =>
            cache.get(key)
          case LoaderA.Put(key: String, value: Option[S3.Key]) =>
            val _ = cache.put(key, value)
            ()

          case LoaderA.KeyExists(key) =>
            if (key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_1.json" ||
              key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_2.json" ||
              key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/add_to_cart_1.json")
              true
            else
              false

          case action =>
            throw new RuntimeException(s"Unexpected Action [$action]")
        }
      }
    }

    val shreddedGood = S3.Folder.coerce("s3://runfolder-test/shredded/good/")

    val expected = List(
      FullDiscovery(
        dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),
        2L,
        List(
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),"com.mailchimp","email_address_change",2,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_2.json")),
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),"com.mailchimp","email_address_change",1,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_1.json"))
        )
      ),

      FullDiscovery(
        dir("s3://runfolder-test/shredded/good/run=2017-05-22-16-00-57/"),
        2L,
        List(
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-16-00-57/"), "com.snowplowanalytics.snowplow","add_to_cart",1,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/add_to_cart_1.json"))
        )
      )
    )

    val discoveryTarget = DataDiscovery.InShreddedGood(shreddedGood)
    val result = DataDiscovery.discoverFull(discoveryTarget, Semver(0,11,0), "us-east-1", None)
    val endResult = result.foldMap(interpreter)

    endResult must beRight(expected)
  }

  def e2 = {

    case class RealWorld(requests: Int, waited: List[Long]) {
      def increment: RealWorld = this.copy(requests + 1)
    }

    type TestState[A] = State[RealWorld, A]

    def interpreter: LoaderA ~> TestState = new (LoaderA ~> TestState) {
      // S3 keys to return
      val initial = List(
        "run=2017-05-22-12-20-57/atomic-events/part-0000",
        "run=2017-05-22-12-20-57/atomic-events/part-0001",
        "run=2017-05-22-12-20-57/com.mailchimp/email_address_change/jsonschema/1-0-0/part-00001",
        "run=2017-05-22-12-20-57/com.mailchimp/email_address_change/jsonschema/1-0-0/part-00002",
        "run=2017-05-22-12-20-57/com.mailchimp/email_address_change/jsonschema/2-0-0/part-00001",

        "run=2017-05-22-16-00-57/atomic-events/part-0000",
        "run=2017-05-22-16-00-57/atomic-events/part-0001",
        "run=2017-05-22-16-00-57/com.snowplowanalytics.snowplow/add_to_cart/jsonschema/1-0-0/part-00000",
        "run=2017-05-22-16-00-57/com.snowplowanalytics.snowplow/add_to_cart/jsonschema/1-0-0/part-00001"
      )
      val second = "run=2017-05-22-16-00-57/com.snowplowanalytics.snowplow/geolocation/jsonschema/1-0-0/part-00001" :: initial
      val end = "run=2017-05-22-16-00-57/com.snowplowanalytics.snowplow/custom_context/jsonschema/1-0-0/part-00000" :: second

      private val cache = collection.mutable.HashMap.empty[String, Option[S3.Key]]

      def apply[A](effect: LoaderA[A]): TestState[A] = {
        effect match {
          case LoaderA.ListS3(bucket) =>
            State { (realWorld: RealWorld) =>
              if (realWorld.requests == 0) {
                (realWorld.increment, Right(initial.map(k => S3.Key.coerce(bucket + k))))
              } else if (realWorld.requests == 1) {
                (realWorld.increment, Right(second.map(k => S3.Key.coerce(bucket + k))))
              } else if (realWorld.requests == 2 || realWorld.requests == 3) {
                (realWorld.increment, Right(end.map(k => S3.Key.coerce(bucket + k))))
              } else {
                throw new RuntimeException("Invalid test state " + realWorld.toString)
              }
            }

          case LoaderA.Get(key: String) =>
            State.pure(cache.get(key))
          case LoaderA.Put(key: String, value: Option[S3.Key]) =>
            val _ = cache.put(key, value)
            State.pure(())

          case LoaderA.KeyExists(key) =>
            if (key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_1.json" ||
              key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_2.json" ||
              key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/add_to_cart_1.json" ||
              key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/custom_context_1.json" ||
              key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/geolocation_1.json")
              State.pure(true)
            else
              State.pure(false)

          case LoaderA.Sleep(timeout) =>
            State.modify((realWorld: RealWorld) => realWorld.copy(waited = timeout :: realWorld.waited))

          case action =>
            throw new RuntimeException(s"Unexpected Action [$action]")
        }
      }
    }

    val shreddedGood = S3.Folder.coerce("s3://runfolder-test/shredded/good/")

    val expected = List(
      FullDiscovery(
        dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),
        2L,
        List(
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),"com.mailchimp","email_address_change",2,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_2.json")),
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),"com.mailchimp","email_address_change",1,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_1.json"))
        )
      ),

      FullDiscovery(
        dir("s3://runfolder-test/shredded/good/run=2017-05-22-16-00-57/"),
        2L,
        List(
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-16-00-57/"), "com.snowplowanalytics.snowplow","add_to_cart",1,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/add_to_cart_1.json")),
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-16-00-57/"), "com.snowplowanalytics.snowplow","geolocation",1,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/geolocation_1.json")),
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-16-00-57/"), "com.snowplowanalytics.snowplow","custom_context",1,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.snowplowanalytics.snowplow/custom_context_1.json"))
        )
      )
    )

    val discoveryTarget = DataDiscovery.InShreddedGood(shreddedGood)
    val request = DataDiscovery.discoverFull(discoveryTarget, Semver(0,11,0), "us-east-1", None)
    val result = DataDiscovery.checkConsistency(request)
    val (endState, endResult) = result.foldMap(interpreter).run(RealWorld(0, Nil)).value

    val state = endState must beEqualTo(RealWorld(4, List(20000L, 20000L, 20000L)))
    val response = endResult must beRight(expected)
    state.and(response)
  }

  def e3 = {
    def interpreter: LoaderA ~> Id = new (LoaderA ~> Id) {
      def apply[A](effect: LoaderA[A]): Id[A] = {
        effect match {
          case LoaderA.ListS3(_) => Right(Nil)

          case action =>
            throw new RuntimeException(s"Unexpected Action [$action]")
        }
      }
    }

    val shreddedGood = S3.Folder.coerce("s3://runfolder-test/shredded/good/run=2017-08-21-19-18-20")

    val expected = DiscoveryError(List(NoDataFailure(shreddedGood)))

    val discoveryTarget = DataDiscovery.InSpecificFolder(shreddedGood)
    val result = DataDiscovery.discoverFull(discoveryTarget, Semver(0,11,0), "us-east-1", None)
    val endResult = result.foldMap(interpreter)

    endResult must beLeft(expected)
  }

  def e4 = {
    def interpreter: LoaderA ~> Id = new (LoaderA ~> Id) {
      def apply[A](effect: LoaderA[A]): Id[A] = {
        effect match {
          case LoaderA.ListS3(_) => Right(Nil)

          case action =>
            throw new RuntimeException(s"Unexpected Action [$action]")
        }
      }
    }

    val shreddedGood = S3.Folder.coerce("s3://runfolder-test/shredded/good")

    val expected = List.empty[DataDiscovery]

    // The only difference with e3
    val discoveryTarget = DataDiscovery.InShreddedGood(shreddedGood)
    val result = DataDiscovery.discoverFull(discoveryTarget, Semver(0,11,0), "us-east-1", None)
    val endResult = result.foldMap(interpreter)

    endResult must beRight(expected)
  }

  def e5 = {
    def interpreter: LoaderA ~> Id = new (LoaderA ~> Id) {
      private val cache = collection.mutable.HashMap.empty[String, Option[S3.Key]]
      def apply[A](effect: LoaderA[A]): Id[A] = {
        effect match {
          case LoaderA.ListS3(bucket) =>
            Right(List(
              S3.Key.coerce(bucket + "atomic-events/part-0000"),
              S3.Key.coerce(bucket + "atomic-events/part-0001"),
              S3.Key.coerce(bucket + "com.mailchimp/email_address_change/jsonschema/1-0-0/part-00001"),
              S3.Key.coerce(bucket + "com.mailchimp/email_address_change/jsonschema/1-0-0/part-00002"),
              S3.Key.coerce(bucket + "com.mailchimp/email_address_change/jsonschema/2-0-0/part-00001")
            ))

          case LoaderA.Get(key: String) =>
            cache.get(key)
          case LoaderA.Put(key: String, value: Option[S3.Key]) =>
            val _ = cache.put(key, value)
            ()

          case LoaderA.KeyExists(key) =>
            if (key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_1.json" ||
              key == "s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_2.json")
              true
            else
              false

          case action =>
            throw new RuntimeException(s"Unexpected Action [$action]")
        }
      }
    }

    val shreddedGood = S3.Folder.coerce("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/")

    val expected = List(
      FullDiscovery(
        dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),
        2L,
        List(
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),"com.mailchimp","email_address_change",2,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_2.json")),
          ShreddedType(
            Info(dir("s3://runfolder-test/shredded/good/run=2017-05-22-12-20-57/"),"com.mailchimp","email_address_change",1,Semver(0,11,0,None)),
            s3key("s3://snowplow-hosted-assets-us-east-1/4-storage/redshift-storage/jsonpaths/com.mailchimp/email_address_change_1.json"))
        )
      )
    )

    val discoveryTarget = DataDiscovery.InShreddedGood(shreddedGood)
    val result = DataDiscovery.discoverFull(discoveryTarget, Semver(0,11,0), "us-east-1", None)
    val endResult = result.foldMap(interpreter)

    endResult must beRight(expected)
  }
}
