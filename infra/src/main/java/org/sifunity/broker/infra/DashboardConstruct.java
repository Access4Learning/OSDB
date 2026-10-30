package org.sifunity.broker.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CloudWatch Dashboard for OSDB infrastructure monitoring.
 *
 * <p>Sections:
 * <ul>
 *   <li>ECS Services: CPU and Memory utilization per service</li>
 *   <li>PostgreSQL (RDS): CPU, connections, IOPS, freeable memory</li>
 *   <li>Redis (ElastiCache): Memory usage, CPU, cache hit rate, connections</li>
 *   <li>ALB/Gateway: Request count, response time, error codes, connections</li>
 * </ul>
 */
public class DashboardConstruct extends Construct {

    private static final Duration PERIOD = Duration.minutes(1);
    private static final int WIDGET_WIDTH = 6;
    private static final int WIDGET_HEIGHT = 6;

    public DashboardConstruct(final Construct scope, final String id,
                              final String environment,
                              final String clusterName,
                              final List<String> serviceNames,
                              final DatabaseInstance rdsInstance,
                              final CfnCacheCluster redisCluster,
                              final ApplicationLoadBalancer alb) {
        super(scope, id);

        Dashboard dashboard = Dashboard.Builder.create(this, "OsdbDashboard")
                .dashboardName("osdb-" + environment.toLowerCase() + "-dashboard")
                .build();

        // --- Section 1: ECS Services CPU & Memory ---
        dashboard.addWidgets(createEcsCpuWidget(clusterName, serviceNames));
        dashboard.addWidgets(createEcsMemoryWidget(clusterName, serviceNames));

        // --- Section 2: PostgreSQL (RDS) ---
        String dbInstanceId = rdsInstance.getInstanceIdentifier();
        dashboard.addWidgets(
                createRdsCpuWidget(dbInstanceId),
                createRdsConnectionsWidget(dbInstanceId),
                createRdsIopsWidget(dbInstanceId),
                createRdsFreeableMemoryWidget(dbInstanceId));

        // --- Section 3: Redis (ElastiCache) ---
        String cacheClusterId = redisCluster.getRef();
        dashboard.addWidgets(
                createRedisMemoryWidget(cacheClusterId),
                createRedisCpuWidget(cacheClusterId),
                createRedisCacheHitRateWidget(cacheClusterId),
                createRedisConnectionsWidget(cacheClusterId));

        // --- Section 4: ALB / Gateway ---
        // ALB full name for metrics dimension (e.g., "app/my-lb/50dc6c495c0c9188")
        String albFullName = alb.getLoadBalancerFullName();
        dashboard.addWidgets(
                createAlbRequestCountWidget(albFullName),
                createAlbResponseTimeWidget(albFullName),
                createAlbErrorsWidget(albFullName),
                createAlbConnectionsWidget(albFullName));
    }

    // ========== ECS Widgets ==========

