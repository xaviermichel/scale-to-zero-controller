#!/bin/bash

set -x

if [ ! -z "$1" ]; then
    kubeContextArgs="--context=$1"
fi

if [ "$CI" != "true" ] ; then
    kubeContextArgs="--context=k3d-scale-to-zero-controller-test"
fi

function checkIfPatternPresent() {
    ingress=$1
    hostHeader=$2
    pattern=$3
    output=$(curl --max-time 120 $ingress -H "Host: $hostHeader")
    if echo "$output" | grep -q "$pattern";then
        echo "assertion ok"
    else
        echo "Unexpected value for url ${ingress} with host ${hostHeader}"
        echo "${output}"
        echo "===="
        echo "${pattern}"
        exit 1
    fi
}

function checkDeploymentReplicaCount() {
    deploymentNamespace=$1
    deploymentName=$2
    expectation=$3
    sleep 3
    replicas=$(kubectl ${kubeContextArgs} -n ${deploymentNamespace} get deployment ${deploymentName} --no-headers -o=custom-columns=REPLICA:.spec.replicas)
    if [ ${replicas} == ${expectation} ]; then
        echo "assertion ok"
    else
        echo "Unexpected value for replicas ${deploymentName}"
        echo "${replicas}"
        echo "${expectation}"
        exit 1
    fi
}

function waitForPodReady() {
    namespace=$1
    namePrefix=$2
    podName=$(kubectl ${kubeContextArgs} -n ${namespace} get pods | grep -E "^${namePrefix}" | awk '{print $1}')
    kubectl ${kubeContextArgs} -n ${namespace} wait pod/${podName} --for=condition=Ready --timeout=120s
}

kubectl ${kubeContextArgs} apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.1.0/deploy/static/provider/baremetal/deploy.yaml
kubectl ${kubeContextArgs} delete -A ValidatingWebhookConfiguration ingress-nginx-admission
waitForPodReady ingress-nginx ingress-nginx-controller

kubectl ${kubeContextArgs} -n ingress-nginx port-forward svc/ingress-nginx-controller 18899:80 &

for f in $(ls ../example-conf/); do
    kubectl ${kubeContextArgs} apply -f ../example-conf/$f
done

#
# echoserver
#

waitForPodReady default echoserver-deployment
for i in $(seq 3); do # the test will be played multiple time
    checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "request_version"

    # scale down (not automatized yet)
    kubectl ${kubeContextArgs} -n default scale --replicas=0 deployment/echoserver-deployment
    checkDeploymentReplicaCount "default" "echoserver-deployment" "0"

    # scale up on request
    checkIfPatternPresent "http://127.0.0.1:18899" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "request_version"
    checkDeploymentReplicaCount "default" "echoserver-deployment" "2"
done
