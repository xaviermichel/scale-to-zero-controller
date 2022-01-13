#!/bin/bash

# for demo purpose only

kubectl -n xmichel-dev label --overwrite deployment kibana-kb scaling.neo9.io/is-scalable="true"
kubectl -n xmichel-dev label --overwrite deployment kibana-kb app.kubernetes.io/name=kibana
kubectl -n xmichel-dev label --overwrite deployment kibana-kb app.kubernetes.io/instance=kibana

kubectl -n xmichel-dev label --overwrite statefulsets elasticsearch-es-default scaling.neo9.io/is-scalable="true"
kubectl -n xmichel-dev label --overwrite statefulsets elasticsearch-es-default app.kubernetes.io/name=elasticsearch
kubectl -n xmichel-dev label --overwrite statefulsets elasticsearch-es-default app.kubernetes.io/instance=elasticsearch

kubectl -n xmichel-dev label --overwrite statefulsets mongodb scaling.neo9.io/is-scalable="true"
kubectl -n xmichel-dev label --overwrite statefulsets mongodb app.kubernetes.io/name=mongodb
kubectl -n xmichel-dev label --overwrite statefulsets mongodb app.kubernetes.io/instance=mongodb

kubectl -n xmichel-dev get deploy | grep -v '^NAME' | awk '{print $1}' | while read d; do kubectl -n xmichel-dev scale --replicas=0 deploy/$d; done
kubectl -n xmichel-dev get statefulset | grep -v '^NAME' | awk '{print $1}' | grep -v elasticsearch | while read d; do kubectl -n xmichel-dev scale --replicas=0 statefulset/$d; done

