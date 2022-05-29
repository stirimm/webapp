#!/usr/bin/env bash

cat .ci/deploy.yaml | sed "s/BUILD_NUMBER/${BUILD_NUMBER}/g" | kubectl apply -f -