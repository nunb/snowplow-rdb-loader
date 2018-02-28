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

import com.snowplowanalytics.snowplow.rdbloader.config.Semver
import com.snowplowanalytics.snowplow.rdbloader.loaders.Common.SqlString

object EventsDdl {

  val registry: Map[Semver, (String) => SqlString] =
    Map(
      Semver(0,10,0) -> ten,
      Semver(0,9,0) -> nine,
      Semver(0,8,0) -> eight,
      Semver(0,7,0) -> seven
    )

  /**
    * Get DDL for atomic.events 0.10.0
    * @see https://github.com/snowplow/snowplow/commit/4bb64acb0ee5aec8a242338fa34ae3d7e93b6deb#diff-4cff77d747a382e71414088ad196028d
    */
  private def ten(tableName: String): SqlString = SqlString.unsafeCoerce(
    s"""
       |CREATE TEMPORARY TABLE $tableName (
       |	-- App
       |	app_id varchar(255) encode ZSTD,
       |	platform varchar(255) encode ZSTD,
       |
       |	-- Date/time
       |	etl_tstamp timestamp  encode ZSTD,
       |	collector_tstamp timestamp not null encode RAW,
       |	dvce_created_tstamp timestamp encode ZSTD,
       |
       |	-- Event
       |	event varchar(128) encode ZSTD,
       |	event_id char(36) not null unique encode ZSTD,
       |	txn_id int encode ZSTD,
       |
       |	-- Namespacing and versioning
       |	name_tracker varchar(128) encode ZSTD,
       |	v_tracker varchar(100) encode ZSTD,
       |	v_collector varchar(100) encode ZSTD not null,
       |	v_etl varchar(100) encode ZSTD not null,
       |
       |	-- User and visit
       |	user_id varchar(255) encode ZSTD,
       |	user_ipaddress varchar(128) encode ZSTD,
       |	user_fingerprint varchar(128) encode ZSTD,
       |	domain_userid varchar(128) encode ZSTD,
       |	domain_sessionidx int encode ZSTD,
       |	network_userid varchar(128) encode ZSTD,
       |
       |	-- Location
       |	geo_country char(2) encode ZSTD,
       |	geo_region char(2) encode ZSTD,
       |	geo_city varchar(75) encode ZSTD,
       |	geo_zipcode varchar(15) encode ZSTD,
       |	geo_latitude double precision encode ZSTD,
       |	geo_longitude double precision encode ZSTD,
       |	geo_region_name varchar(100) encode ZSTD,
       |
       |	-- IP lookups
       |	ip_isp varchar(100) encode ZSTD,
       |	ip_organization varchar(128) encode ZSTD,
       |	ip_domain varchar(128) encode ZSTD,
       |	ip_netspeed varchar(100) encode ZSTD,
       |
       |	-- Page
       |	page_url varchar(4096) encode ZSTD,
       |	page_title varchar(2000) encode ZSTD,
       |	page_referrer varchar(4096) encode ZSTD,
       |
       |	-- Page URL components
       |	page_urlscheme varchar(16) encode ZSTD,
       |	page_urlhost varchar(255) encode ZSTD,
       |	page_urlport int encode ZSTD,
       |	page_urlpath varchar(3000) encode ZSTD,
       |	page_urlquery varchar(6000) encode ZSTD,
       |	page_urlfragment varchar(3000) encode ZSTD,
       |
       |	-- Referrer URL components
       |	refr_urlscheme varchar(16) encode ZSTD,
       |	refr_urlhost varchar(255) encode ZSTD,
       |	refr_urlport int encode ZSTD,
       |	refr_urlpath varchar(6000) encode ZSTD,
       |	refr_urlquery varchar(6000) encode ZSTD,
       |	refr_urlfragment varchar(3000) encode ZSTD,
       |
       |	-- Referrer details
       |	refr_medium varchar(25) encode ZSTD,
       |	refr_source varchar(50) encode ZSTD,
       |	refr_term varchar(255) encode ZSTD,
       |
       |	-- Marketing
       |	mkt_medium varchar(255) encode ZSTD,
       |	mkt_source varchar(255) encode ZSTD,
       |	mkt_term varchar(255) encode ZSTD,
       |	mkt_content varchar(500) encode ZSTD,
       |	mkt_campaign varchar(255) encode ZSTD,
       |
       |	-- Custom structured event
       |	se_category varchar(1000) encode ZSTD,
       |	se_action varchar(1000) encode ZSTD,
       |	se_label varchar(4096) encode ZSTD,
       |	se_property varchar(1000) encode ZSTD,
       |	se_value double precision encode ZSTD,
       |
       |	-- Ecommerce
       |	tr_orderid varchar(255) encode ZSTD,
       |	tr_affiliation varchar(255) encode ZSTD,
       |	tr_total dec(18,2) encode ZSTD,
       |	tr_tax dec(18,2) encode ZSTD,
       |	tr_shipping dec(18,2) encode ZSTD,
       |	tr_city varchar(255) encode ZSTD,
       |	tr_state varchar(255) encode ZSTD,
       |	tr_country varchar(255) encode ZSTD,
       |	ti_orderid varchar(255) encode ZSTD,
       |	ti_sku varchar(255) encode ZSTD,
       |	ti_name varchar(255) encode ZSTD,
       |	ti_category varchar(255) encode ZSTD,
       |	ti_price dec(18,2) encode ZSTD,
       |	ti_quantity int encode ZSTD,
       |
       |	-- Page ping
       |	pp_xoffset_min int encode ZSTD,
       |	pp_xoffset_max int encode ZSTD,
       |	pp_yoffset_min int encode ZSTD,
       |	pp_yoffset_max int encode ZSTD,
       |
       |	-- User Agent
       |	useragent varchar(1000) encode ZSTD,
       |
       |	-- Browser
       |	br_name varchar(50) encode ZSTD,
       |	br_family varchar(50) encode ZSTD,
       |	br_version varchar(50) encode ZSTD,
       |	br_type varchar(50) encode ZSTD,
       |	br_renderengine varchar(50) encode ZSTD,
       |	br_lang varchar(255) encode ZSTD,
       |	br_features_pdf boolean encode ZSTD,
       |	br_features_flash boolean encode ZSTD,
       |	br_features_java boolean encode ZSTD,
       |	br_features_director boolean encode ZSTD,
       |	br_features_quicktime boolean encode ZSTD,
       |	br_features_realplayer boolean encode ZSTD,
       |	br_features_windowsmedia boolean encode ZSTD,
       |	br_features_gears boolean encode ZSTD,
       |	br_features_silverlight boolean encode ZSTD,
       |	br_cookies boolean encode ZSTD,
       |	br_colordepth varchar(12) encode ZSTD,
       |	br_viewwidth int encode ZSTD,
       |	br_viewheight int encode ZSTD,
       |
       |	-- Operating System
       |	os_name varchar(50) encode ZSTD,
       |	os_family varchar(50)  encode ZSTD,
       |	os_manufacturer varchar(50)  encode ZSTD,
       |	os_timezone varchar(255)  encode ZSTD,
       |
       |	-- Device/Hardware
       |	dvce_type varchar(50)  encode ZSTD,
       |	dvce_ismobile boolean encode ZSTD,
       |	dvce_screenwidth int encode ZSTD,
       |	dvce_screenheight int encode ZSTD,
       |
       |	-- Document
       |	doc_charset varchar(128) encode ZSTD,
       |	doc_width int encode ZSTD,
       |	doc_height int encode ZSTD,
       |
       |	-- Currency
       |	tr_currency char(3) encode ZSTD,
       |	tr_total_base dec(18, 2) encode ZSTD,
       |	tr_tax_base dec(18, 2) encode ZSTD,
       |	tr_shipping_base dec(18, 2) encode ZSTD,
       |	ti_currency char(3) encode ZSTD,
       |	ti_price_base dec(18, 2) encode ZSTD,
       |	base_currency char(3) encode ZSTD,
       |
       |	-- Geolocation
       |	geo_timezone varchar(64) encode ZSTD,
       |
       |	-- Click ID
       |	mkt_clickid varchar(128) encode ZSTD,
       |	mkt_network varchar(64) encode ZSTD,
       |
       |	-- ETL tags
       |	etl_tags varchar(500) encode ZSTD,
       |
       |	-- Time event was sent
       |	dvce_sent_tstamp timestamp encode ZSTD,
       |
       |	-- Referer
       |	refr_domain_userid varchar(128) encode ZSTD,
       |	refr_dvce_tstamp timestamp encode ZSTD,
       |
       |	-- Session ID
       |	domain_sessionid char(128) encode ZSTD,
       |
       |	-- Derived timestamp
       |	derived_tstamp timestamp encode ZSTD,
       |
       |	-- Event schema
       |	event_vendor varchar(1000) encode ZSTD,
       |	event_name varchar(1000) encode ZSTD,
       |	event_format varchar(128) encode ZSTD,
       |	event_version varchar(128) encode ZSTD,
       |
       |	-- Event fingerprint
       |	event_fingerprint varchar(128) encode ZSTD,
       |
       |	-- True timestamp
       |	true_tstamp timestamp encode ZSTD
       |)
       |DISTSTYLE KEY
       |DISTKEY (event_id)
       |SORTKEY (collector_tstamp);
    """.stripMargin)

