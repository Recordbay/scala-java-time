/*
 * Copyright (c) 2007-present, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.threeten.bp

import org.threeten.bp.temporal.ChronoField.EPOCH_DAY
import org.threeten.bp.temporal.ChronoField.INSTANT_SECONDS
import org.threeten.bp.temporal.ChronoField.NANO_OF_DAY
import org.threeten.bp.temporal.ChronoField.OFFSET_SECONDS
import org.threeten.bp.temporal.ChronoUnit.NANOS
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.io.InvalidObjectException
import java.io.ObjectStreamException
import java.io.Serializable
import java.util.{Objects, Comparator}
import org.threeten.bp.chrono.IsoChronology
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.DateTimeParseException
import org.threeten.bp.temporal.ChronoField
import org.threeten.bp.temporal.ChronoUnit
import org.threeten.bp.temporal.Temporal
import org.threeten.bp.temporal.TemporalAccessor
import org.threeten.bp.temporal.TemporalAdjuster
import org.threeten.bp.temporal.TemporalAmount
import org.threeten.bp.temporal.TemporalField
import org.threeten.bp.temporal.TemporalQueries
import org.threeten.bp.temporal.TemporalQuery
import org.threeten.bp.temporal.TemporalUnit
import org.threeten.bp.temporal.ValueRange
import org.threeten.bp.zone.ZoneRules

@SerialVersionUID(2287754244819255394L)
object OffsetDateTime {
  /** The minimum supported {@code OffsetDateTime}, '-999999999-01-01T00:00:00+18:00'.
    * This is the local date-time of midnight at the start of the minimum date
    * in the maximum offset (larger offsets are earlier on the time-line).
    * This combines {@link LocalDateTime#MIN} and {@link ZoneOffset#MAX}.
    * This could be used by an application as a "far past" date-time.
    */
  val MIN: OffsetDateTime = LocalDateTime.MIN.atOffset(ZoneOffset.MAX)
  /** The maximum supported {@code OffsetDateTime}, '+999999999-12-31T23:59:59.999999999-18:00'.
    * This is the local date-time just before midnight at the end of the maximum date
    * in the minimum offset (larger negative offsets are later on the time-line).
    * This combines {@link LocalDateTime#MAX} and {@link ZoneOffset#MIN}.
    * This could be used by an application as a "far future" date-time.
    */
  val MAX: OffsetDateTime = LocalDateTime.MAX.atOffset(ZoneOffset.MIN)

  /** Gets a comparator that compares two {@code OffsetDateTime} instances
    * based solely on the instant.
    *
    * This method differs from the comparison in {@link #compareTo} in that it
    * only compares the underlying instant.
    *
    * @return a comparator that compares in time-line order
    * @see #isAfter
    * @see #isBefore
    * @see #isEqual
    */
  def timeLineOrder: Comparator[OffsetDateTime] = INSTANT_COMPARATOR

  private val INSTANT_COMPARATOR: Comparator[OffsetDateTime] =
    (datetime1: OffsetDateTime, datetime2: OffsetDateTime) => {
      var cmp: Int = java.lang.Long.compare(datetime1.toEpochSecond, datetime2.toEpochSecond)
      if (cmp == 0)
        cmp = java.lang.Long.compare(datetime1.getNano, datetime2.getNano)
      cmp
    }

  /** Obtains the current date-time from the system clock in the default time-zone.
    *
    * This will query the {@link Clock#systemDefaultZone() system clock} in the default
    * time-zone to obtain the current date-time.
    * The offset will be calculated from the time-zone in the clock.
    *
    * Using this method will prevent the ability to use an alternate clock for testing
    * because the clock is hard-coded.
    *
    * @return the current date-time using the system clock, not null
    */
  def now: OffsetDateTime = now(Clock.systemDefaultZone)

  /** Obtains the current date-time from the system clock in the specified time-zone.
    *
    * This will query the {@link Clock#system(ZoneId) system clock} to obtain the current date-time.
    * Specifying the time-zone avoids dependence on the default time-zone.
    * The offset will be calculated from the specified time-zone.
    *
    * Using this method will prevent the ability to use an alternate clock for testing
    * because the clock is hard-coded.
    *
    * @param zone  the zone ID to use, not null
    * @return the current date-time using the system clock, not null
    */
  def now(zone: ZoneId): OffsetDateTime = now(Clock.system(zone))

  /** Obtains the current date-time from the specified clock.
    *
    * This will query the specified clock to obtain the current date-time.
    * The offset will be calculated from the time-zone in the clock.
    *
    * Using this method allows the use of an alternate clock for testing.
    * The alternate clock may be introduced using {@link Clock dependency injection}.
    *
    * @param clock  the clock to use, not null
    * @return the current date-time, not null
    */
  def now(clock: Clock): OffsetDateTime = {
    Objects.requireNonNull(clock, "clock")
    val now: Instant = clock.instant
    ofInstant(now, clock.getZone.getRules.getOffset(now))
  }

  /** Obtains an instance of {@code OffsetDateTime} from a date, time and offset.
    *
    * This creates an offset date-time with the specified local date, time and offset.
    *
    * @param date  the local date, not null
    * @param time  the local time, not null
    * @param offset  the zone offset, not null
    * @return the offset date-time, not null
    */
  def of(date: LocalDate, time: LocalTime, offset: ZoneOffset): OffsetDateTime = {
    val dt: LocalDateTime = LocalDateTime.of(date, time)
    new OffsetDateTime(dt, offset)
  }

  /** Obtains an instance of {@code OffsetDateTime} from a date-time and offset.
    *
    * This creates an offset date-time with the specified local date-time and offset.
    *
    * @param dateTime  the local date-time, not null
    * @param offset  the zone offset, not null
    * @return the offset date-time, not null
    */
  def of(dateTime: LocalDateTime, offset: ZoneOffset): OffsetDateTime = new OffsetDateTime(dateTime, offset)

  /** Obtains an instance of {@code OffsetDateTime} from a year, month, day,
    * hour, minute, second, nanosecond and offset.
    *
    * This creates an offset date-time with the seven specified fields.
    *
    * This method exists primarily for writing test cases.
    * Non test-code will typically use other methods to create an offset time.
    * {@code LocalDateTime} has five additional convenience variants of the
    * equivalent factory method taking fewer arguments.
    * They are not provided here to reduce the footprint of the API.
    *
    * @param year  the year to represent, from MIN_YEAR to MAX_YEAR
    * @param month  the month-of-year to represent, from 1 (January) to 12 (December)
    * @param dayOfMonth  the day-of-month to represent, from 1 to 31
    * @param hour  the hour-of-day to represent, from 0 to 23
    * @param minute  the minute-of-hour to represent, from 0 to 59
    * @param second  the second-of-minute to represent, from 0 to 59
    * @param nanoOfSecond  the nano-of-second to represent, from 0 to 999,999,999
    * @param offset  the zone offset, not null
    * @return the offset date-time, not null
    * @throws DateTimeException if the value of any field is out of range, or
    *                           if the day-of-month is invalid for the month-year
    */
  def of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int, offset: ZoneOffset): OffsetDateTime = {
    val dt: LocalDateTime = LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond)
    new OffsetDateTime(dt, offset)
  }

  /** Obtains an instance of {@code OffsetDateTime} from an {@code Instant} and zone ID.
    *
    * This creates an offset date-time with the same instant as that specified.
    * Finding the offset from UTC/Greenwich is simple as there is only one valid
    * offset for each instant.
    *
    * @param instant  the instant to create the date-time from, not null
    * @param zone  the time-zone, which may be an offset, not null
    * @return the offset date-time, not null
    * @throws DateTimeException if the result exceeds the supported range
    */
  def ofInstant(instant: Instant, zone: ZoneId): OffsetDateTime = {
    Objects.requireNonNull(instant, "instant")
    Objects.requireNonNull(zone, "zone")
    val rules: ZoneRules = zone.getRules
    val offset: ZoneOffset = rules.getOffset(instant)
    val ldt: LocalDateTime = LocalDateTime.ofEpochSecond(instant.getEpochSecond, instant.getNano, offset)
    new OffsetDateTime(ldt, offset)
  }

  /** Obtains an instance of {@code OffsetDateTime} from a temporal object.
    *
    * A {@code TemporalAccessor} represents some form of date and time information.
    * This factory converts the arbitrary temporal object to an instance of {@code OffsetDateTime}.
    *
    * The conversion extracts and combines {@code LocalDateTime} and {@code ZoneOffset}.
    * If that fails it will try to extract and combine {@code Instant} and {@code ZoneOffset}.
    *
    * This method matches the signature of the functional interface {@link TemporalQuery}
    * allowing it to be used in queries via method reference, {@code OffsetDateTime::from}.
    *
    * @param temporal  the temporal object to convert, not null
    * @return the offset date-time, not null
    * @throws DateTimeException if unable to convert to an { @code OffsetDateTime}
    */
  def from(temporal: TemporalAccessor): OffsetDateTime = {
    if (temporal.isInstanceOf[OffsetDateTime])
      return temporal.asInstanceOf[OffsetDateTime]
    try {
      val offset: ZoneOffset = ZoneOffset.from(temporal)
      try {
        val ldt: LocalDateTime = LocalDateTime.from(temporal)
        OffsetDateTime.of(ldt, offset)
      }
      catch {
        case ignore: DateTimeException =>
          val instant: Instant = Instant.from(temporal)
          OffsetDateTime.ofInstant(instant, offset)
      }
    }
    catch {
      case ex: DateTimeException =>
        throw new DateTimeException(s"Unable to obtain OffsetDateTime from TemporalAccessor: $temporal, type ${temporal.getClass.getName}")
    }
  }

  /** Obtains an instance of {@code OffsetDateTime} from a text string
    * such as {@code 2007-12-03T10:15:30+01:00}.
    *
    * The string must represent a valid date-time and is parsed using
    * {@link org.threeten.bp.format.DateTimeFormatter#ISO_OFFSET_DATE_TIME}.
    *
    * @param text  the text to parse such as "2007-12-03T10:15:30+01:00", not null
    * @return the parsed offset date-time, not null
    * @throws DateTimeParseException if the text cannot be parsed
    */
  def parse(text: CharSequence): OffsetDateTime = parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  /** Obtains an instance of {@code OffsetDateTime} from a text string using a specific formatter.
    *
    * The text is parsed using the formatter, returning a date-time.
    *
    * @param text  the text to parse, not null
    * @param formatter  the formatter to use, not null
    * @return the parsed offset date-time, not null
    * @throws DateTimeParseException if the text cannot be parsed
    */
  def parse(text: CharSequence, formatter: DateTimeFormatter): OffsetDateTime = {
    Objects.requireNonNull(formatter, "formatter")
    formatter.parse(text, OffsetDateTime.from)
  }

  @throws(classOf[IOException])
  private[bp] def readExternal(in: DataInput): OffsetDateTime = {
    val dateTime: LocalDateTime = LocalDateTime.readExternal(in)
    val offset: ZoneOffset = ZoneOffset.readExternal(in)
    OffsetDateTime.of(dateTime, offset)
  }
}

