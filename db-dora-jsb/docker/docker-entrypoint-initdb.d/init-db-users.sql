-- https://www.postgresql.org/docs/8.0/sql-createuser.html
-- Initialization scripts: https://hub.docker.com/_/postgres

CREATE DATABASE edi;
CREATE DATABASE cbs;

CREATE USER edi WITH PASSWORD 'edi';
GRANT ALL PRIVILEGES ON DATABASE edi TO edi;

CREATE USER cbs_msg WITH PASSWORD 'cbs_msg';
GRANT ALL PRIVILEGES ON DATABASE cbs TO cbs_msg;

CREATE USER cbs_mtf WITH PASSWORD 'cbs_mtf';
GRANT ALL PRIVILEGES ON DATABASE cbs TO cbs_mtf;

CREATE USER cbs_client WITH PASSWORD 'cbs';
GRANT ALL PRIVILEGES ON DATABASE cbs TO cbs_client;
