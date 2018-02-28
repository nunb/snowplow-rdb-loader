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
package com.snowplowanalytics.snowplow.rdbloader.db

import Decoder._
import Entities.AtomicEventsDescription

import com.snowplowanalytics.snowplow.rdbloader.{ LoaderA, LoaderAction }
import com.snowplowanalytics.snowplow.rdbloader.loaders.Common.SqlString

object Schema {
  /** Get version of atomic.events table */
  def getEventsVersion(schema: String): LoaderAction[Option[AtomicEventsDescription]] = {
    val query =
      s"""
        |SELECT description FROM pg_catalog.pg_description
        | WHERE objoid =
        |   (SELECT oid
        |     FROM pg_class
        |     WHERE relname = 'events' and relnamespace =
        |       (SELECT oid
        |         FROM pg_catalog.pg_namespace
        |         WHERE nspname = '$schema'))
      """.stripMargin

    LoaderA.executeQuery[Option[AtomicEventsDescription]](SqlString.unsafeCoerce(query))
  }
}
