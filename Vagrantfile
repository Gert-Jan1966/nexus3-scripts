# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  config.vm.box = "williamyeh/centos7-docker"

  # setting host_ip due to a bug in Vagrant - https://github.com/mitchellh/vagrant/issues/839
  config.vm.network "forwarded_port", guest: 8081, host: 8081, host_ip: "127.0.0.1"
  config.vm.provider "virtualbox" do |vb|
    vb.cpus = 2
    vb.memory = "1536"
  end

  config.vm.provision "test", type: "shell", inline: <<-SHELL
    docker run -d -p 8081:8081 --name nexus --restart yes sonatype/nexus3:3.2.1

    echo "Waiting for Nexus 3 to become available"
    counter=0
    while [[ `curl -s -o /dev/null -I --write-out "%{http_code}" http://localhost:8081/` -ne 200 ]]; do
      counter=$((counter + 1))
      if [[ $((counter % 4)) -eq 0 ]]; then
         echo "not yet..."
      fi
      if [[ counter -eq 50 ]]; then
         echo "Nexus doesn't seem to be online" && exit 1
      fi

      sleep 5
    done
    echo "Nexus is up and running"
SHELL

end
