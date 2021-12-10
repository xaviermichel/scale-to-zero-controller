#!/bin/bash

set -x

if [ ! -z "$1" ]; then
    kubeContextArgs="--context=$1"
fi

function checkWhitelistValue() {
    ingressName=$1
    expectation=$2
    ips=$(kubectl ${kubeContextArgs} get ingress ${ingressName} -o yaml | grep 'whitelist-source-range' | grep -v 'f:' | awk '{print $2}')
    if [ ${ips} == ${expectation} ]; then
        echo "assertion ok"
    else
        echo "Unexpected value for ingress ${ingressName}"
        echo "${ips}"
        echo "${expectation}"
        exit 1
    fi
}

function checkIfExists() {
  resourceType=$1
  namespace=$2
  name=$3
  existingResource=$(kubectl get $resourceType --no-headers $name -n $namespace -o custom-columns=":metadata.name")
  if [ "${existingResource}" == "${name}" ]; then
      echo "assertion ok"
  else
      echo "Unexpected value for type $resourceType"
      echo "${existingResource}"
      echo "${name}"
      exit 1
  fi
}

function checkIfNotExists() {
  resourceType=$1
  namespace=$2
  name=$3
  existingResource=$(kubectl get $resourceType --no-headers $name -n $namespace -o custom-columns=":metadata.name")
  if [ "${existingResource}" != "${name}" ]; then
      echo "assertion ok"
  else
      echo "Unexpected value for type $resourceType"
      echo "${existingResource}"
      echo "${name}"
      exit 1
  fi
}

sleep 5

kubernetesMajorMinorVersion=$(kubectl version --short | grep 'Server Version' | awk -F':' '{print $2}' | sed 's/.*v\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\).*/\1\2/')
for f in $(ls ../example-conf/); do
    kubectl ${kubeContextArgs} apply -f ../example-conf/$f
done

#
# echoserver
#

# scale down (not automatized yet)
kubectl ${kubeContextArgs} -n default scale --replicas=0 deployment/echoserver-deployment

echo "to be continued"
exit 1
