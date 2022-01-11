#!/bin/bash

#
# echoserver-deployment
#

checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "echoserver-deployment.dev-xmichel.neokube.neo9.pro"

# scale down (not automatized yet)
kubectl ${kubeContextArgs} -n default scale --replicas=0 deployment/echoserver-deployment
checkReplicaCount "deployment" "default" "echoserver-deployment" "0"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "echoserver-deployment.dev-xmichel.neokube.neo9.pro"
checkReplicaCount "deployment" "default" "echoserver-deployment" "2"
