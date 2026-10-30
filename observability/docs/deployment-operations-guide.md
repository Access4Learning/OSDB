# Deployment and Operations Guide

## Table of Contents

1. [Overview](#overview)
2. [Local Deployment (Docker Compose)](#local-deployment-docker-compose)
3. [Kubernetes Deployment](#kubernetes-deployment)
4. [Configuring Alerting Destinations](#configuring-alerting-destinations)
5. [Managing Retention Policies](#managing-retention-policies)
6. [Scaling Observability Components](#scaling-observability-components)
7. [Backup and Recovery](#backup-and-recovery)
8. [Monitoring the Observability Stack](#monitoring-the-observability-stack)

## Overview

This guide covers deploying and operating the SIF Unity Broker observability stack in different environments. The stack includes:

- **Jaeger**: Distributed tracing backend
- **Prometheus**: Metrics collection and storage
- **Grafana**: Visualization and dashboards
- **Elasticsearch**: Log storage and search
- **Logstash**: Log processing and aggregation
- **Kibana**: Log visualization

### Deployment Options

1. **Local Development**: Docker Compose (single machine)
2. **Production**: Kubernetes (scalable, highly available)

### Prerequisites

**Local Development**:
- Docker Engine 20.10+
- Docker Compose 2.0+
- 4GB+ available RAM
- 10GB+ available disk space

**Production**:
- Kubernetes 1.24+
- kubectl configured
- Helm 3.0+ (optional but recommended)
- Persistent storage provisioner
- Ingress controller

## Local Deployment (Docker Compose)

### Quick Start

**1. Navigate to observability directory**:
```bash
cd observability/docker
```

**2. Start the stack**:
```bash
docker-compose up -d
```

**3. Verify services are running**:
```bash
docker-compose ps
```

All services should show status as "Up" or "healthy".

**4. Access UIs**:
- Jaeger: http://localhost:16686
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- Kibana: http://localhost:5601
- Elasticsearch: http://localhost:9200

### Configuration Files

**Directory Structure**:
```
observability/docker/
├── docker-compose.yml          # Main compose file
├── prometheus/
│   ├── prometheus.yml          # Prometheus configuration
│   └── alerts.yml              # Alert rules
├── grafana/
│   ├── provisioning/           # Datasources and dashboards
│   └── dashboards/             # Dashboard JSON files
└── logstash/
    ├── config/
    │   └── logstash.yml        # Logstash configuration
    └── pipeline/
        └── logstash.conf       # Log processing pipeline
```


### Configuring Services for Local Stack

**Environment Variables** (add to each service):
```yaml
# docker-compose.yml for SIF services
services:
  queue-service:
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - JAEGER_ENDPOINT=http://jaeger:14250
      - TRACING_SAMPLING_RATE=1.0
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5000
    networks:
      - sif-observability  # Connect to observability network
```

**Network Configuration**:
```yaml
# In your service docker-compose.yml
networks:
  sif-observability:
    external: true
    name: sif-observability
```

### Stopping the Stack

**Stop services (keep data)**:
```bash
docker-compose down
```

**Stop and remove all data**:
```bash
docker-compose down -v
```

**Warning**: This deletes all traces, metrics, and logs!

### Troubleshooting Local Deployment

#### Elasticsearch Won't Start

**Symptom**: Elasticsearch container exits immediately

**Solution 1**: Increase Docker memory to 4GB+

**Solution 2** (Linux): Increase vm.max_map_count
```bash
sudo sysctl -w vm.max_map_count=262144

# Make permanent
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
```

#### Services Can't Connect to Observability Stack

**Symptom**: Services log connection errors to Jaeger/Logstash

**Solution**: Ensure services are on the same Docker network
```bash
# Check network
docker network inspect sif-observability

# Connect service to network
docker network connect sif-observability <service-container>
```

#### Prometheus Not Scraping Services

**Symptom**: No metrics in Prometheus

**Solution**: Update prometheus.yml with correct service endpoints
```yaml
# prometheus/prometheus.yml
scrape_configs:
  - job_name: 'sif-services'
    static_configs:
      - targets:
          - 'host.docker.internal:8080'  # gateway
          - 'host.docker.internal:8081'  # environment-service
          # ... other services
```



## Kubernetes Deployment

### Architecture

**Namespace**: `observability`

**Components**:
- Jaeger (Deployment + Service)
- Prometheus (StatefulSet + Service)
- Grafana (Deployment + Service)
- Elasticsearch (StatefulSet + Service)
- Logstash (Deployment + Service)
- Kibana (Deployment + Service)

### Prerequisites

**1. Create namespace**:
```bash
kubectl create namespace observability
```

**2. Create persistent volume claims**:
```bash
kubectl apply -f k8s/storage/
```

**3. Create config maps**:
```bash
kubectl create configmap prometheus-config \
  --from-file=prometheus.yml=prometheus/prometheus.yml \
  --from-file=alerts.yml=prometheus/alerts.yml \
  -n observability

kubectl create configmap logstash-config \
  --from-file=logstash.yml=logstash/config/logstash.yml \
  -n observability

kubectl create configmap logstash-pipeline \
  --from-file=logstash.conf=logstash/pipeline/logstash.conf \
  -n observability
```

### Deploying Jaeger

**jaeger-deployment.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  namespace: observability
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
      - name: jaeger
        image: jaegertracing/all-in-one:1.50
        ports:
        - containerPort: 16686
          name: ui
        - containerPort: 14250
          name: grpc
        - containerPort: 14268
          name: http
        env:
        - name: COLLECTOR_OTLP_ENABLED
          value: "true"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        volumeMounts:
        - name: jaeger-data
          mountPath: /tmp
      volumes:
      - name: jaeger-data
        persistentVolumeClaim:
          claimName: jaeger-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger
  namespace: observability
spec:
  selector:
    app: jaeger
  ports:
  - name: ui
    port: 16686
    targetPort: 16686
  - name: grpc
    port: 14250
    targetPort: 14250
  - name: http
    port: 14268
    targetPort: 14268
```

**Deploy**:
```bash
kubectl apply -f jaeger-deployment.yaml
```



### Deploying Prometheus

**prometheus-statefulset.yaml**:
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: prometheus
  namespace: observability
spec:
  serviceName: prometheus
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
      - name: prometheus
        image: prom/prometheus:v2.47.0
        args:
        - '--config.file=/etc/prometheus/prometheus.yml'
        - '--storage.tsdb.path=/prometheus'
        - '--web.enable-lifecycle'
        ports:
        - containerPort: 9090
          name: http
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        volumeMounts:
        - name: prometheus-config
          mountPath: /etc/prometheus
        - name: prometheus-data
          mountPath: /prometheus
      volumes:
      - name: prometheus-config
        configMap:
          name: prometheus-config
  volumeClaimTemplates:
  - metadata:
      name: prometheus-data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 50Gi
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus
  namespace: observability
spec:
  selector:
    app: prometheus
  ports:
  - port: 9090
    targetPort: 9090
```

**Service Discovery for Kubernetes**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
    - role: pod
      namespaces:
        names:
        - sif-unity-broker
    relabel_configs:
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
      action: keep
      regex: true
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
      action: replace
      target_label: __metrics_path__
      regex: (.+)
    - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
      action: replace
      regex: ([^:]+)(?::\d+)?;(\d+)
      replacement: $1:$2
      target_label: __address__
```

**Annotate SIF Services for Scraping**:
```yaml
# In your service deployment
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/actuator/prometheus"
```



### Deploying Grafana

**grafana-deployment.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: observability
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
      - name: grafana
        image: grafana/grafana:10.2.0
        ports:
        - containerPort: 3000
          name: http
        env:
        - name: GF_SECURITY_ADMIN_USER
          valueFrom:
            secretKeyRef:
              name: grafana-credentials
              key: admin-user
        - name: GF_SECURITY_ADMIN_PASSWORD
          valueFrom:
            secretKeyRef:
              name: grafana-credentials
              key: admin-password
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        volumeMounts:
        - name: grafana-data
          mountPath: /var/lib/grafana
        - name: grafana-provisioning
          mountPath: /etc/grafana/provisioning
        - name: grafana-dashboards
          mountPath: /var/lib/grafana/dashboards
      volumes:
      - name: grafana-data
        persistentVolumeClaim:
          claimName: grafana-pvc
      - name: grafana-provisioning
        configMap:
          name: grafana-provisioning
      - name: grafana-dashboards
        configMap:
          name: grafana-dashboards
---
apiVersion: v1
kind: Service
metadata:
  name: grafana
  namespace: observability
spec:
  selector:
    app: grafana
  ports:
  - port: 3000
    targetPort: 3000
```

**Create Grafana Credentials Secret**:
```bash
kubectl create secret generic grafana-credentials \
  --from-literal=admin-user=admin \
  --from-literal=admin-password=<strong-password> \
  -n observability
```

### Deploying ELK Stack

**Elasticsearch StatefulSet**:
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: elasticsearch
  namespace: observability
spec:
  serviceName: elasticsearch
  replicas: 3
  selector:
    matchLabels:
      app: elasticsearch
  template:
    metadata:
      labels:
        app: elasticsearch
    spec:
      initContainers:
      - name: increase-vm-max-map
        image: busybox
        command: ["sysctl", "-w", "vm.max_map_count=262144"]
        securityContext:
          privileged: true
      containers:
      - name: elasticsearch
        image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
        ports:
        - containerPort: 9200
          name: http
        - containerPort: 9300
          name: transport
        env:
        - name: cluster.name
          value: "sif-logs"
        - name: node.name
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: discovery.seed_hosts
          value: "elasticsearch-0.elasticsearch,elasticsearch-1.elasticsearch,elasticsearch-2.elasticsearch"
        - name: cluster.initial_master_nodes
          value: "elasticsearch-0,elasticsearch-1,elasticsearch-2"
        - name: ES_JAVA_OPTS
          value: "-Xms2g -Xmx2g"
        - name: xpack.security.enabled
          value: "false"
        resources:
          requests:
            memory: "4Gi"
            cpu: "1000m"
          limits:
            memory: "8Gi"
            cpu: "2000m"
        volumeMounts:
        - name: elasticsearch-data
          mountPath: /usr/share/elasticsearch/data
  volumeClaimTemplates:
  - metadata:
      name: elasticsearch-data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 100Gi
```



### Exposing Services via Ingress

**ingress.yaml**:
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: observability-ingress
  namespace: observability
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/auth-type: basic
    nginx.ingress.kubernetes.io/auth-secret: observability-basic-auth
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - jaeger.your-domain.com
    - prometheus.your-domain.com
    - grafana.your-domain.com
    - kibana.your-domain.com
    secretName: observability-tls
  rules:
  - host: jaeger.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: jaeger
            port:
              number: 16686
  - host: prometheus.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: prometheus
            port:
              number: 9090
  - host: grafana.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: grafana
            port:
              number: 3000
  - host: kibana.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: kibana
            port:
              number: 5601
```

**Create Basic Auth Secret**:
```bash
htpasswd -c auth admin
kubectl create secret generic observability-basic-auth \
  --from-file=auth \
  -n observability
```

### Verifying Deployment

**Check all pods are running**:
```bash
kubectl get pods -n observability
```

**Check services**:
```bash
kubectl get svc -n observability
```

**Check persistent volumes**:
```bash
kubectl get pvc -n observability
```

**Test connectivity**:
```bash
# Port-forward to test locally
kubectl port-forward -n observability svc/jaeger 16686:16686
kubectl port-forward -n observability svc/prometheus 9090:9090
kubectl port-forward -n observability svc/grafana 3000:3000
kubectl port-forward -n observability svc/kibana 5601:5601
```

## Configuring Alerting Destinations

### Prometheus Alertmanager

**1. Deploy Alertmanager**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: alertmanager
  namespace: observability
spec:
  replicas: 1
  selector:
    matchLabels:
      app: alertmanager
  template:
    metadata:
      labels:
        app: alertmanager
    spec:
      containers:
      - name: alertmanager
        image: prom/alertmanager:v0.26.0
        args:
        - '--config.file=/etc/alertmanager/alertmanager.yml'
        ports:
        - containerPort: 9093
        volumeMounts:
        - name: alertmanager-config
          mountPath: /etc/alertmanager
      volumes:
      - name: alertmanager-config
        configMap:
          name: alertmanager-config
```



**2. Configure Email Notifications**:
```yaml
# alertmanager.yml
global:
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'alerts@your-domain.com'
  smtp_auth_username: 'alerts@your-domain.com'
  smtp_auth_password: '<app-password>'

route:
  group_by: ['alertname', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'email-notifications'

receivers:
- name: 'email-notifications'
  email_configs:
  - to: 'ops-team@your-domain.com'
    headers:
      Subject: '[SIF Alert] {{ .GroupLabels.alertname }}'
```

**3. Configure Slack Notifications**:
```yaml
receivers:
- name: 'slack-notifications'
  slack_configs:
  - api_url: 'https://hooks.slack.com/services/YOUR/WEBHOOK/URL'
    channel: '#sif-alerts'
    title: '{{ .GroupLabels.alertname }}'
    text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
    send_resolved: true
```

**4. Configure PagerDuty**:
```yaml
receivers:
- name: 'pagerduty-critical'
  pagerduty_configs:
  - service_key: '<your-pagerduty-integration-key>'
    description: '{{ .GroupLabels.alertname }}: {{ .GroupLabels.service }}'
```

**5. Route Alerts by Severity**:
```yaml
route:
  receiver: 'email-notifications'
  routes:
  - match:
      severity: critical
    receiver: 'pagerduty-critical'
  - match:
      severity: warning
    receiver: 'slack-notifications'
```

**6. Update Prometheus to use Alertmanager**:
```yaml
# prometheus.yml
alerting:
  alertmanagers:
  - static_configs:
    - targets:
      - alertmanager:9093
```

### Testing Alerts

**1. Trigger a test alert**:
```bash
# Stop a service to trigger ServiceDown alert
kubectl scale deployment queue-service --replicas=0 -n sif-unity-broker

# Wait 1 minute for alert to fire
# Check Alertmanager
kubectl port-forward -n observability svc/alertmanager 9093:9093
# Open http://localhost:9093
```

**2. Verify alert notification**:
- Check email inbox
- Check Slack channel
- Check PagerDuty incidents

**3. Restore service**:
```bash
kubectl scale deployment queue-service --replicas=1 -n sif-unity-broker
```

## Managing Retention Policies

### Prometheus Retention

**Configure retention time**:
```yaml
# prometheus-statefulset.yaml
args:
- '--storage.tsdb.retention.time=30d'  # Keep 30 days
- '--storage.tsdb.retention.size=50GB'  # Or 50GB max
```

**Apply changes**:
```bash
kubectl apply -f prometheus-statefulset.yaml
kubectl rollout restart statefulset/prometheus -n observability
```

### Elasticsearch Retention

**1. Create Index Lifecycle Policy**:
```bash
curl -X PUT "http://elasticsearch:9200/_ilm/policy/sif-logs-policy" -H 'Content-Type: application/json' -d'
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_age": "1d",
            "max_size": "50gb"
          }
        }
      },
      "delete": {
        "min_age": "30d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}'
```

**2. Apply policy to indices**:
```bash
curl -X PUT "http://elasticsearch:9200/sif-logs-*/_settings" -H 'Content-Type: application/json' -d'
{
  "index.lifecycle.name": "sif-logs-policy"
}'
```

**3. Verify policy**:
```bash
curl "http://elasticsearch:9200/_ilm/policy/sif-logs-policy"
```



### Jaeger Retention

**Configure retention in Jaeger**:
```yaml
# jaeger-deployment.yaml
env:
- name: SPAN_STORAGE_TYPE
  value: "elasticsearch"
- name: ES_SERVER_URLS
  value: "http://elasticsearch:9200"
- name: ES_INDEX_PREFIX
  value: "jaeger"
- name: ES_TAGS_AS_FIELDS_ALL
  value: "true"
# Retention: delete traces older than 7 days
- name: ES_MAX_SPAN_AGE
  value: "168h"  # 7 days
```

**Manual cleanup** (if needed):
```bash
# Delete old Jaeger indices
curl -X DELETE "http://elasticsearch:9200/jaeger-span-$(date -d '7 days ago' +%Y-%m-%d)"
```

### Grafana Retention

**Configure dashboard retention**:
```yaml
# grafana.ini
[dashboards]
versions_to_keep = 20  # Keep last 20 versions
```

**Backup dashboards**:
```bash
# Export all dashboards
kubectl exec -n observability grafana-xxx -- \
  grafana-cli admin export-dashboard > dashboards-backup.json
```

## Scaling Observability Components

### Scaling Prometheus

**Vertical Scaling** (increase resources):
```yaml
resources:
  requests:
    memory: "4Gi"
    cpu: "2000m"
  limits:
    memory: "8Gi"
    cpu: "4000m"
```

**Horizontal Scaling** (federation):
```yaml
# Deploy multiple Prometheus instances
# Configure federation in main Prometheus
scrape_configs:
- job_name: 'federate'
  honor_labels: true
  metrics_path: '/federate'
  params:
    'match[]':
      - '{job=~".*"}'
  static_configs:
  - targets:
    - 'prometheus-1:9090'
    - 'prometheus-2:9090'
```

### Scaling Elasticsearch

**Horizontal Scaling**:
```bash
# Increase replicas
kubectl scale statefulset elasticsearch --replicas=5 -n observability
```

**Shard Allocation**:
```bash
# Configure number of shards per index
curl -X PUT "http://elasticsearch:9200/_template/sif-logs" -H 'Content-Type: application/json' -d'
{
  "index_patterns": ["sif-logs-*"],
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1
  }
}'
```

### Scaling Logstash

**Horizontal Scaling**:
```bash
# Increase replicas
kubectl scale deployment logstash --replicas=3 -n observability
```

**Load Balancing**:
```yaml
# Use Service to load balance across Logstash instances
apiVersion: v1
kind: Service
metadata:
  name: logstash
spec:
  selector:
    app: logstash
  ports:
  - port: 5000
    targetPort: 5000
  type: LoadBalancer
```

### Scaling Jaeger

**Horizontal Scaling** (with Elasticsearch backend):
```bash
# Scale collector
kubectl scale deployment jaeger-collector --replicas=3 -n observability

# Scale query
kubectl scale deployment jaeger-query --replicas=2 -n observability
```

### Scaling Grafana

**Horizontal Scaling**:
```bash
# Increase replicas
kubectl scale deployment grafana --replicas=3 -n observability
```

**Session Affinity**:
```yaml
# grafana-service.yaml
spec:
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 10800
```

## Backup and Recovery

### Backing Up Prometheus Data

**1. Create backup**:
```bash
# Take snapshot
kubectl exec -n observability prometheus-0 -- \
  curl -X POST http://localhost:9090/api/v1/admin/tsdb/snapshot

# Copy snapshot
kubectl cp observability/prometheus-0:/prometheus/snapshots/<snapshot-id> \
  ./prometheus-backup-$(date +%Y%m%d)
```

**2. Restore from backup**:
```bash
# Copy backup to pod
kubectl cp ./prometheus-backup-20240115 \
  observability/prometheus-0:/prometheus/

# Restart Prometheus
kubectl rollout restart statefulset/prometheus -n observability
```

### Backing Up Elasticsearch Data

**1. Create snapshot repository**:
```bash
curl -X PUT "http://elasticsearch:9200/_snapshot/backup_repo" -H 'Content-Type: application/json' -d'
{
  "type": "fs",
  "settings": {
    "location": "/usr/share/elasticsearch/backup"
  }
}'
```

**2. Create snapshot**:
```bash
curl -X PUT "http://elasticsearch:9200/_snapshot/backup_repo/snapshot_$(date +%Y%m%d)" -d'
{
  "indices": "sif-logs-*,jaeger-*",
  "ignore_unavailable": true,
  "include_global_state": false
}'
```

**3. Restore snapshot**:
```bash
curl -X POST "http://elasticsearch:9200/_snapshot/backup_repo/snapshot_20240115/_restore" -d'
{
  "indices": "sif-logs-*,jaeger-*"
}'
```



### Backing Up Grafana Dashboards

**1. Export dashboards**:
```bash
# Export all dashboards
for dashboard in $(curl -s http://admin:admin@localhost:3000/api/search | jq -r '.[].uid'); do
  curl -s http://admin:admin@localhost:3000/api/dashboards/uid/$dashboard | \
    jq '.dashboard' > dashboard-$dashboard.json
done
```

**2. Import dashboards**:
```bash
# Import dashboard
curl -X POST http://admin:admin@localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @dashboard-xxx.json
```

**3. Automated backup with CronJob**:
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: grafana-backup
  namespace: observability
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: appropriate/curl
            command:
            - /bin/sh
            - -c
            - |
              curl -s http://grafana:3000/api/search | \
              jq -r '.[].uid' | \
              while read uid; do
                curl -s http://grafana:3000/api/dashboards/uid/$uid > /backup/dashboard-$uid.json
              done
            volumeMounts:
            - name: backup
              mountPath: /backup
          volumes:
          - name: backup
            persistentVolumeClaim:
              claimName: grafana-backup-pvc
          restartPolicy: OnFailure
```

## Monitoring the Observability Stack

### Health Checks

**Check all components**:
```bash
# Jaeger
curl http://jaeger:14269/

# Prometheus
curl http://prometheus:9090/-/healthy

# Grafana
curl http://grafana:3000/api/health

# Elasticsearch
curl http://elasticsearch:9200/_cluster/health

# Logstash
curl http://logstash:9600/_node/stats

# Kibana
curl http://kibana:5601/api/status
```

### Resource Monitoring

**Prometheus queries for observability stack**:
```promql
# CPU usage
container_cpu_usage_seconds_total{namespace="observability"}

# Memory usage
container_memory_usage_bytes{namespace="observability"}

# Disk usage
kubelet_volume_stats_used_bytes{namespace="observability"}

# Network I/O
container_network_receive_bytes_total{namespace="observability"}
```

### Log Monitoring

**Check for errors in observability components**:
```bash
# Jaeger errors
kubectl logs -n observability -l app=jaeger --tail=100 | grep -i error

# Prometheus errors
kubectl logs -n observability -l app=prometheus --tail=100 | grep -i error

# Elasticsearch errors
kubectl logs -n observability -l app=elasticsearch --tail=100 | grep -i error

# Logstash errors
kubectl logs -n observability -l app=logstash --tail=100 | grep -i error
```

### Performance Metrics

**Prometheus ingestion rate**:
```promql
rate(prometheus_tsdb_head_samples_appended_total[5m])
```

**Elasticsearch indexing rate**:
```bash
curl "http://elasticsearch:9200/_stats/indexing" | jq '.indices.indexing.index_total'
```

**Jaeger span ingestion rate**:
```promql
rate(jaeger_collector_spans_received_total[5m])
```

### Alerting for Observability Stack

**Add alerts for observability components**:
```yaml
# prometheus-alerts.yml
groups:
- name: observability_health
  rules:
  - alert: PrometheusDown
    expr: up{job="prometheus"} == 0
    for: 5m
    annotations:
      summary: "Prometheus is down"
  
  - alert: ElasticsearchDown
    expr: up{job="elasticsearch"} == 0
    for: 5m
    annotations:
      summary: "Elasticsearch is down"
  
  - alert: HighElasticsearchDiskUsage
    expr: |
      (
        kubelet_volume_stats_used_bytes{namespace="observability",persistentvolumeclaim=~"elasticsearch.*"}
        /
        kubelet_volume_stats_capacity_bytes{namespace="observability",persistentvolumeclaim=~"elasticsearch.*"}
      ) > 0.85
    for: 10m
    annotations:
      summary: "Elasticsearch disk usage above 85%"
```

## Maintenance Tasks

### Regular Maintenance

**Daily**:
- Check service health
- Review error logs
- Verify backups completed

**Weekly**:
- Review disk usage trends
- Check for slow queries
- Review alert history
- Update dashboards if needed

**Monthly**:
- Review retention policies
- Clean up old data
- Update component versions
- Review capacity planning

### Updating Components

**Update Prometheus**:
```bash
# Update image version
kubectl set image statefulset/prometheus \
  prometheus=prom/prometheus:v2.48.0 \
  -n observability

# Verify rollout
kubectl rollout status statefulset/prometheus -n observability
```

**Update Grafana**:
```bash
kubectl set image deployment/grafana \
  grafana=grafana/grafana:10.3.0 \
  -n observability
```

**Update Elasticsearch** (rolling update):
```bash
# Update one node at a time
kubectl set image statefulset/elasticsearch \
  elasticsearch=docker.elastic.co/elasticsearch/elasticsearch:8.12.0 \
  -n observability

# Monitor cluster health during update
watch curl http://elasticsearch:9200/_cluster/health
```

### Troubleshooting Production Issues

**High Prometheus Memory Usage**:
```bash
# Check cardinality
curl http://prometheus:9090/api/v1/status/tsdb | jq '.data.seriesCountByMetricName'

# Reduce retention
kubectl edit statefulset prometheus -n observability
# Change --storage.tsdb.retention.time=15d
```

**Elasticsearch Cluster Red**:
```bash
# Check cluster health
curl http://elasticsearch:9200/_cluster/health?pretty

# Check shard allocation
curl http://elasticsearch:9200/_cat/shards?v

# Reallocate shards
curl -X POST "http://elasticsearch:9200/_cluster/reroute?retry_failed=true"
```

**Logstash Pipeline Blocked**:
```bash
# Check pipeline stats
curl http://logstash:9600/_node/stats/pipelines

# Increase workers
kubectl edit deployment logstash -n observability
# Add env: LS_PIPELINE_WORKERS=4
```

## Security Considerations

### Authentication

**Enable Grafana OAuth**:
```yaml
env:
- name: GF_AUTH_GENERIC_OAUTH_ENABLED
  value: "true"
- name: GF_AUTH_GENERIC_OAUTH_CLIENT_ID
  value: "<client-id>"
- name: GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET
  valueFrom:
    secretKeyRef:
      name: grafana-oauth
      key: client-secret
```

**Enable Elasticsearch Security**:
```yaml
env:
- name: xpack.security.enabled
  value: "true"
- name: ELASTIC_PASSWORD
  valueFrom:
    secretKeyRef:
      name: elasticsearch-credentials
      key: password
```

### Network Policies

**Restrict access to observability namespace**:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: observability-network-policy
  namespace: observability
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: sif-unity-broker
    - namespaceSelector:
        matchLabels:
          name: observability
```

### TLS/SSL

**Enable TLS for Elasticsearch**:
```yaml
env:
- name: xpack.security.transport.ssl.enabled
  value: "true"
- name: xpack.security.http.ssl.enabled
  value: "true"
```

## Additional Resources

- [Configuration Guide](./configuration-guide.md)
- [Troubleshooting Runbooks](./troubleshooting-runbooks.md)
- [Jaeger Usage Guide](./jaeger-usage-guide.md)
- [Kibana Usage Guide](./kibana-usage-guide.md)
- [Grafana Dashboard Guide](./grafana-dashboard-guide.md)
- [Custom Instrumentation Guide](./custom-instrumentation-guide.md)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Helm Charts](https://helm.sh/docs/)