  /**
    * Get DDL for atomic.events 0.9.0
    * @see https://github.com/snowplow/snowplow/blob/b189cb1ffaa55ad09f009994fa1fbe7420911fab/4-storage/redshift-storage/sql/atomic-def.sql
    */
  private def nine(tableName: String): SqlString = SqlString.unsafeCoerce(
    s"""
      |CREATE TEMPORARY TABLE $tableName (
      |	-- App
      |	app_id varchar(255) encode text255,
      |	platform varchar(255) encode text255,
      |
      |	-- Date/time
      |	etl_tstamp timestamp,
      |	collector_tstamp timestamp not null,
      |	dvce_created_tstamp timestamp,
      |
      |	-- Event
      |	event varchar(128) encode text255,
      |	event_id char(36) not null unique,
      |	txn_id int,
      |
      |	-- Namespacing and versioning
      |	name_tracker varchar(128) encode text255,
      |	v_tracker varchar(100) encode text255,
      |	v_collector varchar(100) encode text255 not null,
      |	v_etl varchar(100) encode text255 not null,
      |
      |	-- User and visit
      |	user_id varchar(255) encode runlength,
      |	user_ipaddress varchar(45) encode runlength,
      |	user_fingerprint varchar(50) encode runlength,
      |	domain_userid varchar(36) encode runlength,
      |	domain_sessionidx smallint,
      |	network_userid varchar(38),
      |
      |	-- Location
      |	geo_country char(2) encode runlength,
      |	geo_region char(2) encode runlength,
      |	geo_city varchar(75) encode runlength,
      |	geo_zipcode varchar(15) encode runlength,
      |	geo_latitude double precision encode runlength,
      |	geo_longitude double precision encode runlength,
      |	geo_region_name varchar(100) encode runlength,
      |
      |	-- IP lookups
      |	ip_isp varchar(100) encode runlength,
      |	ip_organization varchar(100) encode runlength,
      |	ip_domain varchar(100) encode runlength,
      |	ip_netspeed varchar(100) encode runlength,
      |
      |	-- Page
      |	page_url varchar(4096),
      |	page_title varchar(2000),
      |	page_referrer varchar(4096),
      |
      |	-- Page URL components
      |	page_urlscheme varchar(16) encode text255,
      |	page_urlhost varchar(255) encode text255,
      |	page_urlport int,
      |	page_urlpath varchar(3000) encode text32k,
      |	page_urlquery varchar(6000),
      |	page_urlfragment varchar(3000),
      |
      |	-- Referrer URL components
      |	refr_urlscheme varchar(16) encode text255,
      |	refr_urlhost varchar(255) encode text255,
      |	refr_urlport int,
      |	refr_urlpath varchar(6000) encode text32k,
      |	refr_urlquery varchar(6000),
      |	refr_urlfragment varchar(3000),
      |
      |	-- Referrer details
      |	refr_medium varchar(25) encode text255,
      |	refr_source varchar(50) encode text255,
      |	refr_term varchar(255) encode raw,
      |
      |	-- Marketing
      |	mkt_medium varchar(255) encode text255,
      |	mkt_source varchar(255) encode text255,
      |	mkt_term varchar(255) encode raw,
      |	mkt_content varchar(500) encode raw,
      |	mkt_campaign varchar(255) encode text32k,
      |
      |	-- Custom structured event
      |	se_category varchar(1000) encode text32k,
      |	se_action varchar(1000) encode text32k,
      |	se_label varchar(1000) encode text32k,
      |	se_property varchar(1000) encode text32k,
      |	se_value double precision,
      |
      |	-- Ecommerce
      |	tr_orderid varchar(255) encode raw,
      |	tr_affiliation varchar(255) encode text255,
      |	tr_total dec(18,2),
      |	tr_tax dec(18,2),
      |	tr_shipping dec(18,2),
      |	tr_city varchar(255) encode text32k,
      |	tr_state varchar(255) encode text32k,
      |	tr_country varchar(255) encode text32k,
      |	ti_orderid varchar(255) encode raw,
      |	ti_sku varchar(255) encode text32k,
      |	ti_name varchar(255) encode text32k,
      |	ti_category varchar(255) encode text255,
      |	ti_price dec(18,2),
      |	ti_quantity int,
      |
      |	-- Page ping
      |	pp_xoffset_min integer,
      |	pp_xoffset_max integer,
      |	pp_yoffset_min integer,
      |	pp_yoffset_max integer,
      |
      |	-- User Agent
      |	useragent varchar(1000) encode text32k,
      |
      |	-- Browser
      |	br_name varchar(50) encode text255,
      |	br_family varchar(50) encode text255,
      |	br_version varchar(50) encode text255,
      |	br_type varchar(50) encode text255,
      |	br_renderengine varchar(50) encode text255,
      |	br_lang varchar(255) encode text255,
      |	br_features_pdf boolean,
      |	br_features_flash boolean,
      |	br_features_java boolean,
      |	br_features_director boolean,
      |	br_features_quicktime boolean,
      |	br_features_realplayer boolean,
      |	br_features_windowsmedia boolean,
      |	br_features_gears boolean ,
      |	br_features_silverlight boolean,
      |	br_cookies boolean,
      |	br_colordepth varchar(12) encode text255,
      |	br_viewwidth integer,
      |	br_viewheight integer,
      |
      |	-- Operating System
      |	os_name varchar(50) encode text255,
      |	os_family varchar(50)  encode text255,
      |	os_manufacturer varchar(50)  encode text255,
      |	os_timezone varchar(255)  encode text255,
      |
      |	-- Device/Hardware
      |	dvce_type varchar(50)  encode text255,
      |	dvce_ismobile boolean,
      |	dvce_screenwidth integer,
      |	dvce_screenheight integer,
      |
      |	-- Document
      |	doc_charset varchar(128) encode text255,
      |	doc_width integer,
      |	doc_height integer,
      |
      |	-- Currency
      |	tr_currency char(3) encode bytedict,
      |	tr_total_base dec(18, 2),
      |	tr_tax_base dec(18, 2),
      |	tr_shipping_base dec(18, 2),
      |	ti_currency char(3) encode bytedict,
      |	ti_price_base dec(18, 2),
      |	base_currency char(3) encode bytedict,
      |
      |	-- Geolocation
      |	geo_timezone varchar(64) encode text255,
      |
      |	-- Click ID
      |	mkt_clickid varchar(128) encode raw,
      |	mkt_network varchar(64) encode text255,
      |
      |	-- ETL tags
      |	etl_tags varchar(500) encode lzo,
      |
      |	-- Time event was sent
      |	dvce_sent_tstamp timestamp,
      |
      |	-- Referer
      |	refr_domain_userid varchar(36),
      |	refr_dvce_tstamp timestamp,
      |
      |	-- Session ID
      |	domain_sessionid char(36) encode raw,
      |
      |	-- Derived timestamp
      |	derived_tstamp timestamp,
      |
      |	-- Event schema
      |	event_vendor varchar(1000) encode lzo,
      |	event_name varchar(1000) encode lzo,
      |	event_format varchar(128) encode lzo,
      |	event_version varchar(128) encode lzo,
      |
      |	-- Event fingerprint
      |	event_fingerprint varchar(128) encode lzo,
      |
      |	-- True timestamp
      |	true_tstamp timestamp
      |)
      |DISTSTYLE KEY
      |DISTKEY (event_id)
      |SORTKEY (collector_tstamp);
    """.stripMargin)

