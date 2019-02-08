# OpenShift deployment

[This blog post](https://developers.redhat.com/blog/2018/12/18/openshift-java-s2i-builder-java-11-grade/) (which [is also here](http://blog2.vorburger.ch/2018/11/s2i-with-java-11-gradle-builds-for.html)) explains the background about how to use S2I with Java 11 & Gradle on OpenShift.

## Your OpenShift instance

You'll either need to [locally install minishift](https://docs.okd.io/latest/minishift/index.html) from the OpenShift community edition OKD.io (great for testing! perhaps slightly increase resources from the default via `minishift config set memory 4096; minishift config set cpus 4`), or [use the Container Development Kit (CDK) from Red Hat](https://developers.redhat.com/products/cdk/overview/) or [Get Started and Create a FREE ;-) Account on OpenShift.com Online](https://www.openshift.com), or [use an OpenShift Partner's cloud](https://www.openshift.com/learn/partners/).

## Obtain the OC CLI

Wherever your OpenShift cluster is, it's easiest to use the `oc` CLI Client tool to deploy Alf.io; it's available e.g. on [okd.io/download](https://www.okd.io/download.html).

## Deploy Alf.io on OpenShift using OC

Once you have `oc` available locally, and you have obtained an OpenShift Login Token from the Copy Login Command on the menu in the upper right corner under your name in the OpenShift Console’s UI, you only have to do the following simple steps to deploy Alf.io:

    oc login https://... --token=...

    oc new-project alf-io

    oc apply -f https://raw.githubusercontent.com/alfio-event/alf.io/1.x-maintenance/etc/OpenShift/openshift.yaml

    oc start-build alfio

    oc logs -f bc/alfio

    oc expose svc/alfio

    oc status

NB: This does NOT use the Docker images (below) from DockerHub, but instead builds a container from source using [OpenShift's S2I Java Builder feature](https://github.com/fabric8io-images/s2i/tree/master/java/examples), thanks to this project's configuration in [.s2i/](../../.s2i/), and uses [OpenShift's PostgreSQL](https://docs.okd.io/latest/using_images/db_images/postgresql.html) ([from here](https://github.com/sclorg/postgresql-container)).

## HTTPS Security

To use TLS on a Route with a custom Hostname, [use openshift-acme](https://github.com/tnozicka/openshift-acme/tree/master/deploy/letsencrypt-live/single-namespace) which will automagically add a Cert from [Let's Encrypt](https://letsencrypt.org) as soon as you add the `metadata: annotations: kubernetes.io/tls-acme: "true"` to your Route.

## Postgres database alternative

Instead of the containerized Postgres which the YAML (above) sets up on your OpenShift, you could also consider using an external managed Postgres as a Service, e.g. from https://www.elephantsql.com.  (You'll want to deploy your ElephantSQL instance as close as possible to your OpenShift cluster in order to reduce latency.)
