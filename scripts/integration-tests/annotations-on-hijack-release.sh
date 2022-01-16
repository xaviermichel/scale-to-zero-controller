#!/bin/bash

box $1

# scale down (manual for faster tests)
manualScaleDownAsControllerBehaviour "deployment" "default" "echoserver-deployment"
checkReplicaCount "deployment" "default" "echoserver-deployment" "0"

haveAnnotation "deployment" "default" "echoserver-deployment" "downscaler/exclude"

# scale up on request
checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "echoserver-deployment.dev-xmichel.neokube.neo9.pro"
checkReplicaCount "deployment" "default" "echoserver-deployment" "2"

doNotHaveAnnotation "deployment" "default" "echoserver-deployment" "downscaler/exclude"

