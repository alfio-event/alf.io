# How to run Alf.io in Kubernetes

Alf.io provides support for running in a Kubernetes cluster. This guide will walk you through the process of setting up and running Alf.io using standard Kubernetes manifests.

## Prerequisites

- A running Kubernetes cluster (Minikube, EKS, GKE, etc.).
- `kubectl` CLI installed and configured to connect to your cluster.
- A PostgreSQL database (Alf.io requires a database to store its data).

## Pulling the Image

The Kubernetes cluster will pull the latest image automatically from the GitHub Container Registry during deployment. However, you can verify it locally:

```bash
docker pull ghcr.io/alfio-event/alf.io:latest
```

## Running with Kubernetes Manifests

Using structured Kubernetes manifests is the recommended way to run Alf.io, as it allows you to manage the application, its database, services, and volumes declaration in an organized cloud-native way.

Create a `kube-deployment.yaml` file with the following content:

> [!NOTE]
> This is a simplified example for demonstration purposes. You should review and adjust the configurations according to your production needs.

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: alfio-db-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: alfio-db
spec:
  replicas: 1
  selector:
    matchLabels:
      app: alfio-db
  template:
    metadata:
      labels:
        app: alfio-db
    spec:
      containers:
        - name: db
          image: postgres:15
          environment:
            - name: POSTGRES_DB
              value: alfio
            - name: POSTGRES_USER
              value: postgres
            - name: POSTGRES_PASSWORD
              value: password
          ports:
            - containerPort: 5432
          volumeMounts:
            - name: db-storage
              mountPath: /var/lib/postgresql/data
      volumes:
        - name: db-storage
          persistentVolumeClaim:
            claimName: alfio-db-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: db
spec:
  ports:
    - port: 5432
  selector:
    app: alfio-db
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: alfio
spec:
  replicas: 1
  selector:
    matchLabels:
      app: alfio
  template:
    metadata:
      labels:
        app: alfio
    spec:
      containers:
        - name: alfio
          image: ghcr.io/catarinas-ps-2026/alf.io:latest
          ports:
            - containerPort: 8080
          env:
            - name: DATASOURCE_URL
              value: jdbc:postgresql://db:5432/alfio
            - name: DATASOURCE_USERNAME
              value: postgres
            - name: DATASOURCE_PASSWORD
              value: password
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            # - name: ALFIO_OVERRIDE_SYSTEM_SETTINGS_HTTPS_FORCE_REDIRECT
            #   value: "false" # for running behind a reverse proxy
          readinessProbe:
            httpGet:
              path: /
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: alfio-service
spec:
  type: NodePort
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
  selector:
    app: alfio

```

Run the following command to start the application and its dependencies in your cluster:

```bash
kubectl apply -f kube-deployment.yaml

```

## Configuration (Environment Variables)

The following environment variables can be used to configure the Alf.io container within the Deployment spec:

| Variable | Description | Default |
| --- | --- | --- |
| `DATASOURCE_URL` | The JDBC URL for the PostgreSQL database. | `jdbc:postgresql://localhost:5432/alfio` |
| `DATASOURCE_USERNAME` | The username for the database. | `postgres` |
| `DATASOURCE_PASSWORD` | The password for the database. | `password` |
| `SPRING_PROFILES_ACTIVE` | The active Spring profiles (e.g., `prod`, `dev`). | `dev` |
| `ALFIO_JAVA_OPTS` | Additional JVM options. |  |
| `ALFIO_PERFORMANCE_OPTS` | Performance-related JVM options. | `-Dspring.jmx.enabled=false -Dlog4j2.disableJmx=true` |

## Persistence

Alf.io is mostly stateless; however, all persistent data is stored in the PostgreSQL database. Ensure that you have a `PersistentVolumeClaim` (PVC) configured and mounted to the database deployment to avoid data loss across pod restarts.


