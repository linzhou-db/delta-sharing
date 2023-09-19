/*
 * Copyright (2021) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.delta.sharing.client.util

import java.util.concurrent.TimeUnit

import org.apache.hadoop.conf.Configuration
import org.apache.spark.network.util.JavaUtils
import org.apache.spark.sql.internal.SQLConf

object ConfUtils {

  val NUM_RETRIES_CONF = "spark.delta.sharing.network.numRetries"
  val NUM_RETRIES_DEFAULT = 10

  val MAX_RETRY_DURATION_CONF = "spark.delta.sharing.network.maxRetryDuration"
  val MAX_RETRY_DURATION_DEFAULT_MILLIS = 10L * 60L* 1000L /* 10 mins */

  val TIMEOUT_CONF = "spark.delta.sharing.network.timeout"
  val TIMEOUT_DEFAULT = "320s"

  val MAX_CONNECTION_CONF = "spark.delta.sharing.network.maxConnections"
  val MAX_CONNECTION_DEFAULT = 64

  val SSL_TRUST_ALL_CONF = "spark.delta.sharing.network.sslTrustAll"
  val SSL_TRUST_ALL_DEFAULT = "false"

  val PROFILE_PROVIDER_CLASS_CONF = "spark.delta.sharing.profile.provider.class"
  val PROFILE_PROVIDER_CLASS_DEFAULT = "io.delta.sharing.client.DeltaSharingFileProfileProvider"

  val CLIENT_CLASS_CONF = "spark.delta.sharing.client.class"
  val CLIENT_CLASS_DEFAULT = "io.delta.sharing.client.DeltaSharingRestClient"

  val JSON_PREDICATE_CONF = "spark.delta.sharing.jsonPredicateHints.enabled"
  val JSON_PREDICATE_DEFAULT = "true"

  val JSON_PREDICATE_V2_CONF = "spark.delta.sharing.jsonPredicateV2Hints.enabled"
  val JSON_PREDICATE_V2_DEFAULT = "false"

  val QUERY_PAGINATION_ENABLED_CONF = "spark.delta.sharing.queryPagination.enabled"
  val QUERY_PAGINATION_ENABLED_DEFAULT = "false"

  val MAX_FILES_CONF = "spark.delta.sharing.maxFilesPerQueryRequest"
  val MAX_FILES_DEFAULT = 100000

  val IGNORE_UNPARSED_ACTIONS = "spark.delta.sharing.ignoreUnparsedActions"
  val IGNORE_UNPARSED_ACTIONS_DEFAULT = false

  val QUERY_TABLE_VERSION_INTERVAL_SECONDS =
    "spark.delta.sharing.streaming.queryTableVersionIntervalSeconds"
  val QUERY_TABLE_VERSION_INTERVAL_SECONDS_DEFAULT = "30s"

  def numRetries(conf: Configuration): Int = {
    val numRetries = conf.getInt(NUM_RETRIES_CONF, NUM_RETRIES_DEFAULT)
    validateNonNeg(numRetries, NUM_RETRIES_CONF)
    numRetries
  }

  def numRetries(conf: SQLConf): Int = {
    val numRetries = conf.getConfString(NUM_RETRIES_CONF, NUM_RETRIES_DEFAULT.toString).toInt
    validateNonNeg(numRetries, NUM_RETRIES_CONF)
    numRetries
  }

  def maxRetryDurationMillis(conf: Configuration): Long = {
    val maxDur = conf.getLong(MAX_RETRY_DURATION_CONF, MAX_RETRY_DURATION_DEFAULT_MILLIS)
    validateNonNeg(maxDur, MAX_RETRY_DURATION_CONF)
    maxDur
  }

  def maxRetryDurationMillis(conf: SQLConf): Long = {
    val maxDur =
      conf.getConfString(MAX_RETRY_DURATION_CONF, MAX_RETRY_DURATION_DEFAULT_MILLIS.toString).toLong
    validateNonNeg(maxDur, MAX_RETRY_DURATION_CONF)
    maxDur
  }

  def timeoutInSeconds(conf: Configuration): Int = {
    val timeoutStr = conf.get(TIMEOUT_CONF, TIMEOUT_DEFAULT)
    toTimeInSeconds(timeoutStr, TIMEOUT_CONF)
  }

  def timeoutInSeconds(conf: SQLConf): Int = {
    val timeoutStr = conf.getConfString(TIMEOUT_CONF, TIMEOUT_DEFAULT)
    toTimeInSeconds(timeoutStr, TIMEOUT_CONF)
  }

  def maxConnections(conf: Configuration): Int = {
    val maxConn = conf.getInt(MAX_CONNECTION_CONF, MAX_CONNECTION_DEFAULT)
    validateNonNeg(maxConn, MAX_CONNECTION_CONF)
    maxConn
  }

  def sslTrustAll(conf: SQLConf): Boolean = {
    conf.getConfString(SSL_TRUST_ALL_CONF, SSL_TRUST_ALL_DEFAULT).toBoolean
  }

  def profileProviderClass(conf: SQLConf): String = {
    conf.getConfString(PROFILE_PROVIDER_CLASS_CONF, PROFILE_PROVIDER_CLASS_DEFAULT)
  }

  def clientClass(conf: SQLConf): String = {
    conf.getConfString(CLIENT_CLASS_CONF, CLIENT_CLASS_DEFAULT)
  }

  def jsonPredicatesEnabled(conf: SQLConf): Boolean = {
    conf.getConfString(JSON_PREDICATE_CONF, JSON_PREDICATE_DEFAULT).toBoolean
  }

  def jsonPredicatesV2Enabled(conf: SQLConf): Boolean = {
    conf.getConfString(JSON_PREDICATE_V2_CONF, JSON_PREDICATE_V2_DEFAULT).toBoolean
  }

  def queryTablePaginationEnabled(conf: SQLConf): Boolean = {
    conf.getConfString(QUERY_PAGINATION_ENABLED_CONF, QUERY_PAGINATION_ENABLED_DEFAULT).toBoolean
  }

  def maxFilesPerQueryRequest(conf: SQLConf): Int = {
    val maxFiles = conf.getConfString(MAX_FILES_CONF, MAX_FILES_DEFAULT.toString).toInt
    validatePositive(maxFiles, MAX_FILES_CONF)
    maxFiles
  }

  def ignoreUnparsedActions(conf: SQLConf): Boolean = {
    conf.getConfString(IGNORE_UNPARSED_ACTIONS, IGNORE_UNPARSED_ACTIONS_DEFAULT.toString).toBoolean
  }

  def streamingQueryTableVersionIntervalSeconds(conf: SQLConf): Int = {
    val intervalStr = conf.getConfString(
      QUERY_TABLE_VERSION_INTERVAL_SECONDS,
      QUERY_TABLE_VERSION_INTERVAL_SECONDS_DEFAULT
    )
    toTimeInSeconds(intervalStr, QUERY_TABLE_VERSION_INTERVAL_SECONDS)
  }

  private def toTimeInSeconds(timeStr: String, conf: String): Int = {
    val timeInSeconds = JavaUtils.timeStringAs(timeStr, TimeUnit.SECONDS)
    validateNonNeg(timeInSeconds, conf)
    if (conf == QUERY_TABLE_VERSION_INTERVAL_SECONDS && timeInSeconds < 30) {
      throw new IllegalArgumentException(conf + " must not be less than 30 seconds.")
    }
    if (timeInSeconds > Int.MaxValue) {
      throw new IllegalArgumentException(conf + " is too big: " + timeStr)
    }
    timeInSeconds.toInt
  }

  private def validateNonNeg(value: Long, conf: String): Unit = {
    if (value < 0L) {
      throw new IllegalArgumentException(conf + " must not be negative")
    }
  }

  private def validatePositive(value: Int, conf: String): Unit = {
    if (value <= 0) {
      throw new IllegalArgumentException(conf + " must be positive")
    }
  }
}