/** A date-time with an offset from UTC/Greenwich in the ISO-8601 calendar system,
  * such as {@code 2007-12-03T10:15:30+01:00}.
  *
  * {@code OffsetDateTime} is an immutable representation of a date-time with an offset.
  * This class stores all date and time fields, to a precision of nanoseconds,
  * as well as the offset from UTC/Greenwich. For example, the value
  * "2nd October 2007 at 13:45.30.123456789 +02:00" can be stored in an {@code OffsetDateTime}.
  *
  * {@code OffsetDateTime}, {@link ZonedDateTime} and {@link Instant} all store an instant
  * on the time-line to nanosecond precision.
  * {@code Instant} is the simplest, simply representing the instant.
  * {@code OffsetDateTime} adds to the instant the offset from UTC/Greenwich, which allows
  * the local date-time to be obtained.
  * {@code ZonedDateTime} adds full time-zone rules.
  *
  * It is intended that {@code ZonedDateTime} or {@code Instant} is used to model data
  * in simpler applications. This class may be used when modeling date-time concepts in
  * more detail, or when communicating to a database or in a network protocol.
  *
  * <h3>Specification for implementors</h3>
  * This class is immutable and thread-safe.
  *
  * @constructor
  * @param dateTime  the local date-time, not null
  * @param offset  the zone offset, not null
  */
@SerialVersionUID(2287754244819255394L)
final class OffsetDateTime private(private val dateTime: LocalDateTime, private val offset: ZoneOffset) extends Temporal with TemporalAdjuster with Ordered[OffsetDateTime] with Serializable {
    Objects.requireNonNull(dateTime, "dateTime")
    Objects.requireNonNull(offset, "offset")

  /** Returns a new date-time based on this one, returning {@code this} where possible.
    *
    * @param dateTime  the date-time to create with, not null
    * @param offset  the zone offset to create with, not null
    */
  private def `with`(dateTime: LocalDateTime, offset: ZoneOffset): OffsetDateTime =
    if ((this.dateTime eq dateTime) && (this.offset == offset)) this
    else new OffsetDateTime(dateTime, offset)

  /** Checks if the specified field is supported.
    *
    * This checks if this date-time can be queried for the specified field.
    * If false, then calling the {@link #range(TemporalField) range} and
    * {@link #get(TemporalField) get} methods will throw an exception.
    *
    * If the field is a {@link ChronoField} then the query is implemented here.
    * The supported fields are:
    * <ul>
    * <li>{@code NANO_OF_SECOND}
    * <li>{@code NANO_OF_DAY}
    * <li>{@code MICRO_OF_SECOND}
    * <li>{@code MICRO_OF_DAY}
    * <li>{@code MILLI_OF_SECOND}
    * <li>{@code MILLI_OF_DAY}
    * <li>{@code SECOND_OF_MINUTE}
    * <li>{@code SECOND_OF_DAY}
    * <li>{@code MINUTE_OF_HOUR}
    * <li>{@code MINUTE_OF_DAY}
    * <li>{@code HOUR_OF_AMPM}
    * <li>{@code CLOCK_HOUR_OF_AMPM}
    * <li>{@code HOUR_OF_DAY}
    * <li>{@code CLOCK_HOUR_OF_DAY}
    * <li>{@code AMPM_OF_DAY}
    * <li>{@code DAY_OF_WEEK}
    * <li>{@code ALIGNED_DAY_OF_WEEK_IN_MONTH}
    * <li>{@code ALIGNED_DAY_OF_WEEK_IN_YEAR}
    * <li>{@code DAY_OF_MONTH}
    * <li>{@code DAY_OF_YEAR}
    * <li>{@code EPOCH_DAY}
    * <li>{@code ALIGNED_WEEK_OF_MONTH}
    * <li>{@code ALIGNED_WEEK_OF_YEAR}
    * <li>{@code MONTH_OF_YEAR}
    * <li>{@code EPOCH_MONTH}
    * <li>{@code YEAR_OF_ERA}
    * <li>{@code YEAR}
    * <li>{@code ERA}
    * <li>{@code INSTANT_SECONDS}
    * <li>{@code OFFSET_SECONDS}
    * </ul>
    * All other {@code ChronoField} instances will return false.
    *
    * If the field is not a {@code ChronoField}, then the result of this method
    * is obtained by invoking {@code TemporalField.isSupportedBy(TemporalAccessor)}
    * passing {@code this} as the argument.
    * Whether the field is supported is determined by the field.
    *
    * @param field  the field to check, null returns false
    * @return true if the field is supported on this date-time, false if not
    */
  def isSupported(field: TemporalField): Boolean =
    field.isInstanceOf[ChronoField] || (field != null && field.isSupportedBy(this))

