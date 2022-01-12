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


waitForPodReady default echoserver-deployment
checkReplicaCount "deployment" "default" "echoserver-deployment" "1"
for i in $(seq 3); do # the test will be played multiple time
  echo "Running deployment test iteration #$i"
  . ./integration-tests/deployment.sh
done


waitForPodReady default echoserver-statefulset
checkReplicaCount "statefulset" "default" "echoserver-statefulset" "1"
for i in $(seq 3); do # the test will be played multiple time
  echo "Running statefulset test iteration #$i"
  . ./integration-tests/statefulset.sh
done


for i in $(seq 3); do # the test will be played multiple time
  echo "Running deployment-with-dependency test iteration #$i"
  . ./integration-tests/deployment-with-dependency.sh
done


waitForPodReady default nginx-privileged
. ./integration-tests/deployment-privileged-port.sh

waitForPodReady default app-with-splash-screen
. ./integration-tests/deployment-with-splash-screen.sh

