# Production Deployment Guide

## Pre-Deployment Checklist

### Code Quality & Testing

- [x] All unit tests passing
- [x] All integration tests passing
- [x] Code coverage > 80%
- [ ] Security scanning completed (OWASP)
- [ ] Performance testing with production-like load
- [ ] Load testing: 1000+ concurrent users
- [ ] Database backup strategy tested

### Environment Setup

- [ ] Production MongoDB cluster configured
  - [ ] Replica set with 3+ nodes
  - [ ] Regular backups enabled
  - [ ] Monitoring enabled
- [ ] Application servers provisioned
  - [ ] Java 21 installed
  - [ ] Log aggregation setup
- [ ] Load balancer configured
  - [ ] Health check endpoint configured
  - [ ] SSL/TLS certificates installed
  - [ ] Rate limiting configured

## Deployment Architecture

```
                         ┌─────────────────┐
                         │   DNS/CDN       │
                         │   (WAF if needed)
                         └────────┬────────┘
                                  │
                    ┌─────────────┴──────────────┐
                    │   Application Load         │
                    │   Balancer (AWS ELB/ALB)  │
                    └──────┬────────────┬────────┘
                           │            │
                    ┌──────▼──┐    ┌───▼──────┐
                    │ App-AZ1 │    │ App-AZ2  │
                    │ :8080   │    │ :8080    │
                    │ (3x)    │    │ (3x)     │
                    └──────┬──┘    └───┬──────┘
                           │           │
                    ┌──────▼───────────▼──┐
                    │  MongoDB Replica Set │
                    │  ┌──────────────────┤
                    │  │ Primary (Write)  │
                    │  ├──────────────────┤
                    │  │ Secondary-1      │
                    │  ├──────────────────┤
                    │  │ Secondary-2      │
                    │  └──────────────────┘
                    └──────────────────────┘
```

## Step 1: Build & Package

```bash
# Build the application
./mvnw clean package -DskipTests

# Output: target/url-shortner-backend-1.0.0.jar
```

## Step 2: Docker Build

```bash
# Build Docker image
docker build -t url-shortner-backend:1.0.0 .

# Tag for registry
docker tag url-shortner-backend:1.0.0 \
  your-registry.com/url-shortner-backend:1.0.0

# Push to registry
docker push your-registry.com/url-shortner-backend:1.0.0
```

## Step 3: Deploy to Production

### Option A: Kubernetes Deployment

Create `k8s-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: url-shortner
  namespace: production
spec:
  replicas: 3
  selector:
    matchLabels:
      app: url-shortner
  template:
    metadata:
      labels:
        app: url-shortner
    spec:
      containers:
      - name: url-shortner
        image: your-registry.com/url-shortner-backend:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: MONGODB_URI
          valueFrom:
            secretKeyRef:
              name: mongodb-secret
              key: uri
        - name: MONGODB_DATABASE
          value: "urlshortner"
        - name: SERVER_PORT
          value: "8080"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          readOnlyRootFilesystem: true
          allowPrivilegeEscalation: false

---
apiVersion: v1
kind: Service
metadata:
  name: url-shortner-service
  namespace: production
spec:
  selector:
    app: url-shortner
  type: LoadBalancer
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: url-shortner-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: url-shortner
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

Deploy:
```bash
kubectl apply -f k8s-deployment.yaml
kubectl get pods -n production
kubectl logs -f deployment/url-shortner -n production
```

### Option B: Docker Compose (Single Server)

```bash
docker-compose up -d
```

### Option C: EC2/VM Deployment

```bash
# SSH into server
ssh ec2-user@your-server.com

# Install Java 21
sudo yum install java-21-amazon-corretto

# Create application directory
sudo mkdir -p /opt/url-shortner
cd /opt/url-shortner

# Download JAR from registry
aws s3 cp s3://your-bucket/url-shortner-backend-1.0.0.jar .

# Create systemd service
sudo tee /etc/systemd/system/url-shortner.service > /dev/null << EOF
[Unit]
Description=URL Shortener Backend
After=network.target

[Service]
Type=simple
User=urlapps
WorkingDirectory=/opt/url-shortner
EnvironmentFile=/opt/url-shortner/.env
ExecStart=/usr/bin/java -jar url-shortner-backend-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Enable and start service
sudo systemctl enable url-shortner
sudo systemctl start url-shortner
sudo systemctl status url-shortner

# View logs
sudo journalctl -u url-shortner -f
```

## Step 4: MongoDB Setup

### Replica Set Configuration

```javascript
// Connect to primary
mongo --host mongo-primary:27017

// Initiate replica set
rs.initiate({
  _id: "urlshortner-rs",
  members: [
    { _id: 0, host: "mongo-primary:27017" },
    { _id: 1, host: "mongo-secondary-1:27017" },
    { _id: 2, host: "mongo-secondary-2:27017" }
  ]
})

// Verify
rs.status()

// Create database and user
use urlshortner

db.createUser({
  user: "appuser",
  pwd: "secure-password",
  roles: ["readWrite"]
})