  def isSupported(unit: TemporalUnit): Boolean =
    if (unit.isInstanceOf[ChronoUnit]) unit.isDateBased || unit.isTimeBased
    else unit != null && unit.isSupportedBy(this)

  /** Gets the range of valid values for the specified field.
    *
    * The range object expresses the minimum and maximum valid values for a field.
    * This date-time is used to enhance the accuracy of the returned range.
    * If it is not possible to return the range, because the field is not supported
    * or for some other reason, an exception is thrown.
    *
    * If the field is a {@link ChronoField} then the query is implemented here.
    * The {@link #isSupported(TemporalField) supported fields} will return
    * appropriate range instances.
    * All other {@code ChronoField} instances will throw a {@code DateTimeException}.
    *
    * If the field is not a {@code ChronoField}, then the result of this method
    * is obtained by invoking {@code TemporalField.rangeRefinedBy(TemporalAccessor)}
    * passing {@code this} as the argument.
    * Whether the range can be obtained is determined by the field.
    *
    * @param field  the field to query the range for, not null
    * @return the range of valid values for the field, not null
    * @throws DateTimeException if the range for the field cannot be obtained
    */
  override def range(field: TemporalField): ValueRange =
    if (field.isInstanceOf[ChronoField])
      if ((field eq INSTANT_SECONDS) || (field eq OFFSET_SECONDS)) field.range
      else dateTime.range(field)
    else
      field.rangeRefinedBy(this)

  /** Gets the value of the specified field from this date-time as an {@code int}.
    *
    * This queries this date-time for the value for the specified field.
    * The returned value will always be within the valid range of values for the field.
    * If it is not possible to return the value, because the field is not supported
    * or for some other reason, an exception is thrown.
    *
    * If the field is a {@link ChronoField} then the query is implemented here.
    * The {@link #isSupported(TemporalField) supported fields} will return valid
    * values based on this date-time, except {@code NANO_OF_DAY}, {@code MICRO_OF_DAY},
    * {@code EPOCH_DAY}, {@code EPOCH_MONTH} and {@code INSTANT_SECONDS} which are too
    * large to fit in an {@code int} and throw a {@code DateTimeException}.
    * All other {@code ChronoField} instances will throw a {@code DateTimeException}.
    *
    * If the field is not a {@code ChronoField}, then the result of this method
    * is obtained by invoking {@code TemporalField.getFrom(TemporalAccessor)}
    * passing {@code this} as the argument. Whether the value can be obtained,
    * and what the value represents, is determined by the field.
    *
    * @param field  the field to get, not null
    * @return the value for the field
    * @throws DateTimeException if a value for the field cannot be obtained
    * @throws ArithmeticException if numeric overflow occurs
    */
  override def get(field: TemporalField): Int = {
    if (field.isInstanceOf[ChronoField]) {
      field.asInstanceOf[ChronoField] match {
        case INSTANT_SECONDS => throw new DateTimeException(s"Field too large for an int: $field")
        case OFFSET_SECONDS  => getOffset.getTotalSeconds
        case _               => dateTime.get(field)
      }
    } else {
      super.get(field)
    }
  }

  /** Gets the value of the specified field from this date-time as a {@code long}.
    *
    * This queries this date-time for the value for the specified field.
    * If it is not possible to return the value, because the field is not supported
    * or for some other reason, an exception is thrown.
    *
    * If the field is a {@link ChronoField} then the query is implemented here.
    * The {@link #isSupported(TemporalField) supported fields} will return valid
    * values based on this date-time.
    * All other {@code ChronoField} instances will throw a {@code DateTimeException}.
    *
    * If the field is not a {@code ChronoField}, then the result of this method
    * is obtained by invoking {@code TemporalField.getFrom(TemporalAccessor)}
    * passing {@code this} as the argument. Whether the value can be obtained,
    * and what the value represents, is determined by the field.
    *
    * @param field  the field to get, not null
    * @return the value for the field
    * @throws DateTimeException if a value for the field cannot be obtained
    * @throws ArithmeticException if numeric overflow occurs
    */
  def getLong(field: TemporalField): Long = {
    if (field.isInstanceOf[ChronoField]) {
      field.asInstanceOf[ChronoField] match {
        case INSTANT_SECONDS => toEpochSecond
        case OFFSET_SECONDS  => getOffset.getTotalSeconds
        case _               => dateTime.getLong(field)
      }
    } else {
      field.getFrom(this)
    }
  }

  /** Gets the zone offset, such as '+01:00'.
    *
    * This is the offset of the local date-time from UTC/Greenwich.
    *
    * @return the zone offset, not null
    */
  def getOffset: ZoneOffset = offset

  /** Returns a copy of this {@code OffsetDateTime} with the specified offset ensuring
    * that the result has the same local date-time.
    *
    * This method returns an object with the same {@code LocalDateTime} and the specified {@code ZoneOffset}.
    * No calculation is needed or performed.
    * For example, if this time represents {@code 2007-12-03T10:30+02:00} and the offset specified is
    * {@code +03:00}, then this method will return {@code 2007-12-03T10:30+03:00}.
    *
    * To take into account the difference between the offsets, and adjust the time fields,
    * use {@link #withOffsetSameInstant}.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param offset  the zone offset to change to, not null
    * @return an { @code OffsetDateTime} based on this date-time with the requested offset, not null
    */
  def withOffsetSameLocal(offset: ZoneOffset): OffsetDateTime = `with`(dateTime, offset)

  /** Returns a copy of this {@code OffsetDateTime} with the specified offset ensuring
    * that the result is at the same instant.
    *
    * This method returns an object with the specified {@code ZoneOffset} and a {@code LocalDateTime}
    * adjusted by the difference between the two offsets.
    * This will result in the old and new objects representing the same instant.
    * This is useful for finding the local time in a different offset.
    * For example, if this time represents {@code 2007-12-03T10:30+02:00} and the offset specified is
    * {@code +03:00}, then this method will return {@code 2007-12-03T11:30+03:00}.
    *
    * To change the offset without adjusting the local time use {@link #withOffsetSameLocal}.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param offset  the zone offset to change to, not null
    * @return an { @code OffsetDateTime} based on this date-time with the requested offset, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def withOffsetSameInstant(offset: ZoneOffset): OffsetDateTime = {
    if (offset == this.offset)
      return this
    val difference: Int = offset.getTotalSeconds - this.offset.getTotalSeconds
    val adjusted: LocalDateTime = dateTime.plusSeconds(difference)
    new OffsetDateTime(adjusted, offset)
  }

  /** Gets the year field.
    *
    * This method returns the primitive {@code int} value for the year.
    *
    * The year returned by this method is proleptic as per {@code get(YEAR)}.
    * To obtain the year-of-era, use {@code get(YEAR_OF_ERA}.
    *
    * @return the year, from MIN_YEAR to MAX_YEAR
    */
  def getYear: Int = dateTime.getYear

