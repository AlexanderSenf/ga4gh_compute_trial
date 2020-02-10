# GA4GH Cloud Compute Demo

## Summary

A demo setup of the GA4GH WES/TES/DRS/TRS ecosystem on localhost to perform workflows on a command line and (potentially) in a GUI environment. Contains existing projects:

- **mock-DRS** (ELIXIR Switzerland, University of Basel; Apache 2.0 licensed)
- **Dockstore** (using public API: dockstore.org)
- **Funnel** (Oregon Health & Science University; MIT licensed)
- **proTES** (ELIXIR Switzerland, University of Basel; Apache 2.0 licensed)
- **wes-service** (common-workflow-language; Apache 2.0 licensed)



Many of these services exist in public deployments - so testing this system does not require to instantiate all services in a local environment: https://github.com/elixir-europe/elixir-cloud-outline 

## Usage

```
git clone {repository}
cd {repository}
```

There are three scripts included to setup, start, and stop the deployment of these services on localhost:

* `build.sh`

  This creates a virtualenv 'demo_platform', installes necessary projects, and builds any docker-compose projects. It also creates a /data directory at the same level as the github repository.
  
  (Ensure the correct minimum version of `docker-compose` https://github.com/docker/compose/releases)

* `start.sh`

  This starts up all 4 services (for TRS the public Dockestore API is used). Two services are started via docker-compose up, two services are started on the command line directly. The services can be tested via their (Swagger) URLs:

  * DRS: http://localhost:9101/ga4gh/drs/v1/ui/
  * TRS (e.g.): https://dockstore.org/api/api/ga4gh/v2/tools/ 
  * TES (Funnel): http://localhost:8000/v1/tasks
  * TES-Proxy (proTES): http://localhost:7878/ga4gh/tes/v1/ui 
  * WES: http://localhost:8080/ga4gh/wes/v1/ui/

* `stop.sh`

  This script stops and removes all Docker containers, and stops any other services started for this demo.

## Limitations

- - DRS was approved as a standard just a few months ago; most reference implementations pre-date this, so they don’t support data access via DRS URI yet (in development, e.g. [TEStribute](https://github.com/elixir-europe/TEStribute) will make it into [proTES](https://github.com/elixir-europe/proTES)).
  - The standard WES implementation can be configured with cwltool, Arvados, Toil backends, but not yet with TES backend.

The goal is to deploy a fully integrated set of services to demonstrate the GA4GH Cloud Compute APIs.