/*
 * Copyright (c) 2012-2018 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package com.snowplowanalytics.snowplow.storage.spark

import cats.implicits._

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder

import com.snowplowanalytics.manifest.core.ProcessingManifest._
import com.snowplowanalytics.manifest.dynamodb.DynamoDbManifest
import com.snowplowanalytics.snowplow.rdbloader.generated.ProjectMetadata

object DynamodbManifest {

  type ManifestFailure[A] = Either[ManifestError, A]

  val ShredJobApplication = Application(ProjectMetadata.name, ProjectMetadata.version, None)

  val ShreddedTypesKeys = "processed:shredder:types"

  def initialize(tableName: String) = {
    val client = AmazonDynamoDBClientBuilder.standard().build()
    DynamoDbManifest[ManifestFailure](client, tableName)
  }
}