  /** Gets the month-of-year field from 1 to 12.
    *
    * This method returns the month as an {@code int} from 1 to 12.
    * Application code is frequently clearer if the enum {@link Month}
    * is used by calling {@link #getMonth()}.
    *
    * @return the month-of-year, from 1 to 12
    * @see #getMonth()
    */
  def getMonthValue: Int = dateTime.getMonthValue

  /** Gets the month-of-year field using the {@code Month} enum.
    *
    * This method returns the enum {@link Month} for the month.
    * This avoids confusion as to what {@code int} values mean.
    * If you need access to the primitive {@code int} value then the enum
    * provides the {@link Month#getValue() int value}.
    *
    * @return the month-of-year, not null
    * @see #getMonthValue()
    */
  def getMonth: Month = dateTime.getMonth

  /** Gets the day-of-month field.
    *
    * This method returns the primitive {@code int} value for the day-of-month.
    *
    * @return the day-of-month, from 1 to 31
    */
  def getDayOfMonth: Int = dateTime.getDayOfMonth

  /** Gets the day-of-year field.
    *
    * This method returns the primitive {@code int} value for the day-of-year.
    *
    * @return the day-of-year, from 1 to 365, or 366 in a leap year
    */
  def getDayOfYear: Int = dateTime.getDayOfYear

  /** Gets the day-of-week field, which is an enum {@code DayOfWeek}.
    *
    * This method returns the enum {@link DayOfWeek} for the day-of-week.
    * This avoids confusion as to what {@code int} values mean.
    * If you need access to the primitive {@code int} value then the enum
    * provides the {@link DayOfWeek#getValue() int value}.
    *
    * Additional information can be obtained from the {@code DayOfWeek}.
    * This includes textual names of the values.
    *
    * @return the day-of-week, not null
    */
  def getDayOfWeek: DayOfWeek = dateTime.getDayOfWeek

  /** Gets the hour-of-day field.
    *
    * @return the hour-of-day, from 0 to 23
    */
  def getHour: Int = dateTime.getHour

  /** Gets the minute-of-hour field.
    *
    * @return the minute-of-hour, from 0 to 59
    */
  def getMinute: Int = dateTime.getMinute

  /** Gets the second-of-minute field.
    *
    * @return the second-of-minute, from 0 to 59
    */
  def getSecond: Int = dateTime.getSecond

  /** Gets the nano-of-second field.
    *
    * @return the nano-of-second, from 0 to 999,999,999
    */
  def getNano: Int = dateTime.getNano

  /** Returns an adjusted copy of this date-time.
    *
    * This returns a new {@code OffsetDateTime}, based on this one, with the date-time adjusted.
    * The adjustment takes place using the specified adjuster strategy object.
    * Read the documentation of the adjuster to understand what adjustment will be made.
    *
    * A simple adjuster might simply set the one of the fields, such as the year field.
    * A more complex adjuster might set the date to the last day of the month.
    * A selection of common adjustments is provided in {@link TemporalAdjusters}.
    * These include finding the "last day of the month" and "next Wednesday".
    * Key date-time classes also implement the {@code TemporalAdjuster} interface,
    * such as {@link Month} and {@link MonthDay}.
    * The adjuster is responsible for handling special cases, such as the varying
    * lengths of month and leap years.
    *
    * For example this code returns a date on the last day of July:
    * <pre>
    * import static org.threeten.bp.Month.*;
    * import static org.threeten.bp.temporal.Adjusters.*;
    *
    * result = offsetDateTime.with(JULY).with(lastDayOfMonth());
    * </pre>
    *
    * The classes {@link LocalDate}, {@link LocalTime} and {@link ZoneOffset} implement
    * {@code TemporalAdjuster}, thus this method can be used to change the date, time or offset:
    * <pre>
    * result = offsetDateTime.with(date);
    * result = offsetDateTime.with(time);
    * result = offsetDateTime.with(offset);
    * </pre>
    *
    * The result of this method is obtained by invoking the
    * {@link TemporalAdjuster#adjustInto(Temporal)} method on the
    * specified adjuster passing {@code this} as the argument.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param adjuster the adjuster to use, not null
    * @return an { @code OffsetDateTime} based on { @code this} with the adjustment made, not null
    * @throws DateTimeException if the adjustment cannot be made
    * @throws ArithmeticException if numeric overflow occurs
    */
  override def `with`(adjuster: TemporalAdjuster): OffsetDateTime =
    if (adjuster.isInstanceOf[LocalDate] || adjuster.isInstanceOf[LocalTime] || adjuster.isInstanceOf[LocalDateTime])
      `with`(dateTime.`with`(adjuster), offset)
    else if (adjuster.isInstanceOf[Instant])
      OffsetDateTime.ofInstant(adjuster.asInstanceOf[Instant], offset)
    else if (adjuster.isInstanceOf[ZoneOffset])
      `with`(dateTime, adjuster.asInstanceOf[ZoneOffset])
    else if (adjuster.isInstanceOf[OffsetDateTime])
      adjuster.asInstanceOf[OffsetDateTime]
    else
      adjuster.adjustInto(this).asInstanceOf[OffsetDateTime]

  /** Returns a copy of this date-time with the specified field set to a new value.
    *
    * This returns a new {@code OffsetDateTime}, based on this one, with the value
    * for the specified field changed.
    * This can be used to change any supported field, such as the year, month or day-of-month.
    * If it is not possible to set the value, because the field is not supported or for
    * some other reason, an exception is thrown.
    *
    * In some cases, changing the specified field can cause the resulting date-time to become invalid,
    * such as changing the month from 31st January to February would make the day-of-month invalid.
    * In cases like this, the field is responsible for resolving the date. Typically it will choose
    * the previous valid date, which would be the last valid day of February in this example.
    *
    * If the field is a {@link ChronoField} then the adjustment is implemented here.
    *
    * The {@code INSTANT_SECONDS} field will return a date-time with the specified instant.
    * The offset and nano-of-second are unchanged.
    * If the new instant value is outside the valid range then a {@code DateTimeException} will be thrown.
    *
    * The {@code OFFSET_SECONDS} field will return a date-time with the specified offset.
    * The local date-time is unaltered. If the new offset value is outside the valid range
    * then a {@code DateTimeException} will be thrown.
    *
    * The other {@link #isSupported(TemporalField) supported fields} will behave as per
    * the matching method on {@link LocalDateTime#with(TemporalField, long) LocalDateTime}.
    * In this case, the offset is not part of the calculation and will be unchanged.
    *
    * All other {@code ChronoField} instances will throw a {@code DateTimeException}.
    *
    * If the field is not a {@code ChronoField}, then the result of this method
    * is obtained by invoking {@code TemporalField.adjustInto(Temporal, long)}
    * passing {@code this} as the argument. In this case, the field determines
    * whether and how to adjust the instant.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param field  the field to set in the result, not null
    * @param newValue  the new value of the field in the result
    * @return an { @code OffsetDateTime} based on { @code this} with the specified field set, not null
    * @throws DateTimeException if the field cannot be set
    * @throws ArithmeticException if numeric overflow occurs
    */
  def `with`(field: TemporalField, newValue: Long): OffsetDateTime = {
    if (field.isInstanceOf[ChronoField]) {
      val f: ChronoField = field.asInstanceOf[ChronoField]
      f match {
        case INSTANT_SECONDS => OffsetDateTime.ofInstant(Instant.ofEpochSecond(newValue, getNano), offset)
        case OFFSET_SECONDS  => `with`(dateTime, ZoneOffset.ofTotalSeconds(f.checkValidIntValue(newValue)))
        case _               => `with`(dateTime.`with`(field, newValue), offset)
      }
    } else {
      field.adjustInto(this, newValue)
    }
  }

