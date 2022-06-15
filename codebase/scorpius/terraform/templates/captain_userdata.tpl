#cloud-config
environment:
  environment: ${environment}
  dcos_username: ${dcos_username}
  dcos_password: ${dcos_password}
  dcos_nodes: ${dcos_nodes}
  dcos_master_url: ${dcos_master_url}
  aws_s3_bucket: ${dcos_apps_bucket}
  aws_s3_bucket_domain: ${dcos_apps_bucket_domain}
  online_prediction_sqs_queue: ${online_prediction_sqs_queue}
  app_aws_access_key_id: ${apps_aws_access_key}
  app_aws_secret_access_key: ${apps_aws_secret_key}
  aws_default_region: ${aws_region}
  dcos_master: ${dcos_master_url}
  baile_lb_url: ${baile_lb_url}
  baile_internal_lb_url: ${baile_internal_lb_url}
  base_jupyter_lab_domain: ${base_jupyter_lab_domain}
  gemini_http_auth_password: ${gemini_http_auth_password}
  zookeeper_url: "${dcos_master_url}:2181"
  marathon_client_marathon_endpoint: "http://${dcos_master_url}:8080"
  master_instance_name: ${master_instance_name}
  rabbit_password: ${rabbit_password}
  aries_http_search_user_password: ${aries_http_search_user_password}
  aries_http_command_user_password: ${aries_http_command_user_password}
  argo_http_auth_user_password: ${argo_http_auth_user_password}
  cortex_http_search_user_password: ${cortex_http_search_user_password}
  online_prediction_password: ${online_prediction_password}
  online_prediction_stream_id: ${online_prediction_stream_id}
  orion_http_search_user_password: ${orion_http_search_user_password}
  pegasus_http_auth_user_password: ${pegasus_http_auth_user_password}
  mongodb_app_password: ${mongodb_app_password}
  mongodb_rootadmin_password: ${mongodb_rootadmin_password}
  mongodb_useradmin_password: ${mongodb_useradmin_password}
  mongodb_clusteradmin_password: ${mongodb_clusteradmin_password}
  mongodb_clustermonitor_password: ${mongodb_clustermonitor_password}
  mongodb_backup_password: ${mongodb_backup_password}
  baile_http_auth_user_password: ${baile_http_auth_user_password}
  argo_docker_image_version: ${argo_docker_image_version}
  aries_docker_image_version: ${aries_docker_image_version}
  baile_docker_image_version: ${baile_docker_image_version}
  baile_haproxy_docker_image_version: ${baile_haproxy_docker_image_version}
  cortex_docker_image_version: ${cortex_docker_image_version}
  cortex_task_docker_image_version: ${cortex_task_docker_image_version}
  logstash_docker_image_version: ${logstash_docker_image_version}
  orion_docker_image_version: ${orion_docker_image_version}
  job_master_docker_image_version: ${job_master_docker_image_version}
  pegasus_docker_image_version: ${pegasus_docker_image_version}
  rmq_docker_image_version: ${rmq_docker_image_version}
  scorpius_mongobackup_version: ${scorpius_mongobackup_version}
  taurus_docker_image_version: ${taurus_docker_image_version}
  um_docker_image_version: ${um_docker_image_version}
  salsa_version: ${salsa_version}
  jupyter_lab_docker_image_version: ${jupyter_lab_docker_image_version}
  gemini_docker_image_version: ${gemini_docker_image_version}
  sql_server_docker_image_version: ${sql_server_docker_image_version}
  baile_api_path: "${baile_api_path}"
  baile_private_api_path: "${baile_private_api_path}"
  sql_server_api_path: "${sql_server_api_path}"
  baile_haproxy_vpc_dns: ${baile_haproxy_vpc_dns}
  baile_haproxy_dns_resolver: ${baile_haproxy_dns_resolver}
  upload_datasets: "${upload_datasets}"
  download_from_s3: "${download_from_s3}"
  online_prediction: "${online_prediction}"
  s3_endpoint: ${s3_endpoint}
  artifacts_s3_bucket: ${artifacts_s3_bucket}
  email_on: "${email_on}"
  smtp_aws_access_key_id: "${smtp_aws_access_key_id}"
  smtp_aws_secret_access_key: "${smtp_aws_secret_access_key}"
  aws_ca_bundle: "${aws_ca_bundle}"
  percona_mongo_version: "${percona_mongo_version}"
  elastic_version: "${elastic_version}"
  kibana_version: "${kibana_version}"
  marathon_lb_version: "${marathon_lb_version}"
  terraform_log_file: "${terraform_log_file}"
  deployment_logs_s3_path: "${deployment_logs_s3_path}"
  dcos_apps_bucket: ${dcos_apps_bucket}
  mongo_restore_date: "${mongo_restore_date}"
  docker_tar_list: "${docker_tar_list}"
  main_iam_role: "${main_iam_role}"
  jump_role: "${jump_role}"
  aws_arn_partition: "${aws_arn_partition}"
manage_resolv_conf: false
preserve_hostname: true
runcmd:
  - instanceid=$(curl -s http://169.254.169.254/latest/meta-data/instance-id | tr -d 'i-')
  - hostn=$(cat /etc/hostname)
  - echo "`date '+%b %d  %H:%M:%S'` [captain_userdata] Existing hostname is $hostn"
  - newhostn="captain-$instanceid"
  - sed -i "s/localhost/$newhostn/g" /etc/hosts
  - sed -i "s/$hostn/$newhostn/g" /etc/hostname
  - hostnamectl set-hostname $newhostn
  - echo "`date '+%b %d  %H:%M:%S'` [captain_userdata] New hostname is $newhostn"
  - service rsyslog restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [captain_userdata] rsyslog service restarted.."
  - service ntpd restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [captain_userdata] ntpd service restarted.."
  - if [[ ${install_amazon_ssm_agent} = true ]];then systemctl start amazon-ssm-agent; fi
  - if [[ ${download_ssh_keys} = true ]]; then aws s3 cp s3://${ssh_keys_s3_bucket} - >> /home/${main_user}/.ssh/authorized_keys; fi
  - DATE=`date +%Y%m%d%H%M%S`
  - set -o noglob
  - source /opt/cronjob_helper.sh
  - log_collector="* * * * * cat ${terraform_log_file} | grep -e ansible -e CAPTAIN -e captain_userdata > /tmp/scorpius.log"
  - log_forwarder_s3="* * * * *  `which aws` s3 cp /tmp/scorpius.log  s3://${dcos_apps_bucket}/${deployment_logs_s3_path}/`date +%Y%m%d`/$newhostn-$DATE"
  - echo $log_collector | add_cronjob
  - echo $log_forwarder_s3 | add_cronjob
  - set +o noglob
  - echo "crontab has configured the cronjob which is scheduled to move the ${terraform_log_file} file to S3"
output : { all : '| tee -a /var/log/cloud-init-output.log' }