// Enable backups
// (Requires MongoDB Enterprise or Atlas)
```

### Connection String

```
MONGODB_URI=mongodb://appuser:secure-password@mongo-primary:27017,mongo-secondary-1:27017,mongo-secondary-2:27017/?replicaSet=urlshortner-rs&authSource=admin
```

## Step 5: Load Balancer Configuration

### AWS ALB Configuration

```
Target Group Settings:
- Protocol: HTTP
- Port: 8080
- Health check path: /actuator/health
- Health check interval: 30 seconds
- Healthy threshold: 2
- Unhealthy threshold: 3
- Matcher: 200-299

Listener Rules:
- HTTP:80 → HTTPS:443 redirect
- HTTPS:443 → Target Group
- Path: /* → Application TG
- Path: /actuator/* → Metrics TG (restrict access)
```

### SSL/TLS Certificate

```bash
# Request certificate from AWS ACM
aws acm request-certificate \
  --domain-name shortener.example.com \
  --validation-method DNS
```

## Step 6: Monitoring & Alerting

### CloudWatch Alarms

```bash
# CPU utilization > 80%
aws cloudwatch put-metric-alarm \
  --alarm-name url-shortner-cpu-high \
  --alarm-description "High CPU usage" \
  --metric-name CPUUtilization \
  --namespace AWS/EC2 \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold

# Error rate > 1%
aws cloudwatch put-metric-alarm \
  --alarm-name url-shortner-errors-high \
  --alarm-description "High error rate" \
  --metric-name ErrorRate \
  --namespace Application \
  --statistic Average \
  --period 60 \
  --threshold 1 \
  --comparison-operator GreaterThanThreshold
```

### Application Metrics to Monitor

```
Application Level:
- Requests per second (RPS)
- Response time (P50, P95, P99)
- Error rate
- URL creation rate
- Click tracking rate

JVM Level:
- Memory usage
- GC pause time
- Thread count
- Connection pool utilization

Database Level:
- Query latency
- Replication lag
- Index usage
- Disk space

Infrastructure Level:
- CPU utilization
- Network I/O
- Disk I/O
```

### Prometheus Configuration

Create `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'url-shortener'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']

  - job_name: 'mongodb'
    static_configs:
      - targets: ['localhost:27017']
```

## Step 7: Backup Strategy

### Database Backups

```bash
# Daily backup at 02:00 UTC
0 2 * * * mongodump \
  --uri="mongodb://appuser:password@primary:27017/urlshortner" \
  --out=/backups/mongodb/$(date +\%Y\%m\%d)

# Enable point-in-time recovery (MongoDB Atlas)
Settings → Backup & Restore → Point-In-Time Restore
```

### Application Backup

```bash
# Backup Docker images
docker save url-shortner-backend:1.0.0 | \
  gzip > /backups/docker/url-shortner-backend-1.0.0.tar.gz

# Store in S3
aws s3 cp /backups/ s3://backup-bucket/url-shortner/ --recursive
```

## Step 8: Security Hardening

### Network Security

```bash
# Security Groups (AWS)
Inbound:
  - Port 80 (HTTP) from: 0.0.0.0/0
  - Port 443 (HTTPS) from: 0.0.0.0/0
  - Port 27017 (MongoDB) from: Application Security Group only
  - Port 9090 (Prometheus) from: Monitoring Security Group only

Outbound:
  - Allow all
```

### Application Security

```yaml
spring:
  web:
    cors:
      allowed-origins: "https://yourdomain.com"
      allowed-methods: "GET,POST"
      allowed-headers: "Content-Type"
      max-age: 3600
  
  security:
    require-https: true
    headers:
      content-security-policy: "default-src 'self'"
```

### Data Security

```bash
# Enable MongoDB encryption at rest
# (AWS: Use EBS encryption)
# (GCP: Use Cloud SQL encryption)
# (Azure: Use Transparent Data Encryption)

# Enable TLS for MongoDB connections
MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/

# Rotate credentials regularly
# AWSSystems Manager → Parameter Store
```

## Step 9: Logging & Log Aggregation

### ELK Stack Configuration

```yaml
logging:
  pattern:
    console: |
      %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
    json: |
      {"timestamp":"%d{yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}","level":"%p","thread":"%t","logger":"%c{1.}","msg":"%m","exception":"%ex"}
  level:
    root: INFO
    com.mks.open: INFO
    org.springframework: WARN
    org.mongodb: WARN
```

Setup Logstash:

```json
input {
  syslog {
    host => "0.0.0.0"
    port => 514
  }
}

filter {
  mutate {
    add_field => { "[@metadata][index_name]" => "url-shortener-%{+YYYY.MM.dd}" }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "%{[@metadata][index_name]}"
  }
}
```

## Step 10: Disaster Recovery

### RTO & RPO Targets

| Failure Scenario | RTO | RPO |
|------------------|-----|-----|
| Single app instance | 2 min | 0 |
| Single MongoDB node | 5 min | 0 (replica) |
| Entire region | 30 min | 5 min |
| Data corruption | 1 hour | 24 hours |

### Recovery Procedures

**Scenario 1: App Instance Failure**
```bash
# Auto-recovery (Kubernetes/ASG)
kubectl delete pod <failed-pod>  # Auto-replaced
# or
aws autoscaling set-desired-capacity --auto-scaling-group-name <asg> --desired-capacity 3
```

**Scenario 2: Database Failover**
```bash
# MongoDB handles automatically with replica set
# Verify is working
rs.status()

# Manual intervention if needed
rs.stepDown()
```

**Scenario 3: Region Failure**
```bash
# Activate standby region
aws route53 failover-dns-policy --switch-to standby-region

# Restore from backup
mongorestore --uri="mongodb://..." --archive=/backups/latest.archive
```

## Performance Tuning

### JVM Tuning

```bash
JAVA_OPTS="
  -server
  -Xms2g -Xmx2g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+ParallelRefProcEnabled
  -XX:+UnlockExperimentalVMOptions
  -XX:G1NewSizePercent=30
  -XX:G1MaxNewSizePercent=40
  -Dfile.encoding=UTF-8
"
```

### MongoDB Tuning

```javascript
// Configure index settings
db.setProfilingLevel(1, { slowms: 100 })

// Monitor operations
db.system.profile.find().limit(5).sort({ ts: -1 }).pretty()

// Optimize query plans
db.urls.createIndex({ shortCode: 1 }, { unique: true })
db.urls.createIndex({ createdAt: -1 })
```

### Application Tuning

```yaml
spring:
  data:
    mongodb:
      maxPoolSize: 200
      minPoolSize: 50
      maxWaitTimeMS: 30000
  mvc:
    async:
      request-timeout: 30000
```

## Scaling Strategy

### Horizontal Scaling Decision Tree

```
Monitor Metrics
    │
    ├─ CPU > 80% → Scale up
    ├─ Memory > 85% → Scale up
    ├─ Request latency > 100ms → Scale up
    └─ Error rate > 1% → Investigate then scale
```

### Auto-Scaling Configuration

```yaml
# Kubernetes HPA
minReplicas: 3
maxReplicas: 10
targetCPUUtilization: 70%
targetMemoryUtilization: 80%

# AWS ASG
MinSize: 3
MaxSize: 10
DesiredCapacity: 3
HealthCheckType: ELB
HealthCheckGracePeriod: 300
```

## Maintenance Windows

### Zero-Downtime Deployment

```bash
# 1. Build new version
docker build -t url-shortner:v2.0.0 .

# 2. Push to registry
docker push registry.com/url-shortner:v2.0.0

# 3. Update deployment (rolling update)
kubectl set image deployment/url-shortner \
  url-shortner=registry.com/url-shortner:v2.0.0 -n production

# 4. Verify health
kubectl rollout status deployment/url-shortner -n production

# 5. Rollback if needed
kubectl rollout undo deployment/url-shortner -n production
```

### Database Migration

```bash
# 1. Create backup
mongodump --uri="mongodb://..." --out=/backups/pre-migration

# 2. Run migration
java -jar migrate.jar --source mongodb://old --target mongodb://new

# 3. Verify data integrity
db.urls.countDocuments()
db.urls.find().sample(100).forEach(doc => print(JSON.stringify(doc)))

# 4. Cutover (point connection string to new DB)

# 5. Monitor
```

## Cost Optimization

### EC2 Instance Sizing

| Traffic | Instances | Instance Type | Cost/month |
|---------|-----------|---------------|------------|
| ~100 req/s | 3 | t3.medium | $60 |
| ~500 req/s | 3-5 | c5.large | $200 |
| ~1000 req/s | 5-10 | c5.xlarge | $500+ |

### MongoDB Sizing

| Storage | Replicas | Atlas Tier | Cost/month |
|---------|----------|-----------|-----------|
| 10 GB | 3 | M10 | $57 |
| 100 GB | 3 | M20 | $570 |
| 1 TB | 3 | M30 | $5700 |

### Cost Reduction Tips

1. Use spot instances for non-critical workloads: -70% cost
2. Reserve instances for baseline: -40% cost
3. Implement caching layer: Reduce DB load
4. Archive old URLs: Reduce storage

## Post-Deployment Checklist

- [ ] Health checks passing
- [ ] Monitoring alerts active
- [ ] Backups running
- [ ] Log aggregation working
- [ ] Metrics visible in dashboards
- [ ] Team trained on runbooks
- [ ] On-call rotation activated
- [ ] Customer notifications sent
- [ ] Performance baselines established
- [ ] Security scan passed

## Support & Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| High latency | DB overload | Scale MongoDB or add cache |
| Out of memory | JVM config | Increase Xmx, analyze heap dump |
| Connection pool exhausted | Leak | Check for unclosed connections |
| MongoDB replica lag | Network | Check MongoDB replication status |

### Runbooks Location

- Emergency procedures: `/docs/runbooks/emergency/`
- Scaling procedures: `/docs/runbooks/scaling/`
- Backup recovery: `/docs/runbooks/disaster-recovery/`

## References

- [Spring Boot Production Checklist](https://spring.io/guides/gs/spring-boot/)
- [MongoDB Ops Manager](https://docs.mongodb.com/ops-manager/)
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/production-environment/)
- [12-Factor App](https://12factor.net/)

