#!/bin/bash

box $1

# start with up status
checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "echoserver-deployment.dev-xmichel.neokube.neo9.pro"
checkReplicaCount "deployment" "default" "echoserver-deployment" "2"
doNotHaveAnnotation "deployment" "default" "echoserver-deployment" "downscaler/exclude"
doNotHaveAnnotation "deployment" "default" "echoserver-deployment" "scaling.neo9.io/original-replicas"

# scale down, simulate that we are kube downscaler
kubectl ${kubeContextArgs} -n default scale --replicas=0 deployment/echoserver-deployment
checkReplicaCount "deployment" "default" "echoserver-deployment" "0"
kubectl ${kubeContextArgs} -n default annotate --overwrite deployment echoserver-deployment downscaler/original-replicas="3"

# check controller actions on hijack
doNotHaveAnnotation "deployment" "default" "echoserver-deployment" "scaling.neo9.io/original-replicas"
haveAnnotation "deployment" "default" "echoserver-deployment" "downscaler/exclude"

# upscale
checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "echoserver-deployment.dev-xmichel.neokube.neo9.pro"
checkReplicaCount "deployment" "default" "echoserver-deployment" "3"

# check controller actions on release
doNotHaveAnnotation "deployment" "default" "echoserver-deployment" "downscaler/exclude"
haveAnnotation "deployment" "default" "echoserver-deployment" "downscaler/original-replicas"
doNotHaveAnnotation "deployment" "default" "echoserver-deployment" "scaling.neo9.io/original-replicas"

