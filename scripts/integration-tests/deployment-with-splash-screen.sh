#!/bin/bash

box $1

checkIfPatternPresentVerbose "http://127.0.0.1:18899" "app-with-splash-screen.default.neokube.neo9.pro" "app-with-splash-screen.default.neokube.neo9.pro"

# scale down (manual for faster tests)
manualScaleDownAsControllerBehaviour "statefulset" "default" "app-with-splash-screen"
checkReplicaCount "statefulset" "default" "app-with-splash-screen" "0"

# scale up on request, and show splash
checkIfPatternPresentVerbose "http://127.0.0.1:18899" "app-with-splash-screen.default.neokube.neo9.pro" "303 See Other"
sleep 40
checkReplicaCount "statefulset" "default" "app-with-splash-screen" "1"
checkIfPatternPresentVerbose "http://127.0.0.1:18899" "app-with-splash-screen.default.neokube.neo9.pro" "app-with-splash-screen.default.neokube.neo9.pro"
