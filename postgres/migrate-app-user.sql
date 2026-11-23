DO $$
DECLARE
    schema_name text := current_schema();
    old_table regclass := to_regclass(format('%I.app_user', schema_name));
BEGIN
    -- The old Lab 3 table has only these three columns.
    IF (SELECT array_agg(attname::text ORDER BY attname)
        FROM pg_attribute
        WHERE attrelid = old_table AND attnum > 0 AND NOT attisdropped)
        IS DISTINCT FROM ARRAY['password_hash', 'role', 'username'] THEN
        RETURN;
    END IF;

    EXECUTE format('ALTER TABLE %I.app_user RENAME TO lab3_app_user', schema_name);
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = old_table AND conname = 'app_user_pkey' AND contype = 'p'
    ) THEN
        EXECUTE format('ALTER TABLE %I.lab3_app_user RENAME CONSTRAINT app_user_pkey TO lab3_app_user_pkey',
                       schema_name);
    END IF;
END
$$;
