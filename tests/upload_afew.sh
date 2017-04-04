#!/bin/sh
echo "Uploading 1.0"
gradle -PcliVersion=1.0 upload
sleep 10
echo "Uploading 1.1"
gradle -PcliVersion=1.1 upload
sleep 10
echo "Uploading 1.2"
gradle -PcliVersion=1.2 upload

