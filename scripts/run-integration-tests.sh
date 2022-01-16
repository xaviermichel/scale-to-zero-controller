#!/bin/bash

set -x

if [ ! -z "$1" ]; then
    kubeContextArgs="--context=$1"
elif [ "$CI" != "true" ] ; then
    kubeContextArgs="--context=k3d-scale-to-zero-controller-test"
fi

# prepare

source ./integration-tests/utils.sh

kubectl ${kubeContextArgs} apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.1.0/deploy/static/provider/baremetal/deploy.yaml
kubectl ${kubeContextArgs} delete -A ValidatingWebhookConfiguration ingress-nginx-admission
waitForPodReady ingress-nginx ingress-nginx-controller

kubectl ${kubeContextArgs} -n ingress-nginx port-forward svc/ingress-nginx-controller 18899:80 &

for f in $(ls ../example-conf/); do
    kubectl ${kubeContextArgs} apply -f ../example-conf/$f
done

# should be up before downscale !
waitForPodReady default app-with-log-downscale
checkIfPatternPresent "http://127.0.0.1:18899" "app-with-log-downscale.dev-xmichel.neokube.neo9.pro" "app-with-log-downscale.dev-xmichel.neokube.neo9.pro"
checkReplicaCount "deployment" "default" "app-with-log-downscale" "1"

waitForPodReady default echoserver-deployment
checkReplicaCount "deployment" "default" "echoserver-deployment" "2"
for i in $(seq 3); do # the test will be played multiple time
  echo "Running deployment test iteration #$i"
  . ./integration-tests/deployment.sh "deployment"
done


waitForPodReady default echoserver-statefulset
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "2"
for i in $(seq 3); do # the test will be played multiple time
  echo "Running statefulset test iteration #$i"
  . ./integration-tests/statefulset.sh "statefulset"
done

for i in $(seq 3); do # the test will be played multiple time
  echo "Running deployment-with-dependency test iteration #$i"
  . ./integration-tests/deployment-with-dependency.sh "deployment-with-dependency"
done

waitForPodReady default nginx-privileged
. ./integration-tests/deployment-privileged-port.sh "deployment-privileged-port"

waitForPodReady default app-with-splash-screen
. ./integration-tests/deployment-with-splash-screen.sh "deployment-with-splash-screen"

. ./integration-tests/app-with-log-downscale.sh "app-with-log-downscale"

. ./integration-tests/annotations-on-hijack-release.sh "annotations-on-hijack-release"

. ./integration-tests/cooperation-with-kube-downscaler.sh "cooperation-with-kube-downscaler"

