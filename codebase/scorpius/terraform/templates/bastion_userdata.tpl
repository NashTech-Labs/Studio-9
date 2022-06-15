#cloud-config
environment:
  environment: ${environment}
  aws_default_region: ${aws_region}
  aws_ca_bundle: "${aws_ca_bundle}"
write_files:
  - path: /opt/bastion_init.sh
    content: |
      echo "export AWS_DEFAULT_REGION=${aws_region}" >> /home/ec2-user/.bashrc
      echo "export AWS_DEFAULT_REGION=${aws_region}" >> /root/.bashrc
      if [[ ${aws_ca_bundle} != "" ]];then
        echo "export AWS_CA_BUNDLE=${aws_ca_bundle}" >> /home/ec2-user/.bashrc
        echo "export AWS_CA_BUNDLE=${aws_ca_bundle}" >> /root/.bashrc
      fi
      if [[ ${private_deployment} = true ]];then
      echo "private deployment for default region is started.."
        sudo yum-config-manager --disable rhui-REGION-client-config-server-7 || true
        sudo yum-config-manager --disable rhui-REGION-rhel-server-releases || true
        sudo yum-config-manager --disable rhui-REGION-rhel-server-rh-common || true
        sudo yum-config-manager --disable nodesource || true
        if [[ ${install_amazon_ssm_agent} = true ]];then
          yum install -y amazon-ssm-agent
          systemctl start amazon-ssm-agent
        fi
        if [[ ${install_aws_cli} = true ]];then
          curl -O https://${s3_endpoint}/${artifacts_s3_bucket}/pre-packages/awscli-bundle.zip
          unzip awscli-bundle.zip
          ./awscli-bundle/install -i /usr/local/aws -b /bin/aws
        fi
      else
        if [[ ${install_amazon_ssm_agent} = true ]];then
          curl https://amazon-ssm-us-east-1.s3.amazonaws.com/latest/linux_amd64/amazon-ssm-agent.rpm -o amazon-ssm-agent.rpm
          yum install -y amazon-ssm-agent.rpm
          systemctl start amazon-ssm-agent
        fi
        easy_install pip
        pip install awscli
      fi
      if [[ ${download_ssh_keys} = true ]];then
        source /home/ec2-user/.bashrc
        aws s3 cp s3://${ssh_keys_s3_bucket} - >> /home/${main_user}/.ssh/authorized_keys
      fi
runcmd:
  - bash /opt/bastion_init.sh > /var/log/bastion-init-output.log
output : { all : '| tee -a /var/log/cloud-init-output.log' }