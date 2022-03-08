#!/bin/bash

docker tag neo9sas/scale-to-zero-controller:latest xaviermichel/scale-to-zero-controller:latest
docker push xaviermichel/scale-to-zero-controller:latest

helm repo add neo9charts https://charts.neo9.pro
helm repo update

# preview
helm --namespace scale-to-zero-controller diff upgrade scale-to-zero-controller neo9charts/n9-api --install --values ./default.yaml --values ./custom.yaml \
      --set image.repository=xaviermichel/scale-to-zero-controller # override image for testing purpose
sleep 10

# deploy
helm --namespace scale-to-zero-controller      upgrade scale-to-zero-controller neo9charts/n9-api --install --values ./default.yaml --values ./custom.yaml --create-namespace \
      --set image.repository=xaviermichel/scale-to-zero-controller # override image for testing purpose
