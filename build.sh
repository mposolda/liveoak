#!/bin/bash

mvn clean install -DskipTests

cp -r ~/dev/liveoak-examples/auth wildfly-dist/target/dist/liveoak-1.0.0-SNAPSHOT/apps/

wildfly-dist/target/dist/liveoak-1.0.0-SNAPSHOT/bin/standalone.sh
