#!/bin/bash

# for demo purpose only

NAMESPACE=xmichel-dev

# disable kube downscaler (integration does not well works)
kubectl annotate --overwrite namespace ${NAMESPACE} downscaler/exclude=true

# add missing labels
for workload in \
      statefulset:varnish:varnish \
    ; do

    kind=$(echo ${workload} | awk -F':' '{print $1}')
    workloadName=$(echo ${workload} | awk -F':' '{print $2}')
    serviceName=$(echo ${workload} | awk -F':' '{print $3}')

    kubectl -n ${NAMESPACE} label --overwrite ${kind} ${workloadName} app.kubernetes.io/name=${workloadName}
    kubectl -n ${NAMESPACE} label --overwrite ${kind} ${workloadName} app.kubernetes.io/instance=${workloadName}
    kubectl -n ${NAMESPACE} label --overwrite service ${serviceName} app.kubernetes.io/name=${workloadName}
    kubectl -n ${NAMESPACE} label --overwrite service ${serviceName} app.kubernetes.io/instance=${workloadName}

    kubectl -n ${NAMESPACE} patch ${kind} ${workloadName} -p "{\"spec\": {\"template\": {\"metadata\":{\"labels\":{\"app.kubernetes.io/instance\":\"${workloadName}\",\"app.kubernetes.io/name\":\"${workloadName}\"}}}}}"

    kubectl -n ${NAMESPACE} get endpointslices | grep -E "^${serviceName}" | awk '{print $1}' | while read endpointslice; do
        kubectl -n ${NAMESPACE} label --overwrite endpointslices ${endpointslice} app.kubernetes.io/name=${workloadName}
        kubectl -n ${NAMESPACE} label --overwrite endpointslices ${endpointslice} app.kubernetes.io/instance=${workloadName}
    done
done

# force service usage
kubectl -n ${NAMESPACE} get ingress | grep -v '^NAME' | awk '{print $1}' | while read ingressName; do
    serviceName=${ingressName}
    if [ "${ingressName}" == "varnish-frontoffice" ]; then serviceName="varnish"; fi
    kubectl -n ${NAMESPACE} annotate --overwrite ingress ${ingressName} nginx.ingress.kubernetes.io/service-upstream="true"
    kubectl -n ${NAMESPACE} annotate --overwrite ingress ${ingressName} nginx.ingress.kubernetes.io/upstream-vhost="${serviceName}.${NAMESPACE}"
done

exclusionList="garden|elasticsearch-es-default|mongodb|kibana-kb"

# mark all as scalable
kubectl -n ${NAMESPACE} get deployment,statefulset | grep -v '^NAME' | grep -Ev '^$' | awk '{print $1}' | grep -Ev "${exclusionList}" | while read workload; do
    kubectl -n ${NAMESPACE} label --overwrite ${workload} scaling.neo9.io/is-scalable="true"
done
kubectl -n ${NAMESPACE} get service | grep -v '^NAME' | grep -v 'None' | awk '{print $1}' | grep -Ev "${exclusionList}" | while read serviceName; do
    kubectl -n ${NAMESPACE} label --overwrite service ${serviceName} scaling.neo9.io/is-scalable="true"
done

# extra conf
allWorkloads=$(kubectl -n ${NAMESPACE} get deployment,statefulset | grep -v '^NAME' | grep -Ev '^$' | grep -Ev "web-api|varnish|${exclusionList}" | awk '{print $1}' | awk -F'/' '{print $2}' | paste -sd,)
kubectl -n ${NAMESPACE} annotate --overwrite statefulset varnish scaling.neo9.io/show-splash-screen="true"
kubectl -n ${NAMESPACE} annotate --overwrite statefulset varnish scaling.neo9.io/before-scale-requirements="web-api,${allWorkloads}"
kubectl -n ${NAMESPACE} annotate --overwrite deployment web-api scaling.neo9.io/before-scale-requirements="${allWorkloads}"

# manual downscale
kubectl -n ${NAMESPACE} get deployment,statefulset | grep -v '^NAME' | grep -Ev '^$' | awk '{print $1}' | grep -Ev "${exclusionList}" | while read workload; do
    kubectl -n ${NAMESPACE} scale --replicas=0 ${workload}
done
