package javax.time.builder;

import static javax.time.MathUtils.safeToInt;

import javax.time.Duration;
import javax.time.LocalDate;
import javax.time.LocalDateTime;
import javax.time.LocalTime;
import javax.time.calendrical.DateTimeRuleRange;
import javax.time.chronology.StandardChronology;

/**
 * The Coptic calendar system.
 * <p>
 * This {@link StandardChronology standard} chronology defines the rules of the Coptic calendar system.
 * The Coptic calendar has twelve months of 30 days followed by an additional
 * period of 5 or 6 days, modeled as the thirteenth month in this implementation.
 * <p>
 * Years are measured in the 'Era of the Martyrs' - AM.
 * 0001-01-01 (Coptic) equals 0284-08-29 (ISO).
 * The supported range is from 1 to 99999999 (inclusive) in both eras.
 * <p>
 * This class is immutable and thread-safe.
 *
 * NB: currently exploratory, represented ranges should be rechecked, ignores eras
 *
 * @author Richard Warburton
 */
public enum CopticChrono implements Chrono {

	INSTANCE;
	
	private final int MONTHS_PER_YEAR = 13;
	
    /**
     * The minimum permitted year.
     */
    private final int MIN_YEAR = -999999998;
    /**
     * The maximum permitted year.
     */
    private final int MAX_YEAR = 999999999;
    /**
     * The minimum permitted epoch-month.
     */
    private final long MIN_EPOCH_MONTH = (MIN_YEAR - 1970L) * MONTHS_PER_YEAR;
    /**
     * The maximum permitted epoch-month.
     */
    private final long MAX_EPOCH_MONTH = (MAX_YEAR - 1970L) * MONTHS_PER_YEAR - 1L;
    /**
     * The minimum permitted epoch-day.
     */
    private final long MIN_EPOCH_DAY = 0;
    /**
     * The maximum permitted epoch-day.
     */
    private final long MAX_EPOCH_DAY = 0;
	
	@Override
	public String getName() {
		return "Coptic";
	}

	@Override
	public DateTimeRuleRange getRange(DateTimeField field) {
		if (field instanceof StandardDateTimeField) {
            switch ((StandardDateTimeField) field) {
            	// FIXME: Should this represent Before/During Era of the Martyrs?
                case ERA: return DateTimeRuleRange.of(MIN_YEAR, MAX_YEAR);   // TODO
                case YEAR: return DateTimeRuleRange.of(MIN_YEAR, MAX_YEAR);
                case YEAR_OF_ERA: return DateTimeRuleRange.of(1, MAX_YEAR);
                case EPOCH_MONTH: return DateTimeRuleRange.of(MIN_EPOCH_MONTH, MAX_EPOCH_MONTH);
                case MONTH_OF_YEAR: return DateTimeRuleRange.of(1, 13);
                case EPOCH_DAY: return DateTimeRuleRange.of(MIN_EPOCH_DAY, MAX_EPOCH_DAY);
                case DAY_OF_MONTH: return DateTimeRuleRange.of(1, 5, 30);
                case DAY_OF_YEAR: return DateTimeRuleRange.of(1, 365, 366);
                case DAY_OF_WEEK: return DateTimeRuleRange.of(1, 7);
                case HOUR_OF_DAY: return DateTimeRuleRange.of(0, 23);
                case MINUTE_OF_HOUR: return DateTimeRuleRange.of(0, 59);
                case SECOND_OF_MINUTE: return DateTimeRuleRange.of(0, 59);
                case MILLI_OF_SECOND: return DateTimeRuleRange.of(0, 999);
                case MICRO_OF_SECOND: return DateTimeRuleRange.of(0, 999999);
                case NANO_OF_SECOND: return DateTimeRuleRange.of(0, 999999999);
            }
        }
        return field.getRules(this).getRange(field);
	}

	@Override
	public DateTimeRuleRange getRange(DateTimeField field, LocalDate date,
			LocalTime time) {
		if (field instanceof StandardDateTimeField) {
            if (date != null) {
                switch ((StandardDateTimeField) field) {
                    case DAY_OF_MONTH:
                    	if(getMonthOfYear(date) == 13) {
                    		return DateTimeRuleRange.of(1, date.isLeapYear() ? 6 : 5);
                    	}
                    	return DateTimeRuleRange.of(1, 30);
                    case DAY_OF_YEAR:
                    	return date.isLeapYear() ? DateTimeRuleRange.of(1, 366) :  DateTimeRuleRange.of(1, 365);
                }
            }
            return getRange(field);
        }
        return field.getRules(this).getRange(field, date, time);
	}

