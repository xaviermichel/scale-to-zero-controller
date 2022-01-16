#!/bin/bash

box $1

checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro"

# scale down (manual for faster tests)
manualScaleDownAsControllerBehaviour "statefulset" "default" "echoserver-statefulset"
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "0"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro" "echoserver-statefulset.dev-xmichel.neokube.neo9.pro"
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "2"
