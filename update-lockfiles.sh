#!/usr/bin/env bash

# This script is used to generate the dependencies.lock file for each combination of fips and servletContainer.

set -euo pipefail

cd $(dirname $0)

for fips in true false; do
    for servletContainer in jetty tomcat undertow; do
        ./gradlew dependencies -Pfips=$fips -PservletContainer=$servletContainer --write-locks
    done
done
