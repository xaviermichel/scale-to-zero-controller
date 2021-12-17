CLUSTER_NAME_AUTOPREFIX="k3d-"
CLUSTER_NAME="scale-to-zero-controller-test"
CLUSTER_NAME_SERVERSUFFIX="-server-0"
CLUSTER_VERSION=v1.21.3-k3s1

start-k3s-for-test:
	k3d cluster create $(CLUSTER_NAME) --image rancher/k3s:$(CLUSTER_VERSION)
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
	./quick-integration-test.sh $(CLUSTER_NAME_AUTOPREFIX)$(CLUSTER_NAME)

start-controller:
	kubectl --context=$(CLUSTER_NAME_AUTOPREFIX)$(CLUSTER_NAME) apply -f controller-deployment/

full-local-integration-tests-run: start-k3s-for-test run-integration-tests stop-k3s-for-test
