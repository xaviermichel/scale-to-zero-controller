#!/bin/bash

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

function checkIfPatternPresentVerbose() {
    ingress=$1
    hostHeader=$2
    pattern=$3
    output=$(curl -v --max-time 120 $ingress -H "Host: $hostHeader" 2>&1)
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

function checkReplicaCount() {
    deploymentKind=$1
    deploymentNamespace=$2
    deploymentName=$3
    expectation=$4
    sleep 3
    replicas=$(kubectl ${kubeContextArgs} -n ${deploymentNamespace} get ${deploymentKind} ${deploymentName} --no-headers -o=custom-columns=REPLICA:.spec.replicas)
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
