CREATE ROLE cloudpos_owner LOGIN PASSWORD 'cloudpos';
CREATE ROLE cloudpos_app   LOGIN PASSWORD 'cloudpos';

\set services '{identity,catalog,order,inventory,payment,billing,reporting}'

CREATE DATABASE identity  OWNER cloudpos_owner;
CREATE DATABASE catalog   OWNER cloudpos_owner;
CREATE DATABASE "order"   OWNER cloudpos_owner;
CREATE DATABASE inventory OWNER cloudpos_owner;
CREATE DATABASE payment   OWNER cloudpos_owner;
CREATE DATABASE billing   OWNER cloudpos_owner;

\connect identity
GRANT USAGE ON SCHEMA public TO cloudpos_app;
ALTER DEFAULT PRIVILEGES FOR ROLE cloudpos_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO cloudpos_app;
