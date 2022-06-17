#!/usr/bin/env bash

export REDSHIFT_DB=""
export REDSHIFT_HOST=""
export REDSHIFT_PORT=""
export REDSHIFT_USER=""
export REDSHIFT_PASSWORD=""
export S3_ACCESS_KEY=""
export S3_SECRET_KEY=""
export S3_BUCKET=""
export S3_REGION=""
export REDSHIFT_TEST_TABLE=""
export REDSHIFT_TEST_TABLE_SCHEMA=""
export S3_TEST_PATH=""
export S3_TABLES_DIR=""
export DREMIO_URL=""
export DREMIO_USERNAME=""
export DREMIO_PASSWORD=""
export DREMIO_SOURCE=""
export DREMIO_TEST_TABLE_SCHEMA=""
export DREMIO_TEST_TABLE_NAME=""
export DREMIO_EXPORTING_CHUNKSIZE=1000000

# to run use "source e2e_testenv.sh"
# the env is needed for tests like E2ERedshiftExporterTest
