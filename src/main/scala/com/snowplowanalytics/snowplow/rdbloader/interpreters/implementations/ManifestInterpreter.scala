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
package com.snowplowanalytics.snowplow.rdbloader.interpreters.implementations

import cats.implicits._

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder

import com.snowplowanalytics.manifest.core.ProcessingManifest
import com.snowplowanalytics.manifest.core.ProcessingManifest.{Item, ManifestError}
import com.snowplowanalytics.manifest.dynamodb.DynamoDbManifest

import com.snowplowanalytics.snowplow.rdbloader.LoaderError
import com.snowplowanalytics.snowplow.rdbloader.config.StorageTarget.ProcessingManifestConfig

import scala.util.control.NonFatal

object ManifestInterpreter {

  type ManifestE[A] = Either[ManifestError, A]

  def initialize(manifestConfig: Option[ProcessingManifestConfig], emrRegion: String): Either[LoaderError, Option[DynamoDbManifest[ManifestE]]] = {
    try {
      manifestConfig.map { config =>
        val dynamodbClient = AmazonDynamoDBClientBuilder.standard().withRegion(emrRegion).build()
        DynamoDbManifest[ManifestE](dynamodbClient, config.amazonDynamoDb.tableName)
      }.asRight[LoaderError]
    } catch {
      case NonFatal(e) =>
        val error: LoaderError =
          LoaderError.LoaderLocalError(s"Cannot initialize DynamoDB client for processing manifest, ${e.toString}")
        error.asLeft[Option[DynamoDbManifest[ManifestE]]]
    }
  }

  def getUnprocessed(manifest: ProcessingManifest[ManifestE],
                     predicate: Item => Boolean): Either[LoaderError, List[Item]] = {
    manifest.unprocessed(predicate).leftMap(LoaderError.fromManifestError)
  }
}
