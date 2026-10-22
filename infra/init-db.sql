-- Database initialization for Digital Banking Microservices
CREATE DATABASE account_db;
CREATE DATABASE transaction_db;
CREATE DATABASE payment_db;

GRANT ALL PRIVILEGES ON DATABASE account_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE transaction_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO postgres;
