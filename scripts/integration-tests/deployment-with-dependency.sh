#!/bin/bash

#
# nginx with dependency
#

checkIfPatternPresent "http://127.0.0.1:18899" "nginx-with-dependency.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"

# scale down (not automatized yet)
kubectl ${kubeContextArgs} -n default scale --replicas=0 deployment/nginx-with-dependency
kubectl ${kubeContextArgs} -n default scale --replicas=0 statefulset/echoserver-statefulset
sleep 10
checkReplicaCount "deployment" "default" "nginx-with-dependency" "0"
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "0"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "nginx-with-dependency.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"
checkReplicaCount "deployment" "default" "nginx-with-dependency" "2"
# the dependency should also have scale up
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "2"
