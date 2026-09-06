-- archive_old_partitions.sql
-- Functions to detach and archive old partitions.
--
-- detach_old_partition(table_name, month_start)
--   - DETACHes the partition from the parent table
--   - Renames it to <table>_<yyyymm>_archived
--   - The archived table remains in the same DB (can be moved to cold storage separately)
--
-- archive_partitions_older_than(table_name, cutoff_date)
--   - Detaches all partitions older than cutoff_date for the given table
--
-- Run as a scheduled job (e.g. monthly) to enforce retention policy.
-- Hot data (3 months) stays attached; older data is detached/archived.

CREATE OR REPLACE FUNCTION detach_old_partition(
    p_table_name  TEXT,
    p_month_start DATE
)
RETURNS VOID AS $$
DECLARE
    v_yyyymm      VARCHAR(6);
    v_partition   TEXT;
    v_archived    TEXT;
    v_exists      BOOLEAN;
BEGIN
    v_yyyymm    := to_char(p_month_start, 'YYYYMM');
    v_partition := p_table_name || '_' || v_yyyymm;
    v_archived  := v_partition || '_archived';

    SELECT EXISTS (
        SELECT 1 FROM pg_tables WHERE tablename = v_partition
    ) INTO v_exists;

    IF NOT v_exists THEN
        RAISE NOTICE 'Partition % does not exist, skipping', v_partition;
        RETURN;
    END IF;

    -- Detach from parent
    EXECUTE format('ALTER TABLE %I DETACH PARTITION %I', p_table_name, v_partition);

    -- Rename to _archived suffix
    EXECUTE format('ALTER TABLE %I RENAME TO %I', v_partition, v_archived);

    RAISE NOTICE 'Detached and archived % -> %', v_partition, v_archived;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION detach_old_partition IS 'Detach a single monthly partition from its parent and rename it to _archived.';


CREATE OR REPLACE FUNCTION archive_partitions_older_than(
    p_table_name  TEXT,
    p_cutoff_date DATE
)
RETURNS VOID AS $$
DECLARE
    v_partition_rec RECORD;
BEGIN
    FOR v_partition_rec IN
        SELECT
            c.relname AS partition_name,
            pg_get_expr(c.relpartbound, c.oid) AS bound_expr
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        JOIN pg_class p ON p.oid = i.inhparent
        JOIN pg_namespace n ON n.oid = p.relnamespace
        WHERE p.relname = p_table_name
          AND n.nspname = current_schema()
          AND c.relname NOT LIKE '%_default'
    LOOP
        -- Only detach if the partition's range end is before the cutoff.
        -- bound_expr looks like: FOR VALUES FROM ('2026-09-01') TO ('2026-10-01')
        DECLARE
            v_range_end DATE;
        BEGIN
            v_range_end := substring(v_partition_rec.bound_expr FROM '''([0-9]{4}-[0-9]{2}-[0-9]{2})''[^'']*''[^'']*''([0-9]{4}-[0-9]{2}-[0-9]{2})')::DATE;
            IF v_range_end IS NULL THEN
                v_range_end := substring(
                    v_partition_rec.bound_expr,
                    '''([0-9]{4}-[0-9]{2}-[0-9]{2})''\)$'
                )::DATE;
            END IF;

            IF v_range_end IS NOT NULL AND v_range_end < p_cutoff_date THEN
                -- Extract yyyymm from partition name
                DECLARE
                    v_yyyymm VARCHAR(6);
                    v_month_start DATE;
                BEGIN
                    v_yyyymm := substring(v_partition_rec.partition_name FROM '([0-9]{6})$');
                    v_month_start := make_date(
                        substring(v_yyyymm FROM 1 FOR 4)::INT,
                        substring(v_yyyymm FROM 5 FOR 2)::INT,
                        1
                    );
                    PERFORM detach_old_partition(p_table_name, v_month_start);
                END;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Skipping %: could not parse range bounds (%)',
                v_partition_rec.partition_name, SQLERRM;
        END;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive_partitions_older_than IS 'Detach and archive all monthly partitions of a table older than the cutoff date. Skips the default partition.';
