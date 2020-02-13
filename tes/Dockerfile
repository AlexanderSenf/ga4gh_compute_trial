##### BASE IMAGE #####
FROM ubuntu:18.04

##### METADATA #####
LABEL base.image="ubuntu:16.04"
LABEL version="1.1"
LABEL software="TES (Funnel)"
LABEL software.version="1.0"
LABEL software.description="Microservice implementing the Global Alliance for Genomics and Health (GA4GH) Data Repository Schema (TES) API specification."
LABEL software.tags="General"

## Install system resources & dependencies
RUN apt-get update \
  && apt-get install -y git make golang-go go-dep

## Env Variables
ENV GOPATH=$HOME/go

RUN mkdir go \
&& cd go \
&& mkdir src \
&& cd src \
&& mkdir github.com \
&& cd github.com \
&& mkdir ohsu-comp-bio \
&& cd ohsu-comp-bio \
&& git clone https://github.com/ohsu-comp-bio/funnel.git \
&& cd funnel \
&& make install

EXPOSE 8000 9090

ENTRYPOINT []
