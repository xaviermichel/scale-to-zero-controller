#!/bin/bash

box $1

checkIfPatternPresent "http://127.0.0.1:18899" "nginx-privileged.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"

# scale down (manual for faster tests)
manualScaleDownAsControllerBehaviour "deployment" "default" "nginx-privileged"
checkReplicaCount "deployment" "default" "nginx-privileged" "0"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "nginx-privileged.dev-xmichel.neokube.neo9.pro" "Thank you for using nginx"
checkReplicaCount "deployment" "default" "nginx-privileged" "1"
