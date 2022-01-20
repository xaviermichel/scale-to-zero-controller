#!/bin/bash

# for demo purpose only

NAMESPACE=xmichel-dev

# add missing labels
for workload in \
      deployment:kibana-kb:kibana-kb \
      statefulset:elasticsearch-es-default:elasticsearch-es-default \
      statefulset:mongodb:mongodb \
      statefulset:varnish:varnish \
    ; do

    kind=$(echo ${workload} | awk -F':' '{print $1}')
    workloadName=$(echo ${workload} | awk -F':' '{print $2}')
    podName=$(echo ${workload} | awk -F':' '{print $3}')

    kubectl -n ${NAMESPACE} get pods | grep -E "^${podName}" | awk '{print $1}' | while read pod; do
        kubectl -n ${NAMESPACE} label --overwrite pod ${pod} app.kubernetes.io/name=${workloadName}
        kubectl -n ${NAMESPACE} label --overwrite pod ${pod} app.kubernetes.io/instance=${workloadName}
    done
done

