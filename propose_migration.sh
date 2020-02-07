#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

# this script generates a proposed database changelog file based on your current JPA classes

## destroy and recreate both databases
if [ "${CIRCLECI-false}" = "true" ]; then
    dropdb -U postgres webservice_test || true
    dropdb -U postgres webservice_test_proposed || true
    createdb -U postgres webservice_test || true
    createdb -U postgres webservice_test_proposed || true
    rm dockstore-webservice/target/detected-migrations.xml || true
else
    sudo -i -u postgres dropdb webservice_test || true
    sudo -i -u postgres dropdb webservice_test_proposed || true
    sudo -i -u postgres createdb webservice_test || true
    sudo -i -u postgres createdb webservice_test_proposed || true
    rm dockstore-webservice/target/detected-migrations.xml || true
fi

## load up the old database based on current migration
rm dockstore-webservice/target/dockstore-webservice-*sources.jar || true
java -jar dockstore-webservice/target/dockstore-webservice-*.jar db migrate dockstore-integration-testing/src/test/resources/dockstore.yml --include 1.3.0.generated,1.4.0,1.5.0,1.6.0,1.7.0,1.8.0,1.9.0
## create the new database based on JPA (ugly, should really create a proper dw command if this works)
timeout 15 java -Ddw.database.url=jdbc:postgresql://localhost:5432/webservice_test_proposed -Ddw.database.properties.hibernate.hbm2ddl.auto=create -jar dockstore-webservice/target/dockstore-webservice-*.jar server dockstore-integration-testing/src/test/resources/dockstore.yml || true

cd dockstore-webservice && mvn liquibase:diff

echo 'examine proposed changes at `dockstore-webservice/target/detected-migrations.xml`'
