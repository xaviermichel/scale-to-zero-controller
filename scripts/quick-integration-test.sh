#!/bin/bash

set -x

if [ ! -z "$1" ]; then
    kubeContextArgs="--context=$1"
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

function waitForIpToBeAssigned() {
    resourceType=$1
    namespace=$2
    name=$3
    LIMIT=60
    counter=0
    while [ $counter -lt $LIMIT -a -z "$(kubectl ${kubeContextArgs} -n ${namespace} get ${resourceType} ${name} --template='{{range .status.loadBalancer.ingress}}{{.ip}}{{end}}')" ]; do
        echo "[$counter] waiting for external IP to be assigned"
        sleep 1
        counter=$((counter + 1))
    done
}

kubectl ${kubeContextArgs} apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.1.0/deploy/static/provider/baremetal/deploy.yaml
kubectl ${kubeContextArgs} apply -f k3s-integration-test-conf/00-nginx.yaml
kubectl ${kubeContextArgs} delete -A ValidatingWebhookConfiguration ingress-nginx-admission

waitForIpToBeAssigned service ingress-nginx ingress-nginx-controller-loadbalancer

ingressExternalIp=$(kubectl ${kubeContextArgs} -n ingress-nginx get service ingress-nginx-controller-loadbalancer --no-headers -o=custom-columns=EXTERNAL-IP:.status.loadBalancer.ingress[0].ip)
ingressExternalPort=$(kubectl ${kubeContextArgs} -n ingress-nginx get service ingress-nginx-controller-loadbalancer --no-headers -o=custom-columns=EXTERNAL-PORT:.spec.ports[0].nodePort)
ingressExternalUrl="http://${ingressExternalIp}:${ingressExternalPort}"

for f in $(ls ../example-conf/); do
    kubectl ${kubeContextArgs} apply -f ../example-conf/$f
done


#
# echoserver
#

for i in $(seq 3); do # the test will be played multiple time

    waitForIpToBeAssigned ingress default echoserver-deployment

    checkIfPatternPresent "$ingressExternalUrl" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "request_version"

    # scale down (not automatized yet)
    kubectl ${kubeContextArgs} -n default scale --replicas=0 deployment/echoserver-deployment
    LIMIT=60
    counter=0
    while [ $counter -lt $LIMIT -a ! -z "$(kubectl ${kubeContextArgs} -n default get pods | grep -E '^echoserver')" ]; do
        echo "[$counter] waiting for pods to be deleted"
        counter=$((counter + 1))
        sleep 1
    done
    checkDeploymentReplicaCount "default" "echoserver-deployment" "0"

    # scale up on request
    checkIfPatternPresent "$ingressExternalUrl" "echoserver-deployment.dev-xmichel.neokube.neo9.pro" "request_version"
    checkDeploymentReplicaCount "default" "echoserver-deployment" "2"

done

