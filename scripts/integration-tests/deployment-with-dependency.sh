#!/bin/bash

box $1

checkIfPatternPresent "http://127.0.0.1:18899" "nginx-with-dependency.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"

# scale down (manual for faster tests)
manualScaleDownAsControllerBehaviour "deployment" "default" "nginx-with-dependency"
manualScaleDownAsControllerBehaviour "statefulset" "default" "echoserver-statefulset"
checkReplicaCount "deployment" "default" "nginx-with-dependency" "0"
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "0"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "nginx-with-dependency.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"
checkReplicaCount "deployment" "default" "nginx-with-dependency" "3"
# the dependency should also have scale up
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "2"
