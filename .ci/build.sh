#!/usr/bin/env bash

set -eux

NAME="registry.emilburzo.com/stirimm-webapp"
PLATFORMS="linux/amd64,linux/arm64"

mvn clean package

docker buildx build --push --platform ${PLATFORMS} -t ${NAME} -t ${NAME}:${BUILD_NUMBER} .