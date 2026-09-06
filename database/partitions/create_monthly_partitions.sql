-- create_monthly_partitions.sql
-- Function to auto-create monthly partitions for all month-partitioned tables.
-- Call: SELECT create_monthly_partitions('2027-06');  -- creates 2027-06 partition
-- Or:   SELECT create_monthly_partitions();           -- creates current month + next 2 months
--
-- Tables covered: location_history, voltage_readings, battery_readings, gsm_readings.
-- Run this as a scheduled cron job (pg_cron or external) monthly before the new month starts.

CREATE OR REPLACE FUNCTION create_monthly_partitions(p_month_start DATE DEFAULT NULL)
RETURNS VOID AS $$
DECLARE
    v_month_start DATE;
    v_month_end   DATE;
    v_yyyymm      VARCHAR(6);
    v_table_names TEXT[] := ARRAY['location_history', 'voltage_readings', 'battery_readings', 'gsm_readings'];
    v_table       TEXT;
    v_partition   TEXT;
    v_exists      BOOLEAN;
BEGIN
    IF p_month_start IS NULL THEN
        -- Default: create current month + next 2 months
        FOR i IN 0..2 LOOP
            v_month_start := date_trunc('month', now() + (i || ' month')::interval)::DATE;
            PERFORM create_monthly_partitions(v_month_start);
        END LOOP;
        RETURN;
    END IF;

    v_month_start := date_trunc('month', p_month_start)::DATE;
    v_month_end   := (v_month_start + INTERVAL '1 month')::DATE;
    v_yyyymm      := to_char(v_month_start, 'YYYYMM');

    FOREACH v_table IN ARRAY v_table_names LOOP
        v_partition := v_table || '_' || v_yyyymm;

        -- Check if partition already exists
        SELECT EXISTS (
            SELECT 1 FROM pg_tables WHERE tablename = v_partition
        ) INTO v_exists;

        IF NOT v_exists THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
                v_partition, v_table, v_month_start, v_month_end
            );

            -- Add composite index (machine_id + recorded_at DESC) on the new partition.
            EXECUTE format(
                'CREATE INDEX %I ON %I(machine_id, recorded_at DESC)',
                'idx_' || v_partition || '_machine_recorded', v_partition
            );

            RAISE NOTICE 'Created partition % for %', v_partition, v_table;
        ELSE
            RAISE NOTICE 'Partition % already exists, skipping', v_partition;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION create_monthly_partitions IS 'Auto-create monthly partitions for location_history, voltage_readings, battery_readings, gsm_readings. Call with a month-start date or no arg for current+next 2 months.';
