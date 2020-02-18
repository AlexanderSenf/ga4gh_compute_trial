#!/bin/bash
echo "Entering virtualenv"
source demo_platform/bin/activate
echo "Starting DRS"
cd drs
nohup docker-compose up -d &
cd ..
echo "Starting TES"
#killall -9 funnel
#nohup funnel server run &
cd tes
nohup docker-compose up -d &
cd ..
echo "Starting TES Proxy"
cd pro_tes
export PROTES_DATA_DIR=../../data/pro_tes/
nohup docker-compose up -d &
cd ..
echo "Starting WES Proxy"
cd pro_wes
export PROWES_DATA_DIR=../../data/pro_wes/
nohup docker-compose up -d &
cd ..
echo "Using https://dockstore.org/ for TRS"
echo "Starting WES"
killall -9 wes
nohup wes-server &
