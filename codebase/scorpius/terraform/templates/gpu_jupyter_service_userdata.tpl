#cloud-config
environment:
  environment: ${environment}
  aws_default_region: ${aws_region}
  s3_endpoint: ${s3_endpoint}
  vulkan_file_path: "${vulkan_file_path}"
manage_resolv_conf: false
preserve_hostname: true
runcmd:
  - instanceid=$(curl -s http://169.254.169.254/latest/meta-data/instance-id | tr -d 'i-')
  - hostn=$(cat /etc/hostname)
  - echo "`date '+%b %d  %H:%M:%S'` [gpu_jupyter_service_userdata] Existing hostname is $hostn"
  - newhostn="gpu-jupyter-$instanceid"
  - sed -i "s/localhost/$newhostn/g" /etc/hosts
  - sed -i "s/$hostn/$newhostn/g" /etc/hostname
  - hostnamectl set-hostname $newhostn
  - echo "`date '+%b %d  %H:%M:%S'` [gpu_jupyter_service_userdata] New hostname is $newhostn"
  - service rsyslog restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [gpu_jupyter_service_userdata] syslog service restarted.."
  - service ntpd restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn  [gpu_jupyter_service_userdata] ntpd service restarted.."
  - sysctl net.bridge.bridge-nf-call-iptables=1
  - sysctl net.bridge.bridge-nf-call-ip6tables=1
  - if [[ ${download_ssh_keys} = true ]]; then curl https://${s3_endpoint}/${ssh_keys_s3_bucket}  >> /home/${main_user}/.ssh/authorized_keys; fi
  - zone_id=$(curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone)
  - mkdir -p /var/lib/dcos
  - touch /var/lib/dcos/mesos-slave-common || exit
  - echo "MESOS_ATTRIBUTES='az_id:$zone_id;cluster:gpu-jupyter;'" > /var/lib/dcos/mesos-slave-common
  - until $(curl --output /dev/null --silent --head --fail http://${bootstrap_dns}:8080/dcos_install.sh); do sleep 5; done
  - curl http://${bootstrap_dns}:8080/dcos_install.sh -o /tmp/dcos_install.sh -s
  - cd /opt/gpu_support; bash install_gpu.sh ${private_deployment} ${s3_endpoint} ${vulkan_file_path}
  - until [[ $(systemctl is-active docker) = "active" ]]; do echo "waiting for docker to start" && sleep 30; done
  - cd /tmp; bash dcos_install.sh slave
  - service ntpd restart
  - echo "`date '+%b %d  %H:%M:%S'` $newhostn [gpu_jupyter_service_userdata] ntpd service restarted..."
  - sleep 120
  - sudo reboot
output : { all : '| tee -a /var/log/cloud-init-output.log' }
