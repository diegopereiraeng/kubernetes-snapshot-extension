package com.appdynamics.monitors.kubernetes.SnapshotTasks;

import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.monitors.kubernetes.Globals;
import com.appdynamics.monitors.kubernetes.Metrics.UploadMetricsTask;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.appdynamics.monitors.kubernetes.RestClient;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.security.AllPermission;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.appdynamics.monitors.kubernetes.Constants.*;
import static com.appdynamics.monitors.kubernetes.Utilities.*;

public class EventSnapshotRunner extends SnapshotRunnerBase {

    public EventSnapshotRunner(){

    }

    public EventSnapshotRunner(TasksExecutionServiceProvider serviceProvider, Map<String, String> config, CountDownLatch countDownLatch){
        super(serviceProvider, config, countDownLatch);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        AssertUtils.assertNotNull(getConfiguration(), "The job configuration cannot be empty");
        generatePodSnapshot();
    }

    private void generatePodSnapshot(){
        logger.info("Proceeding to Event update...");

        Map<String, String> config = (Map<String, String>) getConfiguration().getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = Utilities.getUrl(config.get("eventsUrl") + "/events/publish/" + config.get(CONFIG_SCHEMA_NAME_EVENT));
            URL schemaUrl = Utilities.getUrl(config.get("eventsUrl") + "/events/schema/" + config.get(CONFIG_SCHEMA_NAME_EVENT));
            String requestBody = config.get(CONFIG_SCHEMA_DEF_EVENT);

            if(RestClient.doRequest(schemaUrl, accountName, apiKey, "", "GET") == null){
                logger.info("Schema Url {} does not exists", schemaUrl);
                RestClient.doRequest(schemaUrl, accountName, apiKey, requestBody, "POST");
                logger.info("Schema Url {} created", schemaUrl);
            }
            else {
                logger.info("Schema Url {} exists", schemaUrl);
            }

            try {
                ApiClient client = Utilities.initClient(config);

                Configuration.setDefaultApiClient(client);
                CoreV1Api api = new CoreV1Api();

                V1EventList eventList;
                eventList = api.listEventForAllNamespaces(null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
                String payload = createEventPayload(eventList, config).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push payload to Events API: {}", payload);
                }

                if(!payload.equals("[]")){
                    RestClient.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }

                //build and update metrics
                List<Metric> metricList = Utilities.getMetricsFromSummary(summaryMap, config);
                logger.info("About to send {} event metrics", metricList.size());
                UploadMetricsTask metricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadEventMetricsTask", metricsTask);

            } catch (IOException e) {
                logger.error("Failed to push events", e);
                e.printStackTrace();
            } catch (Exception e) {
                logger.error("Failed to push events", e);
                e.printStackTrace();
            }
        }
    }

    private ArrayNode createEventPayload(V1EventList eventList, Map<String, String> config) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        SummaryObj summary = initEventummaryObject(config, ALL);
        summaryMap.put(ALL, summary);
        int countAll = 0;
        int countErrors = 0;
        for (V1Event item : eventList.getItems()) {
            if (item.getLastTimestamp().isAfter(Globals.previousRunTimestamp) || Globals.previousRunTimestamp == null){
                if (!item.getMetadata().getSelfLink().equals(Globals.previousRunSelfLink)){

                    ObjectNode objectNode = mapper.createObjectNode();
                    String namespace = item.getMetadata().getNamespace();
                    String nodeName = item.getSource().getHost();

                    SummaryObj summaryNamespace = summaryMap.get(namespace);
                    if (summaryNamespace == null){
                        summaryNamespace = initEventummaryObject(config, namespace);
                        summaryMap.put(namespace, summaryNamespace);
                    }

                    String reason = item.getReason();
                    String message = item.getMessage();

                    objectNode = checkAddObject(objectNode, item.getFirstTimestamp(), "firstTimestamp");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getAnnotations(), "annotations");
                    objectNode = checkAddObject(objectNode, item.getLastTimestamp(), "lastTimestamp");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getCreationTimestamp(), "creationTimestamp");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getFinalizers(), "finalizers");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getInitializers(), "initializers");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getLabels(), "labels");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getOwnerReferences(), "ownerReferences");
                    objectNode = checkAddObject(objectNode, item.getInvolvedObject().getKind(), "object_kind");
                    objectNode = checkAddObject(objectNode, item.getInvolvedObject().getName(), "object_name");
                    objectNode = checkAddObject(objectNode, namespace, "object_namespace");
                    objectNode = checkAddObject(objectNode, item.getInvolvedObject().getResourceVersion(), "object_resourceVersion");
                    objectNode = checkAddObject(objectNode, item.getInvolvedObject().getUid(), "object_uid");
                    objectNode = checkAddObject(objectNode, item.getMessage(), "message");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getClusterName(), "clusterName");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getGenerateName(), "generateName");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getGeneration(), "generation");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getName(), "name");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getNamespace(), "namespace");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getResourceVersion(), "resourceVersion");
                    objectNode = checkAddObject(objectNode, item.getMetadata().getSelfLink(), "selfLink");
                    objectNode = checkAddObject(objectNode, item.getType(), "type");
                    objectNode = checkAddObject(objectNode, item.getReason(), "reason");
                    objectNode = checkAddObject(objectNode, item.getCount(), "count");
                    objectNode = checkAddObject(objectNode, item.getSource().getComponent(), "source_component");
                    objectNode = checkAddObject(objectNode, nodeName, "source_host");

                    //metrics
                    if (reason.equals("ScalingReplicaSet") && message.contains("Scaled down")){
                        Utilities.incrementField(summary, "ScaleDowns");
                        Utilities.incrementField(summaryNamespace, "ScaleDowns");
                    }

                    if (reason.equals("BackOff")){
                        Utilities.incrementField(summary, "CrashLoops");
                        Utilities.incrementField(summaryNamespace, "CrashLoops");
                    }

                    if (reason.equals("FailedCreate") && message.contains("failed quota")){
                        Utilities.incrementField(summary, "QuotaViolations");
                        Utilities.incrementField(summaryNamespace, "QuotaViolations");
                    }

                    if (reason.equals("FailedCreate") || reason.equals("FailedBinding") || reason.equals("BackOff")
                    || reason.equals("Unhealthy") || reason.equals("Failed") || reason.equals("SandboxChanged")){
                        Utilities.incrementField(summary, "PodIssues");
                        Utilities.incrementField(summaryNamespace, "PodIssues");
                        countErrors++;
                    }

                    if (reason.equals("Evicted") || reason.equals("FailedDaemonPod") || reason.equals("NodeHasDiskPressure") || reason.equals("NodeNotReady")
                    || reason.equals("EvictionThresholdMet") || reason.equals("ErrorReconciliationRetryTimeout") || reason.equals("ExceededGracePeriod")){
                        Utilities.incrementField(summary, "EvictionThreats");
                        Utilities.incrementField(summaryNamespace, "EvictionThreats");
                        countErrors++;
                    }

                    if (reason.equals("Failed") && message.contains("ImagePullBackOff")){
                        Utilities.incrementField(summary, "ImagePullErrors");
                        Utilities.incrementField(summaryNamespace, "ImagePullErrors");
                    }

                    if (reason.equals("Pulling")){
                        Utilities.incrementField(summary, "ImagePulls");
                        Utilities.incrementField(summaryNamespace, "ImagePulls");
                    }

                    if (reason.equals("FailedBinding")){
                        Utilities.incrementField(summary, "StorageIssues");
                        Utilities.incrementField(summaryNamespace, "StorageIssues");
                    }

                    if (reason.equals("Killing")){
                        Utilities.incrementField(summary, "PodKills");
                        Utilities.incrementField(summaryNamespace, "PodKills");
                    }

                    countAll++;

                    Utilities.incrementField(summary, "EventsCount");
                    Utilities.incrementField(summaryNamespace, "EventsCount");


                    Utilities.incrementField(summary, "EventsError", countErrors);
                    Utilities.incrementField(summaryNamespace, "EventsError", countErrors);


                    int countInfo = countAll - countErrors;
                    Utilities.incrementField(summary, "EventsInfo",  countInfo);
                    Utilities.incrementField(summaryNamespace, "EventsInfo", countInfo);

                    arrayNode.add(objectNode);
                    Globals.lastElementSelfLink = item.getMetadata().getSelfLink();
                }

                if(item.getLastTimestamp().isAfter(Globals.lastElementTimestamp) || Globals.lastElementTimestamp == null){
                    Globals.lastElementTimestamp = item.getLastTimestamp();
                }
            }
        }
        Globals.previousRunSelfLink = Globals.lastElementSelfLink;
        Globals.previousRunTimestamp = Globals.lastElementTimestamp;

        return arrayNode;
    }
    public  static SummaryObj initEventummaryObject(Map<String, String> config, String namespace){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("namespace", namespace);
        summary.put("EventsCount", 0);
        summary.put("EventsError", 0);
        summary.put("EventsInfo", 0);
        summary.put("ScaleDowns", 0);
        summary.put("CrashLoops", 0);
        summary.put("QuotaViolations", 0);
        summary.put("PodIssues", 0);
        summary.put("PodKills", 0);
        summary.put("EvictionThreats", 0);
        summary.put("ImagePullErrors", 0);
        summary.put("ImagePulls", 0);
        summary.put("StorageIssues", 0);


        ArrayList<AppDMetricObj> metricsList = initMetrics(config, namespace);
        String path = Utilities.getMetricsPath(config, namespace, ALL);

        return new SummaryObj(summary, metricsList, path);
    }

    public  static  ArrayList<AppDMetricObj> initMetrics(Map<String, String> config, String namespace){
        if (Utilities.ClusterName == null || Utilities.ClusterName.isEmpty()){
            return new ArrayList<AppDMetricObj>();
        }
        String rootPath = String.format("Application Infrastructure Performance|%s|Custom Metrics|Cluster Stats|", config.get(CONFIG_APP_TIER_NAME));
        String clusterName = Utilities.ClusterName;
        String parentSchema = config.get(CONFIG_SCHEMA_NAME_EVENT);
        ArrayList<AppDMetricObj> metricsList = new ArrayList<AppDMetricObj>();

        String filter = "";
        if(!namespace.equals(ALL)){
            filter = String.format("and object_namespace = \"%s\"", namespace);
        }


        metricsList.add(new AppDMetricObj("EventsCount", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("EventsError", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason IN (\"FailedCreate\",\"FailedBinding\",\"BackOff\",\"Unhealthy\",\"Failed\",\"SandboxChanged\",\"Evicted\",\"FailedDaemonPod\",\"NodeHasDiskPressure\",\"NodeNotReady\",\"EvictionThresholdMet\",\"ErrorReconciliationRetryTimeout\",\"ExceededGracePeriod\") and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("EventsInfo", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason NOT IN (\"FailedCreate\",\"FailedBinding\",\"BackOff\",\"Unhealthy\",\"Failed\",\"SandboxChanged\",\"Evicted\",\"FailedDaemonPod\",\"NodeHasDiskPressure\",\"NodeNotReady\",\"EvictionThresholdMet\",\"ErrorReconciliationRetryTimeout\",\"ExceededGracePeriod\") and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("ScaleDowns", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason = \"ScalingReplicaSet\" and message like \'Scaled down*\' and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("CrashLoops", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason = \"BackOff\" and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));


        metricsList.add(new AppDMetricObj("QuotaViolations", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason = \"FailedCreate\" and message like \'*failed quota*\' and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("QuotaViolations", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason IN ( \"FailedCreate\", \"FailedBinding\", \"BackOff\", \"Unhealthy\", \"Failed\", \"SandboxChanged\") and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("EvictionThreats", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason IN \"Evicted\",\"FailedDaemonPod\",\"NodeHasDiskPressure\",\"NodeNotReady\",\"EvictionThresholdMet\",\"ErrorReconciliationRetryTimeout\",\"ExceededGracePeriod\") and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("ImagePullErrors", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason = \"Failed\" AND message LIKE \"*ImagePullBackOff*\" and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("ImagePulls", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason = \"Pulling\" and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("StorageIssues", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason = \"FailedBinding\" and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("PodKills", parentSchema, CONFIG_SCHEMA_DEF_EVENT,
                String.format("select * from %s where reason = \"Killing\" and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath));



        return metricsList;
    }
}

