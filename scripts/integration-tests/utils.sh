#!/bin/bash

function box(){
	t="$1xxxx";c=${2:-=}; echo ${t//?/$c}; echo "$c $1 $c"; echo ${t//?/$c};
}

function manualScaleDownAsControllerBehaviour() {
    kind=$1
    namespace=$2
    name=$3
    replicas=$(kubectl ${kubeContextArgs} -n ${namespace} get ${kind} ${name} --no-headers -o=custom-columns=REPLICA:.spec.replicas)
    kubectl ${kubeContextArgs} -n ${namespace} scale --replicas=0 ${kind}/${name}
    kubectl ${kubeContextArgs} -n ${namespace} annotate --overwrite ${kind} ${name} scaling.neo9.io/original-replicas="${replicas}"
    sleep 10
}

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

function haveAnnotation() {
    kind=$1
    namespace=$2
    name=$3
    annotation=$4
    annotations=$(kubectl ${kubeContextArgs} -n ${namespace} get ${kind} ${name} -o jsonpath='{.metadata.annotations}')
    if echo "$annotations" | grep -q "$annotation";then
        echo "assertion ok"
    else
        echo "Did not found annotation"
        exit 1
    fi
}

function doNotHaveAnnotation() {
    kind=$1
    namespace=$2
    name=$3
    annotation=$4
    annotations=$(kubectl ${kubeContextArgs} -n ${namespace} get ${kind} ${name} -o jsonpath='{.metadata.annotations}')
    if echo "$annotations" | grep -vq "$annotation";then
        echo "assertion ok"
    else
        echo "Found annotation"
        exit 1
    fi
}
