CLUSTER_NAME_AUTOPREFIX="k3d-"
CLUSTER_NAME="scale-to-zero-controller-test"
CLUSTER_NAME_SERVERSUFFIX="-server-0"

CLUSTER_KUBE_CONTEXT=$(CLUSTER_NAME_AUTOPREFIX)$(CLUSTER_NAME)

CLUSTER_VERSION=v1.22.5-k3s1


start-k3s-for-test:
	k3d cluster create $(CLUSTER_NAME) --image rancher/k3s:$(CLUSTER_VERSION) --k3s-arg "--disable=traefik@server:0"
	k3d kubeconfig merge $(CLUSTER_NAME)

stop-k3s-for-test:
	k3d cluster delete $(CLUSTER_NAME) || true

import-image-in-k3s:
	mkdir -p ../build/images || true
	docker save --output ../build/images/scale-to-zero-controller-latest.tar docker.io/neo9sas/scale-to-zero-controller:latest
	docker cp ../build/images/scale-to-zero-controller-latest.tar $(CLUSTER_NAME_AUTOPREFIX)$(CLUSTER_NAME)$(CLUSTER_NAME_SERVERSUFFIX):/tmp
	docker exec $(CLUSTER_NAME_AUTOPREFIX)$(CLUSTER_NAME)$(CLUSTER_NAME_SERVERSUFFIX) sh -c "ctr image import /tmp/scale-to-zero-controller-latest.tar"
	docker exec $(CLUSTER_NAME_AUTOPREFIX)$(CLUSTER_NAME)$(CLUSTER_NAME_SERVERSUFFIX) sh -c "ctr image list"

setup-k3s-for-test: start-k3s-for-test import-image-in-k3s

run-integration-tests:
	./run-integration-tests.sh $(CLUSTER_KUBE_CONTEXT)

start-controller:
	kubeContextArgs=
	if [ ! -z "$(CLUSTER_KUBE_CONTEXT)" ]; then kubeContextArgs="--context=$(CLUSTER_KUBE_CONTEXT)"; fi
	kubectl ${kubeContextArgs} apply -f controller-deployment/

local-cluster-update-controller:
	cd .. && NATIVE_IMAGE=false ./gradlew bootBuildImage && cd scripts
	make import-image-in-k3s
	kubeContextArgs=
	if [ ! -z "$(CLUSTER_KUBE_CONTEXT)" ]; then kubeContextArgs="--context=$(CLUSTER_KUBE_CONTEXT)"; fi
	kubectl ${kubeContextArgs} -n scale-to-zero-controller rollout restart deploy/scale-to-zero-controller-n9-api

full-local-integration-tests-run: setup-k3s-for-test start-controller run-integration-tests stop-k3s-for-test
