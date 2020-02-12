#!/bin/bash
echo "Creating virtualenv"
python -m venv demo_platform
source demo_platform/bin/activate
echo "Installing Funnel (TES)"
brew tap ohsu-comp-bio/formula
brew install funnel
echo "Installing wes-service (WES)"
pip install wes-service
echo "Building DRS"
cd drs
docker-compose build
cd ..
echo "Create data directories"
mkdir ../data
mkdir ../data/pro_tes
mkdir ../data/pro_tes/db
mkdir ../data/pro_tes/specs
echo "Build TES Proxy"
cd pro_tes
export PROTES_DATA_DIR=../../data/
docker-compose build
cd ..
