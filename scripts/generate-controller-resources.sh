#!/bin/bash

# generates resources used for CI
# the goal is to be independent from helm

set -x

targetDir=./controller-deployment/

helm repo add neo9charts https://charts.neo9.pro
helm repo update

helm template \
        --namespace scale-to-zero-controller \
         scale-to-zero-controller \
         neo9charts/n9-api \
        --values ../values/default.yaml --values ../values/dev.yaml \
        --set image.repository=docker.io/neo9sas/scale-to-zero-controller \
        --set image.pullPolicy=Never \
        > ${targetDir}/scale-to-zero-controller.yaml
