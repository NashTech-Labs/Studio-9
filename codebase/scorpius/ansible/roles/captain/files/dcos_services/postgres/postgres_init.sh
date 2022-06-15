#!/bin/bash

export PGPASSWORD=$REDSHIFT_PASSWORD

psql -h "${REDSHIFT_HOST}" -U deepcortex -d dev -p 5439 -c 'create schema if not exists predictions;'
psql -h "${REDSHIFT_HOST}" -U deepcortex -d dev -p 5439 -c 'create table if not exists predictions.cv_predictions (
    job_id varchar(50) not null sortkey,
    model_id varchar(50) not null,
    album_path varchar(50) not null,
    file_name varchar(30) not null,
    file_path varchar(100) not null,
    file_size integer not null,
    label varchar(20) not null,
    confidence decimal not null,
    stream_id varchar(50) not null,
    owner varchar(50) not null,
    album_id varchar(50) not null,
    created_at timestamptz not null,
    created_by varchar(20) not null
) diststyle even;'