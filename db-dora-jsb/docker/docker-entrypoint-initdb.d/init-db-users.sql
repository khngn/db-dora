-- https://www.postgresql.org/docs/8.0/sql-createuser.html
-- Initialization scripts: https://hub.docker.com/_/postgres

CREATE DATABASE cbs;

GRANT ALL PRIVILEGES ON DATABASE cbs TO dora;

-- For migration scripts
CREATE USER cbs_mtf WITH PASSWORD 'cbs_mtf';
GRANT ALL PRIVILEGES ON DATABASE cbs TO cbs_mtf;
