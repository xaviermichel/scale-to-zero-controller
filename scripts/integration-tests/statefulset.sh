#!/bin/bash

#
# echoserver-statefulset
#

checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro"

# scale down (manual for faster tests)
kubectl ${kubeContextArgs} -n default scale --replicas=0 statefulset/echoserver-statefulset
sleep 10
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "0"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro"
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "2"