	@Override
	public long getValue(DateTimeField field, LocalDate date, LocalTime time) {
		if (field instanceof StandardDateTimeField) {
            if (date != null) {
                switch ((StandardDateTimeField) field) {
                    case ERA: return (date.getYear() > 0 ? 1 : 0);   // TODO
                    case YEAR: return date.getYear();
                    case YEAR_OF_ERA: return (date.getYear() > 0 ? date.getYear() : -date.getYear());   // TODO
                    case EPOCH_MONTH: return ((date.getYear() - 1970) * 13L) + getMonthOfYear(date);
                    case MONTH_OF_YEAR: return getMonthOfYear(date);
                    case EPOCH_DAY: return date.toEpochDay();
                    case DAY_OF_MONTH: return getDayOfMonth(date);
                    case DAY_OF_YEAR: return date.getDayOfYear();
                    case DAY_OF_WEEK: return date.getDayOfWeek().getValue();
                }
            }
            if (time != null) {
                switch ((StandardDateTimeField) field) {
                    case HOUR_OF_DAY: return time.getHourOfDay();
                    case MINUTE_OF_HOUR: return time.getMinuteOfHour();
                    case SECOND_OF_MINUTE: return time.getSecondOfMinute();
                    case MILLI_OF_SECOND: return time.getNanoOfSecond() / 1000000;
                    case MICRO_OF_SECOND: return time.getNanoOfSecond() / 1000;
                    case NANO_OF_SECOND: return time.getNanoOfSecond();
                }
            }
        }
        return Integer.MIN_VALUE;  // TODO: exception or quiet
	}
	
	/**
	 * Abstracted common logic, readability
	 */
	private long getMonthOfYear(LocalDate date) {
		return 1 + (date.getDayOfYear() / 30);
	}
	
	private long getDayOfMonth(LocalDate date) {
		return date.getDayOfYear() - (getMonthOfYear(date) - 1) * 30;
	}

	@Override
	public LocalDate setDate(DateTimeField field, LocalDate date, long newValue) {
		if (field instanceof StandardDateTimeField) {
            StandardDateTimeField std = (StandardDateTimeField) field;
            if (getRange(field, date, null).isValidValue(newValue) == false) {
                throw new IllegalArgumentException();  // TODO
            }
            switch (std) {
	        	case ERA: {   // TODO
	                if ((date.getYear() > 0 && newValue == 0) && (date.getYear() <= 0 && newValue == 1)) {
	                    return date.withYear(1 - date.getYear());
	                }
	                return date;
	            }
	        	case YEAR: return date.withYear(safeToInt(newValue));
	            case YEAR_OF_ERA: new UnsupportedOperationException("Not implemented yet");
	            case EPOCH_MONTH: return setMonthOfYear(date.withYear(safeToInt(newValue / MONTHS_PER_YEAR)),newValue % MONTHS_PER_YEAR);
	            case MONTH_OF_YEAR: return setMonthOfYear(date,newValue);
	            case EPOCH_DAY: return LocalDate.ofEpochDay(newValue);
	            case DAY_OF_MONTH: return date.withDayOfYear(safeToInt((getMonthOfYear(date) - 1) * 30 + newValue));
	            case DAY_OF_YEAR: return date.withDayOfYear(safeToInt(newValue));
	            case DAY_OF_WEEK: return date.plusDays(newValue - date.getDayOfWeek().getValue());
	        }
		}
		return field.getRules(this).setDate(field, date, newValue);
	}
	
	/**
	 * Abstracted common logic, readability
	 */
	private LocalDate setMonthOfYear(LocalDate date, long newValue) {
		long dom = getDayOfMonth(date);
		return date.withDayOfYear(safeToInt((newValue - 1) * 30 + dom));
	}

