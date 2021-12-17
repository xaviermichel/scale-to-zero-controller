#!/bin/bash

helm repo add neo9charts https://charts.neo9.pro
helm repo update

# preview
helm --namespace scale-to-zero-controller diff upgrade scale-to-zero-controller neo9charts/n9-api --install --values ./default.yaml
sleep 10

# deploy
helm --namespace scale-to-zero-controller      upgrade scale-to-zero-controller neo9charts/n9-api --install --values ./default.yaml --create-namespace
