# Kibana Saved Objects for SIF Unity Broker

## Overview

This directory contains Kibana saved objects exported as NDJSON for the SIF Unity Broker observability stack. The export includes an index pattern, a saved search, visualizations, and a dashboard for log analysis.

## Contents

### `sif-kibana-objects.ndjson`

| Object Type | ID | Name | Description |
|---|---|---|---|
| Index Pattern | `sif-logs-index-pattern` | `sif-logs-*` | Matches all SIF log indices with `@timestamp` as the time field |
| Saved Search | `sif-trace-search` | SIF Trace Search | Pre-configured search with columns for timestamp, service, level, message, trace_id, and correlation_id |
| Visualization | `sif-log-volume-by-service` | Log Volume by Service | Date histogram of log volume over time, split by service name |
| Visualization | `sif-log-level-distribution` | Log Level Distribution | Pie chart showing distribution of log entries by level (INFO, WARN, ERROR, etc.) |
| Dashboard | `sif-log-volume-dashboard` | SIF Log Volume | Dashboard combining log volume and log level panels with 10-second auto-refresh |

## Importing into Kibana

### Prerequisites

- Kibana 8.x running and accessible (default: `http://localhost:5601`)
- Elasticsearch indices matching `sif-logs-*` pattern (created by the Logstash pipeline)

### Import via Kibana UI

1. Open Kibana at `http://localhost:5601`
2. Navigate to **Stack Management** → **Saved Objects** (under Kibana section)
3. Click **Import** in the top-right corner
4. Select the `sif-kibana-objects.ndjson` file
5. Choose **Automatically overwrite conflicts** if re-importing
6. Click **Import**
7. Verify all 5 objects imported successfully

### Import via Kibana API

```bash
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@observability/docker/kibana/sif-kibana-objects.ndjson
```

### Import via Docker Compose (automated)

If using the observability Docker Compose stack, you can add a startup script to auto-import:

```bash
# Wait for Kibana to be ready, then import
until curl -s http://localhost:5601/api/status | grep -q '"overall":{"level":"available"'; do
  sleep 5
done

curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@/kibana-objects/sif-kibana-objects.ndjson
```

## Using the Saved Objects

### SIF Trace Search

Use this saved search to filter logs by trace or correlation ID:

1. Go to **Discover** in Kibana
2. Open the saved search **SIF Trace Search**
3. In the search bar, enter a specific trace_id or correlation_id:
   - `trace_id: "abc123def456"` — find all logs for a specific trace
   - `correlation_id: "corr-uuid-here"` — find all logs for a business correlation
4. The pre-configured columns show timestamp, service, level, message, trace_id, and correlation_id

### SIF Log Volume Dashboard

1. Go to **Dashboard** in Kibana
2. Open **SIF Log Volume**
3. The dashboard shows:
   - **Log Volume by Service** (left panel): Date histogram showing log count over time, color-coded by service
   - **Log Level Distribution** (right panel): Pie chart showing the proportion of each log level
4. Use the time picker to adjust the time range
5. Auto-refresh is set to 10 seconds by default

## Troubleshooting

### No data in dashboard

- Verify Elasticsearch has indices matching `sif-logs-*`: `curl http://localhost:9200/_cat/indices/sif-logs-*`
- Ensure the Logstash pipeline is running and receiving logs
- Check that services are running with the `prod` Spring profile (required for Logstash TCP appender)

### Import fails

- Ensure Kibana version is 8.x (objects use 8.x saved object format)
- Check Kibana logs for detailed error messages
- Try importing with `overwrite=true` to replace existing objects

### Fields not available in search

- After first importing, go to **Stack Management** → **Data Views** and refresh the `sif-logs-*` field list
- Ensure log entries contain the expected fields (service, level, trace_id, correlation_id)
