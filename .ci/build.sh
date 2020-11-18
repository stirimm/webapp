#!/usr/bin/env bash

set -eux

NAME="stirimm-webapp"

mvn clean package

docker build -t ${NAME} .
