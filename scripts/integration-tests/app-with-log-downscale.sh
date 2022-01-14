#!/bin/bash

#
# app-with-log-downscale
#

# have downscale
checkReplicaCount "deployment" "default" "app-with-log-downscale" "0"

checkIfPatternPresent "http://127.0.0.1:18899" "app-with-log-downscale.dev-xmichel.neokube.neo9.pro" "app-with-log-downscale.dev-xmichel.neokube.neo9.pro"
# have upscale
checkReplicaCount "deployment" "default" "echoserver-deployment" "2"
