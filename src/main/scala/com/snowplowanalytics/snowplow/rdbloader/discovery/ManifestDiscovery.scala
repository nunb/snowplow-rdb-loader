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

import cats.data._
import cats.implicits._

import com.snowplowanalytics.iglu.core.SchemaKey

import com.snowplowanalytics.manifest.core.ProcessingManifest._
import com.snowplowanalytics.manifest.core.ProcessingManifest.ManifestError._


import com.snowplowanalytics.snowplow.rdbloader.LoaderError._
import com.snowplowanalytics.snowplow.rdbloader.config.Semver
import com.snowplowanalytics.snowplow.rdbloader.generated.ProjectMetadata


/**
  * Module containing logic for discovering `ShreddedType` through `ProcessingManifest`,
  * as opposed to `DataDiscovery`, where `ShreddedType` discovered directly from S3
  */
object ManifestDiscovery {

  /**
    * Protocol for exchanging payloads between Shredder and Loader
    * Should be common compile-time dependency
    */
  object RdbPayload {
    val ShreddedTypesKeys = "processed:shredder:types"
    val ShreddedTypesGet = (payload: Payload) => payload.set.get(ShreddedTypesKeys)
    val ShreddedTypesSet = (payload: Payload, types: Set[String]) => {
      val current = ShreddedTypesGet(payload).getOrElse(Set.empty[String])
      val updated = payload.set.updated(ShreddedTypesKeys, current ++ types)
      payload.copy(set = updated)
    }
  }

  val ShredderName: String = ProjectMetadata.shredderName
  val ShredderApp = Application(ShredderName, ProjectMetadata.shredderVersion, None, None)
  def getLoaderApp(id: String): Application =
    Application(ProjectMetadata.name, ProjectMetadata.version, Some(id), None)

  /**
    * Get list of unprocessed items (as `DataDiscovery`) from processing manifest
    * Unprocessed items are those that were processed by RDB Shredder and were NOT
    * processed by RDB Loader. Will return `ManifestFailure` if some item is blocked
    *
    * @param id storage target id to avoid "re-discovering" target
    * @param region AWS S3 Region for JSONPaths
    * @param jsonpathAssets user-owned bucket with JSONPaths
    * @return list of data discoveries (always with shredded types) or error
    *         if any error occurred
    */
  def discover(id: String, region: String, jsonpathAssets: Option[S3.Folder]): LoaderAction[List[DataDiscovery]] = {
    val itemsA = LoaderA.manifestDiscover(predicate(id))

    for {
      items       <- EitherT[Action, LoaderError, List[Item]](itemsA)
      discoveries <- items.traverse { item => for {
        infos     <- LoaderAction.liftE(parseItemPayload(item))
        discovery <- itemToDiscovery(region, jsonpathAssets, item.id, infos)
      } yield discovery }

    } yield discoveries.distinct  // .distinct is double-protection from double-loading
  }

  /** Primary predicate, deciding what `Item` should be loaded */
  def predicate(id: String)(item: Item): Boolean =
    processedBy(ShredderApp, item) && !processedBy(getLoaderApp(id), item)

  /** Get only shredder-"consumed" items */
  private[discovery] def parseItemPayload[F[_]: ManifestAction](item: Item): Either[LoaderError, List[ShreddedType.Info]] = {
    // At least New, Processing, Processed
    val shredderRecords = item.records.filter(_.app.name == ShredderName)
    val processedRecord = findProcessed(shredderRecords)

    processedRecord.map(_.flatMap(parseRecord)) match {
      case Some(either) => either.leftMap { error => DiscoveryError(ManifestFailure(error)) }
      case None =>
        // Impossible error-state if function is used on filtered `Item` before
        val message = s"Item [${item.id}] does not have 'Processed' step for $ShredderName"
        val error: ManifestError = Corrupted(InvalidContent(NonEmptyList.one(message)))
        DiscoveryError(ManifestFailure(error)).asLeft
    }
  }

  /** Find 'Processed' by shredder, but only if it has valid state */
  private def findProcessed(processedByShredder: List[Record]): Option[Either[ManifestError, Record]] = {
    processedByShredder.foldLeft(none[Record].asRight[ManifestError]) { (result, record) =>
      if (record.state.step == StepState.Processed) {
        val consumed = processedByShredder.exists(_.state.step == StepState.Processing)
        result match {
          case Right(None) if consumed => record.some.asRight
          case Right(None) =>
            val error = s"Processed record ${record.state.show} does not have corresponding 'Processing' state"
            Corrupted(InvalidContent(NonEmptyList.one(error))).asLeft
          case other => other
        }
      } else none.asRight
    }.sequence
  }

  /**
    * Extract information about shredded types (added to manifest by RDB Shredder)
    * from `Processed` `Record`
    *
    * @param record `Processed` by RDB Shredder record
    * @return list of shredded types discovered in this item (added by shredder)
    */
  def parseRecord(record: Record): Either[ManifestError, List[ShreddedType.Info]] = {
    val version = Semver.decodeSemver(record.app.version).toValidatedNel
    val types = record.payload.flatMap(RdbPayload.ShreddedTypesGet).getOrElse(Set.empty)
    val schemaKeys = types.toList.traverse { t => SchemaKey.fromUri(t) match {
      case Some(ss) => ss.validNel[String]
      case None => s"Key [$t] is invalid Iglu URI".invalidNel[SchemaKey]
    }}

    val base = S3.Folder
      .parse(record.id)
      .leftMap(message => s"Path [${record.id}] is not valid base for shredded type. $message")
      .toValidatedNel

    (version, schemaKeys, base).mapN { (v: Semver, k: List[SchemaKey], b: S3.Folder) =>
      k.map(kk => ShreddedType.Info(b, kk.vendor, kk.name, kk.version.model, v))
    } match {
      case Validated.Valid(infos) => infos.distinct.asRight
      case Validated.Invalid(errors) =>
        val state = record.state.show
        val details = errors.toList.mkString(", ")
        ManifestError.parseError(s"Cannot parse manifest record [$state] into ShreddedType.Info. $details").asLeft
    }
  }

  private def itemToDiscovery(region: String,
                              jsonpathAssets: Option[S3.Folder],
                              itemId: ItemId,
                              infos: List[ShreddedType.Info]): LoaderAction[DataDiscovery] = {
    val shreddedTypes = ShreddedType.discoverBatch(region, jsonpathAssets, infos)

    val base: Either[LoaderError, S3.Folder] =
      S3.Folder.parse(itemId)
        .leftMap(error => LoaderError.fromManifestError(ManifestError.parseError(error)))

    (LoaderAction.liftE(base), shreddedTypes).mapN(DataDiscovery(_, 0, _))
  }
}
