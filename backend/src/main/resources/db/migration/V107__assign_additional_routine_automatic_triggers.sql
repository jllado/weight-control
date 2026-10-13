update routines
set automatic_trigger = case binary name
        when 'SLEEP 7 HOURS' then 'SLEEP_BED_AT_LEAST_7_HOURS'
        when 'Kiwi' then 'KIWI_MEAL'
        when 'Wake up early' then 'WAKE_BEFORE_7'
        when 'Night stretching' then 'NIGHT_STRETCHING_WORKOUT'
        when 'Sitting-Rising' then 'SITTING_RISING_WORKOUT'
    end,
    automatic_completion_enabled = true,
    updated_at = updated_at
where automatic_trigger = 'NONE'
    and binary name in ('SLEEP 7 HOURS', 'Kiwi', 'Wake up early', 'Night stretching', 'Sitting-Rising');