    private GraphWidget createEcsCpuWidget(String clusterName, List<String> serviceNames) {
        List<IMetric> metrics = new ArrayList<>();
        for (String svc : serviceNames) {
            metrics.add(Metric.Builder.create()
                    .namespace("AWS/ECS")
                    .metricName("CPUUtilization")
                    .dimensionsMap(Map.of("ClusterName", clusterName, "ServiceName", svc))
                    .period(PERIOD)
                    .statistic("Average")
                    .label(svc)
                    .build());
        }
        return GraphWidget.Builder.create()
                .title("ECS CPU Utilization (all services)")
                .left(metrics)
                .width(24)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createEcsMemoryWidget(String clusterName, List<String> serviceNames) {
        List<IMetric> metrics = new ArrayList<>();
        for (String svc : serviceNames) {
            metrics.add(Metric.Builder.create()
                    .namespace("AWS/ECS")
                    .metricName("MemoryUtilization")
                    .dimensionsMap(Map.of("ClusterName", clusterName, "ServiceName", svc))
                    .period(PERIOD)
                    .statistic("Average")
                    .label(svc)
                    .build());
        }
        return GraphWidget.Builder.create()
                .title("ECS Memory Utilization (all services)")
                .left(metrics)
                .width(24)
                .height(WIDGET_HEIGHT)
                .build();
    }

    // ========== RDS Widgets ==========

    private GraphWidget createRdsCpuWidget(String dbInstanceId) {
        return GraphWidget.Builder.create()
                .title("RDS CPU Utilization")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/RDS")
                        .metricName("CPUUtilization")
                        .dimensionsMap(Map.of("DBInstanceIdentifier", dbInstanceId))
                        .period(PERIOD)
                        .statistic("Average")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createRdsConnectionsWidget(String dbInstanceId) {
        return GraphWidget.Builder.create()
                .title("RDS Database Connections")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/RDS")
                        .metricName("DatabaseConnections")
                        .dimensionsMap(Map.of("DBInstanceIdentifier", dbInstanceId))
                        .period(PERIOD)
                        .statistic("Average")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createRdsIopsWidget(String dbInstanceId) {
        return GraphWidget.Builder.create()
                .title("RDS IOPS (Read + Write)")
                .left(List.of(
                        Metric.Builder.create()
                                .namespace("AWS/RDS")
                                .metricName("ReadIOPS")
                                .dimensionsMap(Map.of("DBInstanceIdentifier", dbInstanceId))
                                .period(PERIOD)
                                .statistic("Average")
                                .label("Read IOPS")
                                .build(),
                        Metric.Builder.create()
                                .namespace("AWS/RDS")
                                .metricName("WriteIOPS")
                                .dimensionsMap(Map.of("DBInstanceIdentifier", dbInstanceId))
                                .period(PERIOD)
                                .statistic("Average")
                                .label("Write IOPS")
                                .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createRdsFreeableMemoryWidget(String dbInstanceId) {
        return GraphWidget.Builder.create()
                .title("RDS Freeable Memory")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/RDS")
                        .metricName("FreeableMemory")
                        .dimensionsMap(Map.of("DBInstanceIdentifier", dbInstanceId))
                        .period(PERIOD)
                        .statistic("Average")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    // ========== Redis Widgets ==========

    private GraphWidget createRedisMemoryWidget(String cacheClusterId) {
        return GraphWidget.Builder.create()
                .title("Redis Memory Usage %")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/ElastiCache")
                        .metricName("DatabaseMemoryUsagePercentage")
                        .dimensionsMap(Map.of("CacheClusterId", cacheClusterId))
                        .period(PERIOD)
                        .statistic("Average")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createRedisCpuWidget(String cacheClusterId) {
        return GraphWidget.Builder.create()
                .title("Redis Engine CPU Utilization")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/ElastiCache")
                        .metricName("EngineCPUUtilization")
                        .dimensionsMap(Map.of("CacheClusterId", cacheClusterId))
                        .period(PERIOD)
                        .statistic("Average")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createRedisCacheHitRateWidget(String cacheClusterId) {
        return GraphWidget.Builder.create()
                .title("Redis Cache Hit Rate")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/ElastiCache")
                        .metricName("CacheHitRate")
                        .dimensionsMap(Map.of("CacheClusterId", cacheClusterId))
                        .period(PERIOD)
                        .statistic("Average")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createRedisConnectionsWidget(String cacheClusterId) {
        return GraphWidget.Builder.create()
                .title("Redis Current Connections")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/ElastiCache")
                        .metricName("CurrConnections")
                        .dimensionsMap(Map.of("CacheClusterId", cacheClusterId))
                        .period(PERIOD)
                        .statistic("Average")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    // ========== ALB Widgets ==========

    private GraphWidget createAlbRequestCountWidget(String albFullName) {
        return GraphWidget.Builder.create()
                .title("ALB Request Count")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/ApplicationELB")
                        .metricName("RequestCount")
                        .dimensionsMap(Map.of("LoadBalancer", albFullName))
                        .period(PERIOD)
                        .statistic("Sum")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createAlbResponseTimeWidget(String albFullName) {
        return GraphWidget.Builder.create()
                .title("ALB Target Response Time")
                .left(List.of(
                        Metric.Builder.create()
                                .namespace("AWS/ApplicationELB")
                                .metricName("TargetResponseTime")
                                .dimensionsMap(Map.of("LoadBalancer", albFullName))
                                .period(PERIOD)
                                .statistic("Average")
                                .label("Average")
                                .build(),
                        Metric.Builder.create()
                                .namespace("AWS/ApplicationELB")
                                .metricName("TargetResponseTime")
                                .dimensionsMap(Map.of("LoadBalancer", albFullName))
                                .period(PERIOD)
                                .statistic("p99")
                                .label("p99")
                                .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createAlbErrorsWidget(String albFullName) {
        return GraphWidget.Builder.create()
                .title("ALB Error Codes (4XX + 5XX)")
                .left(List.of(
                        Metric.Builder.create()
                                .namespace("AWS/ApplicationELB")
                                .metricName("HTTPCode_Target_4XX_Count")
                                .dimensionsMap(Map.of("LoadBalancer", albFullName))
                                .period(PERIOD)
                                .statistic("Sum")
                                .label("4XX")
                                .build(),
                        Metric.Builder.create()
                                .namespace("AWS/ApplicationELB")
                                .metricName("HTTPCode_Target_5XX_Count")
                                .dimensionsMap(Map.of("LoadBalancer", albFullName))
                                .period(PERIOD)
                                .statistic("Sum")
                                .label("5XX")
                                .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }

    private GraphWidget createAlbConnectionsWidget(String albFullName) {
        return GraphWidget.Builder.create()
                .title("ALB Active Connections")
                .left(List.of(Metric.Builder.create()
                        .namespace("AWS/ApplicationELB")
                        .metricName("ActiveConnectionCount")
                        .dimensionsMap(Map.of("LoadBalancer", albFullName))
                        .period(PERIOD)
                        .statistic("Sum")
                        .build()))
                .width(WIDGET_WIDTH)
                .height(WIDGET_HEIGHT)
                .build();
    }
}
