#!/usr/bin/env bash

add_cronjob(){
  while read new_cronjob;
  do
    sudo crontab -l > /tmp/crontab.conf
    echo "Adding crontab : $new_cronjob"
    echo $new_cronjob >> /tmp/crontab.conf
    sudo crontab /tmp/crontab.conf
  done;
}
