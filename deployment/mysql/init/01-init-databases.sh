#!/bin/bash
set -e

escape_sql_string() {
  printf "%s" "$1" | sed "s/'/''/g"
}

lottery_user="$(escape_sql_string "${LOTTERY_DB_USERNAME:-lottery_user}")"
lottery_password="$(escape_sql_string "${LOTTERY_DB_PASSWORD}")"
gobang_user="$(escape_sql_string "${GOBANG_DB_USERNAME:-gobang_user}")"
gobang_password="$(escape_sql_string "${GOBANG_DB_PASSWORD}")"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
  CREATE DATABASE IF NOT EXISTS lottery_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  CREATE DATABASE IF NOT EXISTS java_gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

  CREATE USER IF NOT EXISTS '${lottery_user}'@'%' IDENTIFIED BY '${lottery_password}';
  CREATE USER IF NOT EXISTS '${gobang_user}'@'%' IDENTIFIED BY '${gobang_password}';

  GRANT ALL PRIVILEGES ON lottery_system.* TO '${lottery_user}'@'%';
  GRANT ALL PRIVILEGES ON java_gobang.* TO '${gobang_user}'@'%';
  FLUSH PRIVILEGES;
EOSQL
