#!/usr/bin/env bash

set -eux

NAME="stirimm-webapp"

docker stop ${NAME}
docker rm ${NAME}
docker run -d \
  --restart=always \
  -p 7563:8080 \
  -e DB_PASS="${DB_PASS}" \
  -e DB_HOST="${DB_HOST}" \
  --name ${NAME} \
  ${NAME}