  /** Returns a copy of this {@code OffsetDateTime} with the year altered.
    * The offset does not affect the calculation and will be the same in the result.
    * If the day-of-month is invalid for the year, it will be changed to the last valid day of the month.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param year  the year to set in the result, from MIN_YEAR to MAX_YEAR
    * @return an { @code OffsetDateTime} based on this date-time with the requested year, not null
    * @throws DateTimeException if the year value is invalid
    */
  def withYear(year: Int): OffsetDateTime = `with`(dateTime.withYear(year), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the month-of-year altered.
    * The offset does not affect the calculation and will be the same in the result.
    * If the day-of-month is invalid for the year, it will be changed to the last valid day of the month.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param month  the month-of-year to set in the result, from 1 (January) to 12 (December)
    * @return an { @code OffsetDateTime} based on this date-time with the requested month, not null
    * @throws DateTimeException if the month-of-year value is invalid
    */
  def withMonth(month: Int): OffsetDateTime = `with`(dateTime.withMonth(month), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the day-of-month altered.
    * If the resulting {@code OffsetDateTime} is invalid, an exception is thrown.
    * The offset does not affect the calculation and will be the same in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param dayOfMonth  the day-of-month to set in the result, from 1 to 28-31
    * @return an { @code OffsetDateTime} based on this date-time with the requested day, not null
    * @throws DateTimeException if the day-of-month value is invalid
    * @throws DateTimeException if the day-of-month is invalid for the month-year
    */
  def withDayOfMonth(dayOfMonth: Int): OffsetDateTime = `with`(dateTime.withDayOfMonth(dayOfMonth), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the day-of-year altered.
    * If the resulting {@code OffsetDateTime} is invalid, an exception is thrown.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param dayOfYear  the day-of-year to set in the result, from 1 to 365-366
    * @return an { @code OffsetDateTime} based on this date with the requested day, not null
    * @throws DateTimeException if the day-of-year value is invalid
    * @throws DateTimeException if the day-of-year is invalid for the year
    */
  def withDayOfYear(dayOfYear: Int): OffsetDateTime = `with`(dateTime.withDayOfYear(dayOfYear), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the hour-of-day value altered.
    *
    * The offset does not affect the calculation and will be the same in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param hour  the hour-of-day to set in the result, from 0 to 23
    * @return an { @code OffsetDateTime} based on this date-time with the requested hour, not null
    * @throws DateTimeException if the hour value is invalid
    */
  def withHour(hour: Int): OffsetDateTime = `with`(dateTime.withHour(hour), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the minute-of-hour value altered.
    *
    * The offset does not affect the calculation and will be the same in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param minute  the minute-of-hour to set in the result, from 0 to 59
    * @return an { @code OffsetDateTime} based on this date-time with the requested minute, not null
    * @throws DateTimeException if the minute value is invalid
    */
  def withMinute(minute: Int): OffsetDateTime = `with`(dateTime.withMinute(minute), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the second-of-minute value altered.
    *
    * The offset does not affect the calculation and will be the same in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param second  the second-of-minute to set in the result, from 0 to 59
    * @return an { @code OffsetDateTime} based on this date-time with the requested second, not null
    * @throws DateTimeException if the second value is invalid
    */
  def withSecond(second: Int): OffsetDateTime = `with`(dateTime.withSecond(second), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the nano-of-second value altered.
    *
    * The offset does not affect the calculation and will be the same in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param nanoOfSecond  the nano-of-second to set in the result, from 0 to 999,999,999
    * @return an { @code OffsetDateTime} based on this date-time with the requested nanosecond, not null
    * @throws DateTimeException if the nanos value is invalid
    */
  def withNano(nanoOfSecond: Int): OffsetDateTime = `with`(dateTime.withNano(nanoOfSecond), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the time truncated.
    *
    * Truncation returns a copy of the original date-time with fields
    * smaller than the specified unit set to zero.
    * For example, truncating with the {@link ChronoUnit#MINUTES minutes} unit
    * will set the second-of-minute and nano-of-second field to zero.
    *
    * The unit must have a {@linkplain TemporalUnit#getDuration() duration}
    * that divides into the length of a standard day without remainder.
    * This includes all supplied time units on {@link ChronoUnit} and
    * {@link ChronoUnit#DAYS DAYS}. Other units throw an exception.
    *
    * The offset does not affect the calculation and will be the same in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param unit  the unit to truncate to, not null
    * @return an { @code OffsetDateTime} based on this date-time with the time truncated, not null
    * @throws DateTimeException if unable to truncate
    */
  def truncatedTo(unit: TemporalUnit): OffsetDateTime = `with`(dateTime.truncatedTo(unit), offset)

  /** Returns a copy of this date-time with the specified period added.
    *
    * This method returns a new date-time based on this time with the specified period added.
    * The amount is typically {@link Period} but may be any other type implementing
    * the {@link TemporalAmount} interface.
    * The calculation is delegated to the specified adjuster, which typically calls
    * back to {@link #plus(long, TemporalUnit)}.
    * The offset is not part of the calculation and will be unchanged in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param amount  the amount to add, not null
    * @return an { @code OffsetDateTime} based on this date-time with the addition made, not null
    * @throws DateTimeException if the addition cannot be made
    * @throws ArithmeticException if numeric overflow occurs
    */
  override def plus(amount: TemporalAmount): OffsetDateTime = amount.addTo(this).asInstanceOf[OffsetDateTime]

  /** Returns a copy of this date-time with the specified period added.
    *
    * This method returns a new date-time based on this date-time with the specified period added.
    * This can be used to add any period that is defined by a unit, for example to add years, months or days.
    * The unit is responsible for the details of the calculation, including the resolution
    * of any edge cases in the calculation.
    * The offset is not part of the calculation and will be unchanged in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param amountToAdd  the amount of the unit to add to the result, may be negative
    * @param unit  the unit of the period to add, not null
    * @return an { @code OffsetDateTime} based on this date-time with the specified period added, not null
    * @throws DateTimeException if the unit cannot be added to this type
    */
  def plus(amountToAdd: Long, unit: TemporalUnit): OffsetDateTime =
    if (unit.isInstanceOf[ChronoUnit]) `with`(dateTime.plus(amountToAdd, unit), offset)
    else unit.addTo(this, amountToAdd)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in years added.
    *
    * This method adds the specified amount to the years field in three steps:
    * <ol>
    * <li>Add the input years to the year field</li>
    * <li>Check if the resulting date would be invalid</li>
    * <li>Adjust the day-of-month to the last valid day if necessary</li>
    * </ol>
    *
    * For example, 2008-02-29 (leap year) plus one year would result in the
    * invalid date 2009-02-29 (standard year). Instead of returning an invalid
    * result, the last valid day of the month, 2009-02-28, is selected instead.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param years  the years to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the years added, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def plusYears(years: Long): OffsetDateTime = `with`(dateTime.plusYears(years), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in months added.
    *
    * This method adds the specified amount to the months field in three steps:
    * <ol>
    * <li>Add the input months to the month-of-year field</li>
    * <li>Check if the resulting date would be invalid</li>
    * <li>Adjust the day-of-month to the last valid day if necessary</li>
    * </ol>
    *
    * For example, 2007-03-31 plus one month would result in the invalid date
    * 2007-04-31. Instead of returning an invalid result, the last valid day
    * of the month, 2007-04-30, is selected instead.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param months  the months to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the months added, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def plusMonths(months: Long): OffsetDateTime = `with`(dateTime.plusMonths(months), offset)

  /** Returns a copy of this OffsetDateTime with the specified period in weeks added.
    *
    * This method adds the specified amount in weeks to the days field incrementing
    * the month and year fields as necessary to ensure the result remains valid.
    * The result is only invalid if the maximum/minimum year is exceeded.
    *
    * For example, 2008-12-31 plus one week would result in the 2009-01-07.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param weeks  the weeks to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the weeks added, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def plusWeeks(weeks: Long): OffsetDateTime = `with`(dateTime.plusWeeks(weeks), offset)

  /** Returns a copy of this OffsetDateTime with the specified period in days added.
    *
    * This method adds the specified amount to the days field incrementing the
    * month and year fields as necessary to ensure the result remains valid.
    * The result is only invalid if the maximum/minimum year is exceeded.
    *
    * For example, 2008-12-31 plus one day would result in the 2009-01-01.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param days  the days to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the days added, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def plusDays(days: Long): OffsetDateTime = `with`(dateTime.plusDays(days), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in hours added.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param hours  the hours to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the hours added, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def plusHours(hours: Long): OffsetDateTime = `with`(dateTime.plusHours(hours), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in minutes added.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param minutes  the minutes to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the minutes added, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def plusMinutes(minutes: Long): OffsetDateTime = `with`(dateTime.plusMinutes(minutes), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in seconds added.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param seconds  the seconds to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the seconds added, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def plusSeconds(seconds: Long): OffsetDateTime = `with`(dateTime.plusSeconds(seconds), offset)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in nanoseconds added.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param nanos  the nanos to add, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the nanoseconds added, not null
    * @throws DateTimeException if the unit cannot be added to this type
    */
  def plusNanos(nanos: Long): OffsetDateTime = `with`(dateTime.plusNanos(nanos), offset)

  /** Returns a copy of this date-time with the specified period subtracted.
    *
    * This method returns a new date-time based on this time with the specified period subtracted.
    * The amount is typically {@link Period} but may be any other type implementing
    * the {@link TemporalAmount} interface.
    * The calculation is delegated to the specified adjuster, which typically calls
    * back to {@link #minus(long, TemporalUnit)}.
    * The offset is not part of the calculation and will be unchanged in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param amount  the amount to subtract, not null
    * @return an { @code OffsetDateTime} based on this date-time with the subtraction made, not null
    * @throws DateTimeException if the subtraction cannot be made
    * @throws ArithmeticException if numeric overflow occurs
    */
  override def minus(amount: TemporalAmount): OffsetDateTime = amount.subtractFrom(this).asInstanceOf[OffsetDateTime]

  /** Returns a copy of this date-time with the specified period subtracted.
    *
    * This method returns a new date-time based on this date-time with the specified period subtracted.
    * This can be used to subtract any period that is defined by a unit, for example to subtract years, months or days.
    * The unit is responsible for the details of the calculation, including the resolution
    * of any edge cases in the calculation.
    * The offset is not part of the calculation and will be unchanged in the result.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param amountToSubtract  the amount of the unit to subtract from the result, may be negative
    * @param unit  the unit of the period to subtract, not null
    * @return an { @code OffsetDateTime} based on this date-time with the specified period subtracted, not null
    */
  override def minus(amountToSubtract: Long, unit: TemporalUnit): OffsetDateTime =
    if (amountToSubtract == Long.MinValue) plus(Long.MaxValue, unit).plus(1, unit)
    else plus(-amountToSubtract, unit)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in years subtracted.
    *
    * This method subtracts the specified amount from the years field in three steps:
    * <ol>
    * <li>Subtract the input years to the year field</li>
    * <li>Check if the resulting date would be invalid</li>
    * <li>Adjust the day-of-month to the last valid day if necessary</li>
    * </ol>
    *
    * For example, 2008-02-29 (leap year) minus one year would result in the
    * invalid date 2009-02-29 (standard year). Instead of returning an invalid
    * result, the last valid day of the month, 2009-02-28, is selected instead.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param years  the years to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the years subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusYears(years: Long): OffsetDateTime =
    if (years == Long.MinValue) plusYears(Long.MaxValue).plusYears(1)
    else plusYears(-years)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in months subtracted.
    *
    * This method subtracts the specified amount from the months field in three steps:
    * <ol>
    * <li>Subtract the input months to the month-of-year field</li>
    * <li>Check if the resulting date would be invalid</li>
    * <li>Adjust the day-of-month to the last valid day if necessary</li>
    * </ol>
    *
    * For example, 2007-03-31 minus one month would result in the invalid date
    * 2007-04-31. Instead of returning an invalid result, the last valid day
    * of the month, 2007-04-30, is selected instead.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param months  the months to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the months subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusMonths(months: Long): OffsetDateTime =
    if (months == Long.MinValue) plusMonths(Long.MaxValue).plusMonths(1)
    else plusMonths(-months)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in weeks subtracted.
    *
    * This method subtracts the specified amount in weeks from the days field decrementing
    * the month and year fields as necessary to ensure the result remains valid.
    * The result is only invalid if the maximum/minimum year is exceeded.
    *
    * For example, 2008-12-31 minus one week would result in the 2009-01-07.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param weeks  the weeks to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the weeks subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusWeeks(weeks: Long): OffsetDateTime =
    if (weeks == Long.MinValue) plusWeeks(Long.MaxValue).plusWeeks(1)
    else plusWeeks(-weeks)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in days subtracted.
    *
    * This method subtracts the specified amount from the days field incrementing the
    * month and year fields as necessary to ensure the result remains valid.
    * The result is only invalid if the maximum/minimum year is exceeded.
    *
    * For example, 2008-12-31 minus one day would result in the 2009-01-01.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param days  the days to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the days subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusDays(days: Long): OffsetDateTime =
    if (days == Long.MinValue) plusDays(Long.MaxValue).plusDays(1)
    else plusDays(-days)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in hours subtracted.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param hours  the hours to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the hours subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusHours(hours: Long): OffsetDateTime =
    if (hours == Long.MinValue) plusHours(Long.MaxValue).plusHours(1)
    else plusHours(-hours)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in minutes subtracted.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param minutes  the minutes to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the minutes subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusMinutes(minutes: Long): OffsetDateTime =
    if (minutes == Long.MinValue) plusMinutes(Long.MaxValue).plusMinutes(1)
    else plusMinutes(-minutes)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in seconds subtracted.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param seconds  the seconds to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the seconds subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusSeconds(seconds: Long): OffsetDateTime =
    if (seconds == Long.MinValue) plusSeconds(Long.MaxValue).plusSeconds(1)
    else plusSeconds(-seconds)

  /** Returns a copy of this {@code OffsetDateTime} with the specified period in nanoseconds subtracted.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param nanos  the nanos to subtract, may be negative
    * @return an { @code OffsetDateTime} based on this date-time with the nanoseconds subtracted, not null
    * @throws DateTimeException if the result exceeds the supported date range
    */
  def minusNanos(nanos: Long): OffsetDateTime =
    if (nanos == Long.MinValue) plusNanos(Long.MaxValue).plusNanos(1)
    else plusNanos(-nanos)

  /** Queries this date-time using the specified query.
    *
    * This queries this date-time using the specified query strategy object.
    * The {@code TemporalQuery} object defines the logic to be used to
    * obtain the result. Read the documentation of the query to understand
    * what the result of this method will be.
    *
    * The result of this method is obtained by invoking the
    * {@link TemporalQuery#queryFrom(TemporalAccessor)} method on the
    * specified query passing {@code this} as the argument.
    *
    * @tparam R the type of the result
    * @param query  the query to invoke, not null
    * @return the query result, null may be returned (defined by the query)
    * @throws DateTimeException if unable to query (defined by the query)
    * @throws ArithmeticException if numeric overflow occurs (defined by the query)
    */
  override def query[R >: Null](query: TemporalQuery[R]): R =
    query match {
      case TemporalQueries.chronology => IsoChronology.INSTANCE.asInstanceOf[R]
      case TemporalQueries.precision  => NANOS.asInstanceOf[R]
      case TemporalQueries.offset
         | TemporalQueries.zone       => getOffset.asInstanceOf[R]
      case TemporalQueries.localDate  => toLocalDate.asInstanceOf[R]
      case TemporalQueries.localTime  => toLocalTime.asInstanceOf[R]
      case TemporalQueries.zoneId     => null
      case _                          => super.query(query)
    }

  /** Adjusts the specified temporal object to have the same offset, date
    * and time as this object.
    *
    * This returns a temporal object of the same observable type as the input
    * with the offset, date and time changed to be the same as this.
    *
    * The adjustment is equivalent to using {@link Temporal#with(TemporalField, long)}
    * three times, passing {@link ChronoField#EPOCH_DAY},
    * {@link ChronoField#NANO_OF_DAY} and {@link ChronoField#OFFSET_SECONDS} as the fields.
    *
    * In most cases, it is clearer to reverse the calling pattern by using
    * {@link Temporal#with(TemporalAdjuster)}:
    * <pre>
    * // these two lines are equivalent, but the second approach is recommended
    * temporal = thisOffsetDateTime.adjustInto(temporal);
    * temporal = temporal.with(thisOffsetDateTime);
    * </pre>
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param temporal  the target object to be adjusted, not null
    * @return the adjusted object, not null
    * @throws DateTimeException if unable to make the adjustment
    * @throws ArithmeticException if numeric overflow occurs
    */
  def adjustInto(temporal: Temporal): Temporal =
    temporal.`with`(EPOCH_DAY, toLocalDate.toEpochDay).`with`(NANO_OF_DAY, toLocalTime.toNanoOfDay).`with`(OFFSET_SECONDS, getOffset.getTotalSeconds)

  /** Calculates the period between this date-time and another date-time in
    * terms of the specified unit.
    *
    * This calculates the period between two date-times in terms of a single unit.
    * The start and end points are {@code this} and the specified date-time.
    * The result will be negative if the end is before the start.
    * For example, the period in days between two date-times can be calculated
    * using {@code startDateTime.until(endDateTime, DAYS)}.
    *
    * The {@code Temporal} passed to this method must be an {@code OffsetDateTime}.
    * If the offset differs between the two date-times, the specified
    * end date-time is normalized to have the same offset as this date-time.
    *
    * The calculation returns a whole number, representing the number of
    * complete units between the two date-times.
    * For example, the period in months between 2012-06-15T00:00Z and 2012-08-14T23:59Z
    * will only be one month as it is one minute short of two months.
    *
    * This method operates in association with {@link TemporalUnit#between}.
    * The result of this method is a {@code long} representing the amount of
    * the specified unit. By contrast, the result of {@code between} is an
    * object that can be used directly in addition/subtraction:
    * <pre>
    * long period = start.until(end, MONTHS);   // this method
    * dateTime.plus(MONTHS.between(start, end));      // use in plus/minus
    * </pre>
    *
    * The calculation is implemented in this method for {@link ChronoUnit}.
    * The units {@code NANOS}, {@code MICROS}, {@code MILLIS}, {@code SECONDS},
    * {@code MINUTES}, {@code HOURS} and {@code HALF_DAYS}, {@code DAYS},
    * {@code WEEKS}, {@code MONTHS}, {@code YEARS}, {@code DECADES},
    * {@code CENTURIES}, {@code MILLENNIA} and {@code ERAS} are supported.
    * Other {@code ChronoUnit} values will throw an exception.
    *
    * If the unit is not a {@code ChronoUnit}, then the result of this method
    * is obtained by invoking {@code TemporalUnit.between(Temporal, Temporal)}
    * passing {@code this} as the first argument and the input temporal as
    * the second argument.
    *
    * This instance is immutable and unaffected by this method call.
    *
    * @param endExclusive  the end date-time, which is converted to an { @code OffsetDateTime}, not null
    * @param unit  the unit to measure the period in, not null
    * @return the amount of the period between this date-time and the end date-time
    * @throws DateTimeException if the period cannot be calculated
    * @throws ArithmeticException if numeric overflow occurs
    */
  def until(endExclusive: Temporal, unit: TemporalUnit): Long = {
    var end: OffsetDateTime = OffsetDateTime.from(endExclusive)
    if (unit.isInstanceOf[ChronoUnit]) {
      end = end.withOffsetSameInstant(offset)
      dateTime.until(end.dateTime, unit)
    } else
      unit.between(this, end)
  }

  /** Combines this date-time with a time-zone to create a {@code ZonedDateTime}
    * ensuring that the result has the same instant.
    *
    * This returns a {@code ZonedDateTime} formed from this date-time and the specified time-zone.
    * This conversion will ignore the visible local date-time and use the underlying instant instead.
    * This avoids any problems with local time-line gaps or overlaps.
    * The result might have different values for fields such as hour, minute an even day.
    *
    * To attempt to retain the values of the fields, use {@link #atZoneSimilarLocal(ZoneId)}.
    * To use the offset as the zone ID, use {@link #toZonedDateTime()}.
    *
    * @param zone  the time-zone to use, not null
    * @return the zoned date-time formed from this date-time, not null
    */
  def atZoneSameInstant(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofInstant(dateTime, offset, zone)

  /** Combines this date-time with a time-zone to create a {@code ZonedDateTime}
    * trying to keep the same local date and time.
    *
    * This returns a {@code ZonedDateTime} formed from this date-time and the specified time-zone.
    * Where possible, the result will have the same local date-time as this object.
    *
    * Time-zone rules, such as daylight savings, mean that not every time on the
    * local time-line exists. If the local date-time is in a gap or overlap according to
    * the rules then a resolver is used to determine the resultant local time and offset.
    * This method uses {@link ZonedDateTime#ofLocal(LocalDateTime, ZoneId, ZoneOffset)}
    * to retain the offset from this instance if possible.
    *
    * Finer control over gaps and overlaps is available in two ways.
    * If you simply want to use the later offset at overlaps then call
    * {@link ZonedDateTime#withLaterOffsetAtOverlap()} immediately after this method.
    *
    * To create a zoned date-time at the same instant irrespective of the local time-line,
    * use {@link #atZoneSameInstant(ZoneId)}.
    * To use the offset as the zone ID, use {@link #toZonedDateTime()}.
    *
    * @param zone  the time-zone to use, not null
    * @return the zoned date-time formed from this date and the earliest valid time for the zone, not null
    */
  def atZoneSimilarLocal(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofLocal(dateTime, zone, offset)

  /** Gets the {@code LocalDateTime} part of this offset date-time.
    *
    * This returns a {@code LocalDateTime} with the same year, month, day and time
    * as this date-time.
    *
    * @return the local date-time part of this date-time, not null
    */
  def toLocalDateTime: LocalDateTime = dateTime

  /** Gets the {@code LocalDate} part of this date-time.
    *
    * This returns a {@code LocalDate} with the same year, month and day
    * as this date-time.
    *
    * @return the date part of this date-time, not null
    */
  def toLocalDate: LocalDate = dateTime.toLocalDate

  /** Gets the {@code LocalTime} part of this date-time.
    *
    * This returns a {@code LocalTime} with the same hour, minute, second and
    * nanosecond as this date-time.
    *
    * @return the time part of this date-time, not null
    */
  def toLocalTime: LocalTime = dateTime.toLocalTime

  /** Converts this date-time to an {@code OffsetTime}.
    *
    * This returns an offset time with the same local time and offset.
    *
    * @return an OffsetTime representing the time and offset, not null
    */
  def toOffsetTime: OffsetTime = OffsetTime.of(dateTime.toLocalTime, offset)

  /** Converts this date-time to a {@code ZonedDateTime} using the offset as the zone ID.
    *
    * This creates the simplest possible {@code ZonedDateTime} using the offset
    * as the zone ID.
    *
    * To control the time-zone used, see {@link #atZoneSameInstant(ZoneId)} and
    * {@link #atZoneSimilarLocal(ZoneId)}.
    *
    * @return a zoned date-time representing the same local date-time and offset, not null
    */
  def toZonedDateTime: ZonedDateTime = ZonedDateTime.of(dateTime, offset)

  /** Converts this date-time to an {@code Instant}.
    *
    * @return an { @code Instant} representing the same instant, not null
    */
  def toInstant: Instant = dateTime.toInstant(offset)

  /** Converts this date-time to the number of seconds from the epoch of 1970-01-01T00:00:00Z.
    *
    * This allows this date-time to be converted to a value of the
    * {@link ChronoField#INSTANT_SECONDS epoch-seconds} field. This is primarily
    * intended for low-level conversions rather than general application usage.
    *
    * @return the number of seconds from the epoch of 1970-01-01T00:00:00Z
    */
  def toEpochSecond: Long = dateTime.toEpochSecond(offset)

  /** Compares this {@code OffsetDateTime} to another date-time.
    *
    * The comparison is based on the instant then on the local date-time.
    * It is "consistent with equals", as defined by {@link Comparable}.
    *
    * For example, the following is the comparator order:
    * <ol>
    * <li>{@code 2008-12-03T10:30+01:00}</li>
    * <li>{@code 2008-12-03T11:00+01:00}</li>
    * <li>{@code 2008-12-03T12:00+02:00}</li>
    * <li>{@code 2008-12-03T11:30+01:00}</li>
    * <li>{@code 2008-12-03T12:00+01:00}</li>
    * <li>{@code 2008-12-03T12:30+01:00}</li>
    * </ol>
    * Values #2 and #3 represent the same instant on the time-line.
    * When two values represent the same instant, the local date-time is compared
    * to distinguish them. This step is needed to make the ordering
    * consistent with {@code equals()}.
    *
    * @param other  the other date-time to compare to, not null
    * @return the comparator value, negative if less, positive if greater
    */
  def compare(other: OffsetDateTime): Int = {
    if (getOffset == other.getOffset)
      return toLocalDateTime.compareTo(other.toLocalDateTime)
    var cmp: Int = java.lang.Long.compare(toEpochSecond, other.toEpochSecond)
    if (cmp == 0) {
      cmp = toLocalTime.getNano - other.toLocalTime.getNano
      if (cmp == 0)
        cmp = toLocalDateTime.compareTo(other.toLocalDateTime)
    }
    cmp
  }

  /** Checks if the instant of this date-time is after that of the specified date-time.
    *
    * This method differs from the comparison in {@link #compareTo} and {@link #equals} in that it
    * only compares the instant of the date-time. This is equivalent to using
    * {@code dateTime1.toInstant().isAfter(dateTime2.toInstant());}.
    *
    * @param other  the other date-time to compare to, not null
    * @return true if this is after the instant of the specified date-time
    */
  def isAfter(other: OffsetDateTime): Boolean = {
    val thisEpochSec: Long = toEpochSecond
    val otherEpochSec: Long = other.toEpochSecond
    thisEpochSec > otherEpochSec || (thisEpochSec == otherEpochSec && toLocalTime.getNano > other.toLocalTime.getNano)
  }

  /** Checks if the instant of this date-time is before that of the specified date-time.
    *
    * This method differs from the comparison in {@link #compareTo} in that it
    * only compares the instant of the date-time. This is equivalent to using
    * {@code dateTime1.toInstant().isBefore(dateTime2.toInstant());}.
    *
    * @param other  the other date-time to compare to, not null
    * @return true if this is before the instant of the specified date-time
    */
  def isBefore(other: OffsetDateTime): Boolean = {
    val thisEpochSec: Long = toEpochSecond
    val otherEpochSec: Long = other.toEpochSecond
    thisEpochSec < otherEpochSec || (thisEpochSec == otherEpochSec && toLocalTime.getNano < other.toLocalTime.getNano)
  }

  /** Checks if the instant of this date-time is equal to that of the specified date-time.
    *
    * This method differs from the comparison in {@link #compareTo} and {@link #equals}
    * in that it only compares the instant of the date-time. This is equivalent to using
    * {@code dateTime1.toInstant().equals(dateTime2.toInstant());}.
    *
    * @param other  the other date-time to compare to, not null
    * @return true if the instant equals the instant of the specified date-time
    */
  def isEqual(other: OffsetDateTime): Boolean =
    toEpochSecond == other.toEpochSecond && toLocalTime.getNano == other.toLocalTime.getNano

  /** Checks if this date-time is equal to another date-time.
    *
    * The comparison is based on the local date-time and the offset.
    * To compare for the same instant on the time-line, use {@link #isEqual}.
    * Only objects of type {@code OffsetDateTime} are compared, other types return false.
    *
    * @param obj  the object to check, null returns false
    * @return true if this is equal to the other date-time
    */
  override def equals(obj: Any): Boolean =
    obj match {
      case other: OffsetDateTime => (this eq other) || ((dateTime == other.dateTime) && (offset == other.offset))
      case _                     => false
    }

  /** A hash code for this date-time.
    *
    * @return a suitable hash code
    */
  override def hashCode: Int = dateTime.hashCode ^ offset.hashCode

  /** Outputs this date-time as a {@code String}, such as {@code 2007-12-03T10:15:30+01:00}.
    *
    * The output will be one of the following ISO-8601 formats:
    *<ul>
    * <li>{@code yyyy-MM-dd'T'HH:mmXXXXX}</li>
    * <li>{@code yyyy-MM-dd'T'HH:mm:ssXXXXX}</li>
    * <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX}</li>
    * <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXXXX}</li>
    * <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXXXX}</li>
    * </ul><p>
    * The format used will be the shortest that outputs the full value of
    * the time where the omitted parts are implied to be zero.
    *
    * @return a string representation of this date-time, not null
    */
  override def toString: String = dateTime.toString + offset.toString

  /** Outputs this date-time as a {@code String} using the formatter.
    *
    * This date-time will be passed to the formatter
    * {@link DateTimeFormatter#format(TemporalAccessor) print method}.
    *
    * @param formatter  the formatter to use, not null
    * @return the formatted date-time string, not null
    * @throws DateTimeException if an error occurs during printing
    */
  def format(formatter: DateTimeFormatter): String = {
    Objects.requireNonNull(formatter, "formatter")
    formatter.format(this)
  }

  private def writeReplace: AnyRef = new Ser(Ser.OFFSET_DATE_TIME_TYPE, this)

  /** Defend against malicious streams.
    *
    * @return never
    * @throws InvalidObjectException always
    */
  @throws[ObjectStreamException]
  private def readResolve: AnyRef = throw new InvalidObjectException("Deserialization via serialization delegate")

  @throws[IOException]
  private[bp] def writeExternal(out: DataOutput): Unit = {
    dateTime.writeExternal(out)
    offset.writeExternal(out)
  }
}