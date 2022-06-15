#cloud-config
environment:
  environment: ${environment}
  aws_default_region: ${aws_region}
  aws_ca_bundle: "${aws_ca_bundle}"
  terraform_log_file: "${terraform_log_file}"
  deployment_logs_s3_path: "${deployment_logs_s3_path}"
  dcos_apps_bucket: "${dcos_apps_bucket}"
manage_resolv_conf: false
preserve_hostname: true
runcmd:
  - instanceid=$(curl -s http://169.254.169.254/latest/meta-data/instance-id | tr -d 'i-')
  - hostn=$(cat /etc/hostname)
  - echo "`date '+%b %d  %H:%M:%S'` [bootstrap_userdata] Existing hostname is $hostn"
  - newhostn="bootstrap-$instanceid"
  - sed -i "s/localhost/$newhostn/g" /etc/hosts
  - sed -i "s/$hostn/$newhostn/g" /etc/hostname
  - hostnamectl set-hostname $newhostn
  - echo "`date '+%b %d  %H:%M:%S'` [bootstrap_userdata] New hostname is $newhostn"
  - service rsyslog restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [bootstrap_userdata] rsyslog service restarted.."
  - service ntpd restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [bootstrap_userdata] ntpd service restarted.."
  - if [[ ${install_amazon_ssm_agent} = true ]];then systemctl start amazon-ssm-agent; fi
  - export AWS_DEFAULT_REGION=${aws_region}
  - sed -i "s/cluster_name_via_user_data/${cluster_name}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/s3_bucket_via_user_data/${s3_bucket}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/s3_prefix_via_user_data/${s3_prefix}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/provider_dns_via_user_data/${dns_ip}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/num_masters_via_user_data/${num_masters}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/masters_elb_dns_via_user_data/${masters_elb}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/bootstrap_dns_via_user_data/${bootstrap_dns}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/aws_region_via_user_data/${aws_region}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/dcos_username_via_user_data/${dcos_username}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s,docker_registry_url_via_user_data,${docker_registry_url},g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/docker_email_login_via_user_data/${docker_email_login}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - sed -i "s/docker_registry_auth_token_via_user_data/${docker_registry_auth_token}/g" /var/lib/dcos-bootstrap/genconf/config.yaml
  - if [[ ${download_ssh_keys} = true ]]; then aws s3 cp s3://${ssh_keys_s3_bucket} - >> /home/${main_user}/.ssh/authorized_keys; fi
  - service docker start
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [bootstrap_userdata] Docker service started.."
  - cd /var/lib/dcos-bootstrap; bash dcos_generate_config.sh --set-superuser-password ${dcos_password}
  - cd /var/lib/dcos-bootstrap; bash dcos_generate_config.sh
  - docker run -d --name dcos_haproxy -p 8080:80 -v /var/lib/dcos-bootstrap/genconf/serve:/usr/local/apache2/htdocs/:ro httpd:2.4.23
  - DATE=`date +%Y%m%d%H%M%S`
  - set -o noglob
  - source /opt/cronjob_helper.sh
  - log_collector="* * * * * cat ${terraform_log_file} | grep -e ansible -e BOOTSTRAP -e bootstrap_userdata >> /tmp/scorpius.log"
  - log_forwarder_s3="* * * * *  `which aws` s3 cp /tmp/scorpius.log  s3://${dcos_apps_bucket}/${deployment_logs_s3_path}/`date +%Y%m%d`/$newhostn-$DATE"
  - echo $log_collector | add_cronjob
  - echo $log_forwarder_s3 | add_cronjob
  - echo "removing cronjobs.."
  - log_remover="/tmp/scorpius.log"
  - echo "*/10 * * * * sudo crontab -l | grep -v "\"$log_remover"\" | sudo crontab -" | add_cronjob
  - echo "*/12 * * * * sudo crontab -l | grep -v "\"$log_forwarder_s3"\" | sudo crontab -"  | add_cronjob
  - set +o noglob
  - echo "crontab has configured the cronjob which is scheduled to move the ${terraform_log_file} file to S3"
output : { all : '| tee -a /var/log/cloud-init-output.log' }