  /**
    * Get DDL for atomic.events 0.8.0
    * @see https://github.com/snowplow/snowplow/blob/b189cb1ffaa55ad09f009994fa1fbe7420911fab/4-storage/redshift-storage/sql/atomic-def.sql
    */
  private def eight(tableName: String): SqlString = SqlString.unsafeCoerce(
    s"""
      |CREATE TEMPORARY TABLE $tableName (
      |	-- App
      |	app_id varchar(255) encode text255,
      |	platform varchar(255) encode text255,
      |	-- Date/time
      |	etl_tstamp timestamp,
      |	collector_tstamp timestamp not null,
      |	dvce_created_tstamp timestamp,
      |	-- Event
      |	event varchar(128) encode text255,
      |	event_id char(36) not null unique,
      |	txn_id int,
      |	-- Namespacing and versioning
      |	name_tracker varchar(128) encode text255,
      |	v_tracker varchar(100) encode text255,
      |	v_collector varchar(100) encode text255 not null,
      |	v_etl varchar(100) encode text255 not null,
      |	-- User and visit
      |	user_id varchar(255) encode runlength,
      |	user_ipaddress varchar(45) encode runlength,
      |	user_fingerprint varchar(50) encode runlength,
      |	domain_userid varchar(36) encode runlength,
      |	domain_sessionidx smallint,
      |	network_userid varchar(38),
      |	-- Location
      |	geo_country char(2) encode runlength,
      |	geo_region char(2) encode runlength,
      |	geo_city varchar(75) encode runlength,
      |	geo_zipcode varchar(15) encode runlength,
      |	geo_latitude double precision encode runlength,
      |	geo_longitude double precision encode runlength,
      |	geo_region_name varchar(100) encode runlength,
      |	-- IP lookups
      |	ip_isp varchar(100) encode runlength,
      |	ip_organization varchar(100) encode runlength,
      |	ip_domain varchar(100) encode runlength,
      |	ip_netspeed varchar(100) encode runlength,
      |	-- Page
      |	page_url varchar(4096),
      |	page_title varchar(2000),
      |	page_referrer varchar(4096),
      |	-- Page URL components
      |	page_urlscheme varchar(16) encode text255,
      |	page_urlhost varchar(255) encode text255,
      |	page_urlport int,
      |	page_urlpath varchar(3000) encode text32k,
      |	page_urlquery varchar(6000),
      |	page_urlfragment varchar(3000),
      |	-- Referrer URL components
      |	refr_urlscheme varchar(16) encode text255,
      |	refr_urlhost varchar(255) encode text255,
      |	refr_urlport int,
      |	refr_urlpath varchar(6000) encode text32k,
      |	refr_urlquery varchar(6000),
      |	refr_urlfragment varchar(3000),
      |	-- Referrer details
      |	refr_medium varchar(25) encode text255,
      |	refr_source varchar(50) encode text255,
      |	refr_term varchar(255) encode raw,
      |	-- Marketing
      |	mkt_medium varchar(255) encode text255,
      |	mkt_source varchar(255) encode text255,
      |	mkt_term varchar(255) encode raw,
      |	mkt_content varchar(500) encode raw,
      |	mkt_campaign varchar(255) encode text32k,
      |	-- Custom structured event
      |	se_category varchar(1000) encode text32k,
      |	se_action varchar(1000) encode text32k,
      |	se_label varchar(1000) encode text32k,
      |	se_property varchar(1000) encode text32k,
      |	se_value double precision,
      |	-- Ecommerce
      |	tr_orderid varchar(255) encode raw,
      |	tr_affiliation varchar(255) encode text255,
      |	tr_total dec(18,2),
      |	tr_tax dec(18,2),
      |	tr_shipping dec(18,2),
      |	tr_city varchar(255) encode text32k,
      |	tr_state varchar(255) encode text32k,
      |	tr_country varchar(255) encode text32k,
      |	ti_orderid varchar(255) encode raw,
      |	ti_sku varchar(255) encode text32k,
      |	ti_name varchar(255) encode text32k,
      |	ti_category varchar(255) encode text255,
      |	ti_price dec(18,2),
      |	ti_quantity int,
      |	-- Page ping
      |	pp_xoffset_min integer,
      |	pp_xoffset_max integer,
      |	pp_yoffset_min integer,
      |	pp_yoffset_max integer,
      |	-- User Agent
      |	useragent varchar(1000) encode text32k,
      |	-- Browser
      |	br_name varchar(50) encode text255,
      |	br_family varchar(50) encode text255,
      |	br_version varchar(50) encode text255,
      |	br_type varchar(50) encode text255,
      |	br_renderengine varchar(50) encode text255,
      |	br_lang varchar(255) encode text255,
      |	br_features_pdf boolean,
      |	br_features_flash boolean,
      |	br_features_java boolean,
      |	br_features_director boolean,
      |	br_features_quicktime boolean,
      |	br_features_realplayer boolean,
      |	br_features_windowsmedia boolean,
      |	br_features_gears boolean ,
      |	br_features_silverlight boolean,
      |	br_cookies boolean,
      |	br_colordepth varchar(12) encode text255,
      |	br_viewwidth integer,
      |	br_viewheight integer,
      |	-- Operating System
      |	os_name varchar(50) encode text255,
      |	os_family varchar(50)  encode text255,
      |	os_manufacturer varchar(50)  encode text255,
      |	os_timezone varchar(255)  encode text255,
      |	-- Device/Hardware
      |	dvce_type varchar(50)  encode text255,
      |	dvce_ismobile boolean,
      |	dvce_screenwidth integer,
      |	dvce_screenheight integer,
      |	-- Document
      |	doc_charset varchar(128) encode text255,
      |	doc_width integer,
      |	doc_height integer,
      |
      |	-- Currency
      |	tr_currency char(3) encode bytedict,
      |	tr_total_base dec(18, 2),
      |	tr_tax_base dec(18, 2),
      |	tr_shipping_base dec(18, 2),
      |	ti_currency char(3) encode bytedict,
      |	ti_price_base dec(18, 2),
      |	base_currency char(3) encode bytedict,
      |
      |	-- Geolocation
      |	geo_timezone varchar(64) encode text255,
      |
      |	-- Click ID
      |	mkt_clickid varchar(128) encode raw,
      |	mkt_network varchar(64) encode text255,
      |
      |	-- ETL tags
      |	etl_tags varchar(500) encode lzo,
      |
      |	-- Time event was sent
      |	dvce_sent_tstamp timestamp,
      |
      |	-- Referer
      |	refr_domain_userid varchar(36),
      |	refr_dvce_tstamp timestamp,
      |
      |	-- Session ID
      |	domain_sessionid char(36) encode raw,
      |
      |	-- Derived timestamp
      |	derived_tstamp timestamp,
      |
      |	-- Event schema
      |	event_vendor varchar(1000) encode lzo,
      |	event_name varchar(1000) encode lzo,
      |	event_format varchar(128) encode lzo,
      |	event_version varchar(128) encode lzo,
      |
      |	-- Event fingerprint
      |	event_fingerprint varchar(128) encode lzo,
      |
      |	-- True timestamp
      |	true_tstamp timestamp,
      |
      |	CONSTRAINT event_id_080_pk PRIMARY KEY(event_id)
      |)
      |DISTSTYLE KEY
      |DISTKEY (event_id)
      |SORTKEY (collector_tstamp);
    """.stripMargin)

