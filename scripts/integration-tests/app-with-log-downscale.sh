#!/bin/bash

box $1

# have downscale
checkReplicaCount "deployment" "default" "app-with-log-downscale" "0"
haveAnnotation "deployment" "default" "app-with-log-downscale" "scaling.neo9.io/original-replicas"

checkIfPatternPresent "http://127.0.0.1:18899" "app-with-log-downscale.dev-xmichel.neokube.neo9.pro" "app-with-log-downscale.dev-xmichel.neokube.neo9.pro"
# have upscale
checkReplicaCount "deployment" "default" "app-with-log-downscale" "1"