    @Override
    public LocalTime setTime(DateTimeField field, LocalTime time, long newValue) {
        if (field instanceof StandardDateTimeField) {
            if (getRange(field, null, time).isValidValue(newValue) == false) {
                throw new IllegalArgumentException();  // TODO
            }
            switch ((StandardDateTimeField) field) {
                case HOUR_OF_DAY: return time.withHourOfDay((int) newValue);
                case MINUTE_OF_HOUR: return time.withMinuteOfHour((int) newValue);
                case SECOND_OF_MINUTE: return time.withSecondOfMinute((int) newValue);
                case MILLI_OF_SECOND: return time.withNanoOfSecond((int) newValue * 1000000);
                case MICRO_OF_SECOND: return time.withNanoOfSecond((int) newValue * 1000);
                case NANO_OF_SECOND: return time.withNanoOfSecond((int) newValue);
            }
            return time;
        }
        return null;  // TODO
    }

	@Override
	public LocalDateTime setDateTime(DateTimeField field,
			LocalDateTime dateTime, long newValue) {
		if (field instanceof StandardDateTimeField) {
            StandardDateTimeField std = (StandardDateTimeField) field;
            if (std.isDateField()) {
                return dateTime.with(setDate(field, dateTime.toLocalDate(), newValue));
            } else {
                return dateTime.with(setTime(field, dateTime.toLocalTime(), newValue));
            }
        }
        return null;  // TODO
	}

	@Override
	public LocalDate setDateLenient(DateTimeField field, LocalDate date,
			long newValue) {
		// TODO
		return null;
	}

	@Override
	public LocalTime setTimeLenient(DateTimeField field, LocalTime time,
			long newValue) {
		// TODO
		return null;
	}

	@Override
	public LocalDateTime setDateTimeLenient(DateTimeField field,
			LocalDateTime dateTime, long newValue) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LocalDate addToDate(DateTimeField field, LocalDate date, long amount) {
		long oldValue = getValue(field, date, null);
		return setDate(field, date, oldValue + amount);
	}

	@Override
	public LocalTime addToTime(DateTimeField field, LocalTime time, long amount) {
		long oldValue = getValue(field, null, time);
		return setTime(field, time, oldValue + amount);
	}

	@Override
	public LocalDateTime addToDateTime(DateTimeField field,
			LocalDateTime dateTime, long amount) {
		long oldValue = getValue(field, dateTime.toLocalDate(), dateTime.toLocalTime());
		return setDateTime(field, dateTime, oldValue + amount);
	}

	@Override
	public LocalDate rollDate(DateTimeField field, LocalDate date, long roll) {
		// Identical to ISO
		DateTimeRuleRange range = getRange(field, date, null);
		long valueRange = (range.getMaximum() - range.getMinimum()) + 1;
		long currentValue = getValue(field, date, null);
		long newValue = roll % valueRange;
		return addToDate(field, date, newValue - currentValue);
	}

	@Override
	public LocalTime rollTime(DateTimeField field, LocalTime time, long roll) {
		// Identical to ISO
		DateTimeRuleRange range = getRange(field, null, time);
		long valueRange = (range.getMaximum() - range.getMinimum()) + 1;
		long currentValue = getValue(field, null, time);
		long newValue = roll % valueRange;
		return addToTime(field, time, newValue - currentValue);
	}

	@Override
	public LocalDateTime rollDateTime(DateTimeField field, LocalDateTime dateTime, long roll) {
		// Identical to ISO
		LocalDate date = dateTime.toLocalDate();
		LocalTime time = dateTime.toLocalTime();
		DateTimeRuleRange range = getRange(field, date, time);
		long valueRange = (range.getMaximum() - range.getMinimum()) + 1;
		long currentValue = getValue(field, date, time);
		long newValue = roll % valueRange;
		return addToDateTime(field, dateTime, newValue - currentValue);
	}

	@Override
	public Duration getEstimatedDuration(PeriodUnit unit) {
		
		return null;
	}

	@Override
	public Duration getDurationBetween(LocalDate date1, LocalTime time1,
			LocalDate date2, LocalTime time2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getPeriodBetween(PeriodUnit unit, LocalDate date1,
			LocalTime time1, LocalDate date2, LocalTime time2) {
		// TODO Auto-generated method stub
		return 0;
	}
	
}