  /**
    * Get DDL for atomic.events 0.7.0
    * @see https://github.com/snowplow/snowplow/blob/badf32e8a854dc043b1769753f6a05f1074cf993/4-storage/redshift-storage/sql/atomic-def.sql
    */
  private def seven(tableName: String): SqlString = SqlString.unsafeCoerce(
    s"""
      |CREATE TEMPORARY TABLE $tableName (
      |	-- App
      |	app_id varchar(255) encode text255,
      |	platform varchar(255) encode text255,
      |
      |	-- Date/time
      |	etl_tstamp timestamp,
      |	collector_tstamp timestamp not null,
      |	dvce_created_tstamp timestamp,
      |
      |	-- Event
      |	event varchar(128) encode text255,
      |	event_id char(36) not null unique,
      |	txn_id int,
      |
      |	-- Namespacing and versioning
      |	name_tracker varchar(128) encode text255,
      |	v_tracker varchar(100) encode text255,
      |	v_collector varchar(100) encode text255 not null,
      |	v_etl varchar(100) encode text255 not null,
      |
      |	-- User and visit
      |	user_id varchar(255) encode runlength,
      |	user_ipaddress varchar(45) encode runlength,
      |	user_fingerprint varchar(50) encode runlength,
      |	domain_userid varchar(36) encode runlength,
      |	domain_sessionidx smallint,
      |	network_userid varchar(38),
      |
      |	-- Location
      |	geo_country char(2) encode runlength,
      |	geo_region char(2) encode runlength,
      |	geo_city varchar(75) encode runlength,
      |	geo_zipcode varchar(15) encode runlength,
      |	geo_latitude double precision encode runlength,
      |	geo_longitude double precision encode runlength,
      |	geo_region_name varchar(100) encode runlength,
      |
      |	-- IP lookups
      |	ip_isp varchar(100) encode runlength,
      |	ip_organization varchar(100) encode runlength,
      |	ip_domain varchar(100) encode runlength,
      |	ip_netspeed varchar(100) encode runlength,
      |
      |	-- Page
      |	page_url varchar(4096),
      |	page_title varchar(2000),
      |	page_referrer varchar(4096),
      |
      |	-- Page URL components
      |	page_urlscheme varchar(16) encode text255,
      |	page_urlhost varchar(255) encode text255,
      |	page_urlport int,
      |	page_urlpath varchar(3000) encode text32k,
      |	page_urlquery varchar(6000),
      |	page_urlfragment varchar(3000),
      |
      |	-- Referrer URL components
      |	refr_urlscheme varchar(16) encode text255,
      |	refr_urlhost varchar(255) encode text255,
      |	refr_urlport int,
      |	refr_urlpath varchar(6000) encode text32k,
      |	refr_urlquery varchar(6000),
      |	refr_urlfragment varchar(3000),
      |
      |	-- Referrer details
      |	refr_medium varchar(25) encode text255,
      |	refr_source varchar(50) encode text255,
      |	refr_term varchar(255) encode raw,
      |
      |	-- Marketing
      |	mkt_medium varchar(255) encode text255,
      |	mkt_source varchar(255) encode text255,
      |	mkt_term varchar(255) encode raw,
      |	mkt_content varchar(500) encode raw,
      |	mkt_campaign varchar(255) encode text32k,
      |
      |	-- Custom contexts
      |	contexts varchar(15000) encode lzo,
      |
      |	-- Custom structured event
      |	se_category varchar(1000) encode text32k,
      |	se_action varchar(1000) encode text32k,
      |	se_label varchar(1000) encode text32k,
      |	se_property varchar(1000) encode text32k,
      |	se_value double precision,
      |
      |	-- Custom unstructured event
      |	unstruct_event varchar(15000) encode lzo,
      |
      |	-- Ecommerce
      |	tr_orderid varchar(255) encode raw,
      |	tr_affiliation varchar(255) encode text255,
      |	tr_total dec(18,2),
      |	tr_tax dec(18,2),
      |	tr_shipping dec(18,2),
      |	tr_city varchar(255) encode text32k,
      |	tr_state varchar(255) encode text32k,
      |	tr_country varchar(255) encode text32k,
      |	ti_orderid varchar(255) encode raw,
      |	ti_sku varchar(255) encode text32k,
      |	ti_name varchar(255) encode text32k,
      |	ti_category varchar(255) encode text255,
      |	ti_price dec(18,2),
      |	ti_quantity int,
      |
      |	-- Page ping
      |	pp_xoffset_min integer,
      |	pp_xoffset_max integer,
      |	pp_yoffset_min integer,
      |	pp_yoffset_max integer,
      |
      |	-- User Agent
      |	useragent varchar(1000) encode text32k,
      |
      |	-- Browser
      |	br_name varchar(50) encode text255,
      |	br_family varchar(50) encode text255,
      |	br_version varchar(50) encode text255,
      |	br_type varchar(50) encode text255,
      |	br_renderengine varchar(50) encode text255,
      |	br_lang varchar(255) encode text255,
      |	br_features_pdf boolean,
      |	br_features_flash boolean,
      |	br_features_java boolean,
      |	br_features_director boolean,
      |	br_features_quicktime boolean,
      |	br_features_realplayer boolean,
      |	br_features_windowsmedia boolean,
      |	br_features_gears boolean ,
      |	br_features_silverlight boolean,
      |	br_cookies boolean,
      |	br_colordepth varchar(12) encode text255,
      |	br_viewwidth integer,
      |	br_viewheight integer,
      |
      |	-- Operating System
      |	os_name varchar(50) encode text255,
      |	os_family varchar(50)  encode text255,
      |	os_manufacturer varchar(50)  encode text255,
      |	os_timezone varchar(255)  encode text255,
      |
      |	-- Device/Hardware
      |	dvce_type varchar(50)  encode text255,
      |	dvce_ismobile boolean,
      |	dvce_screenwidth integer,
      |	dvce_screenheight integer,
      |
      |	-- Document
      |	doc_charset varchar(128) encode text255,
      |	doc_width integer,
      |	doc_height integer,
      |
      |	-- Currency
      |	tr_currency char(3) encode bytedict,
      |	tr_total_base dec(18, 2),
      |	tr_tax_base dec(18, 2),
      |	tr_shipping_base dec(18, 2),
      |	ti_currency char(3) encode bytedict,
      |	ti_price_base dec(18, 2),
      |	base_currency char(3) encode bytedict,
      |
      |	-- Geolocation
      |	geo_timezone varchar(64) encode text255,
      |
      |	-- Click ID
      |	mkt_clickid varchar(128) encode raw,
      |	mkt_network varchar(64) encode text255,
      |
      |	-- ETL tags
      |	etl_tags varchar(500) encode lzo,
      |
      |	-- Time event was sent
      |	dvce_sent_tstamp timestamp,
      |
      |	-- Referer
      |	refr_domain_userid varchar(36),
      |	refr_dvce_tstamp timestamp,
      |
      |	-- Derived contexts
      |	derived_contexts varchar(15000) encode lzo,
      |
      |	-- Session ID
      |	domain_sessionid char(36) encode raw,
      |
      |	-- Derived timestamp
      |	derived_tstamp timestamp,
      |
      |	-- Event schema
      |	event_vendor varchar(1000) encode lzo,
      |	event_name varchar(1000) encode lzo,
      |	event_format varchar(128) encode lzo,
      |	event_version varchar(128) encode lzo,
      |
      |	-- Event fingerprint
      |	event_fingerprint varchar(128) encode lzo,
      |
      |	-- True timestamp
      |	true_tstamp timestamp
      |)
      |DISTSTYLE KEY
      |DISTKEY (event_id)
      |SORTKEY (collector_tstamp);
    """.stripMargin)
}
