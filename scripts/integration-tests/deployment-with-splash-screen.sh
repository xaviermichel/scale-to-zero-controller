#!/bin/bash

#
# app with splash screen
#

checkIfPatternPresentVerbose "http://127.0.0.1:18899" "app-with-splash-screen.default.neokube.neo9.pro" "app-with-splash-screen.default.neokube.neo9.pro"

# scale down (not automatized yet)
kubectl ${kubeContextArgs} -n default scale --replicas=0 statefulset/app-with-splash-screen
sleep 10
checkReplicaCount "statefulset" "default" "app-with-splash-screen" "0"

# scale up on request, and show splash
checkIfPatternPresentVerbose "http://127.0.0.1:18899" "app-with-splash-screen.default.neokube.neo9.pro" "303 See Other"
sleep 40
checkReplicaCount "statefulset" "default" "app-with-splash-screen" "2"
checkIfPatternPresentVerbose "http://127.0.0.1:18899" "app-with-splash-screen.default.neokube.neo9.pro" "app-with-splash-screen.default.neokube.neo9.pro"
