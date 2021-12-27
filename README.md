Scale to zero controller
========================

Motivation
----------

The goal of this controller is to provide a way to start a kubernetes workload on demand (mean when there is incoming TCP traffic on it), and shutdown it after that. It very useful in order to :
* we do not need kubernetes nodes all the time (FinOps)
* distribute daily developments environements start according to real team needs

To acheive that, the controller takes control of the `endpointslice` associated to your application when the application is scaled to 0. The controller is aware of incoming traffic to your service, and can scale up the workload and give back `endpointslice` to your cloud provider controller.

Compatibility
-------------

Tested on GKE. and k3s, with Kubernetes 1.20 and nginx ingress controller

Concepts and usage
------------------

In Kubernetes, there is no direct relationship between deployments and services. That's why we use [standard labels](https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/) to link a service with the application : ` app.kubernetes.io/name` and `app.kubernetes.io/instance`.

In order to be sur that the controller manages resources it's allowed to, you have to add this label on service and deployment : `scaling.neo9.io/is-scalable: "true"`. You can have a look in `example-conf/echoserver/echoserver.yaml`.

That's all ! Your deployment will be detected by controller and follow the rules bellow.

Downscaling and Upscaling technicals actions are detailled bellow.

# Downscaling

Work in progress, there is no auto-downscaling for the moment.

TODO : explain hijacking

# Upscaling

TODO

Similars projets
----------------

This project is similar to [osiris](https://github.com/dailymotion-oss/osiris) but wants to :
* focus on layer 4
* uses `endpointslice` instead of `endpoints`
* do not inject custom proxy

