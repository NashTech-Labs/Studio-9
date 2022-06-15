create schema predictions;

-- TODO adjust limits better
create table predictions.cv_predictions (
	job_id varchar(50) not null sortkey,
	model_id varchar(50) not null,
	album_path varchar(255) not null,
	file_name varchar(255) not null,
	file_path varchar(255) not null,
	file_size integer not null,
	label varchar(255) not null,
	confidence decimal not null,

	stream_id varchar(50) not null,
	owner varchar(50) not null,
	album_id varchar(50) not null,
	created_at timestamptz not null,
	created_by varchar(20) not null
) diststyle even;
