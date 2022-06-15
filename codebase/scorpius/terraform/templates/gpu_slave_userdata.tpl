#cloud-config
environment:
  environment: ${environment}
  aws_default_region: ${aws_region}
  s3_endpoint: ${s3_endpoint}
  artifacts_s3_bucket: ${artifacts_s3_bucket}
  aws_ca_bundle: "${aws_ca_bundle}"
  terraform_log_file: "${terraform_log_file}"
  deployment_logs_s3_path: "${deployment_logs_s3_path}"
  dcos_apps_bucket: "${dcos_apps_bucket}"
  vulkan_file_path: "${vulkan_file_path}"
manage_resolv_conf: false
preserve_hostname: true
runcmd:
  - instanceid=$(curl -s http://169.254.169.254/latest/meta-data/instance-id | tr -d 'i-')
  - hostn=$(cat /etc/hostname)
  - echo "`date '+%b %d  %H:%M:%S'` [gpu_slave_userdata] Existing hostname is $hostn"
  - newhostn="gpu-slave-$instanceid"
  - sed -i "s/localhost/$newhostn/g" /etc/hosts
  - sed -i "s/$hostn/$newhostn/g" /etc/hostname
  - hostnamectl set-hostname $newhostn
  - echo "`date '+%b %d  %H:%M:%S'` [gpu_slave_userdata] New hostname is $newhostn"
  - service rsyslog restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [gpu_slave_userdata] syslog service restarted.."
  - service ntpd restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn  [gpu_slave_userdata] ntpd service restarted.."
  - if [[ ${install_amazon_ssm_agent} = true ]];then systemctl start amazon-ssm-agent; fi
  - export AWS_DEFAULT_REGION=${aws_region}
  - if [[ ${download_ssh_keys} = true ]]; then aws s3 cp s3://${ssh_keys_s3_bucket} - >> /home/${main_user}/.ssh/authorized_keys; fi
  - sysctl net.bridge.bridge-nf-call-iptables=1
  - sysctl net.bridge.bridge-nf-call-ip6tables=1
  - zone_id=$(curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone)
  - mkdir -p /var/lib/dcos
  - touch /var/lib/dcos/mesos-slave-common || exit
  - echo "MESOS_ATTRIBUTES='az_id:$zone_id;cluster:gpu;'" > /var/lib/dcos/mesos-slave-common
  - until $(curl --output /dev/null --silent --head --fail http://${bootstrap_dns}:8080/dcos_install.sh); do sleep 5; done
  - curl http://${bootstrap_dns}:8080/dcos_install.sh -o /tmp/dcos_install.sh -s
  - cd /opt/gpu_support; bash install_gpu.sh ${private_deployment} ${s3_endpoint} ${vulkan_file_path}
  - until [[ $(systemctl is-active docker) = "active" ]]; do echo "waiting for docker to start" && sleep 30; done
  - cd /tmp; bash dcos_install.sh slave
  - service ntpd restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [gpu_slave_userdata] ntpd service restarted..."
  - sleep 120
  - sudo reboot
  - DATE=`date +%Y%m%d%H%M%S`
  - set -o noglob
  - source /opt/cronjob_helper.sh
  - log_collector="* * * * * cat ${terraform_log_file} | grep -e ansible -e GPU -e gpu_slave_userdata > /tmp/scorpius.log"
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
