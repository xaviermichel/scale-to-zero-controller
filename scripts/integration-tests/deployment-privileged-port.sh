#!/bin/bash

#
# app with priveged port
#

checkIfPatternPresent "http://127.0.0.1:18899" "nginx-privileged.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"

# scale down (not automatized yet)
kubectl ${kubeContextArgs} -n default scale --replicas=0 deployment/nginx-privileged
sleep 10
checkReplicaCount "deployment" "default" "nginx-privileged" "0"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "nginx-privileged.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"
checkReplicaCount "deployment" "default" "nginx-privileged" "2"
