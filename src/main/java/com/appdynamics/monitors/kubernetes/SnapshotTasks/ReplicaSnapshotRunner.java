package com.appdynamics.monitors.kubernetes.SnapshotTasks;

import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.AssertUtils;
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
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.V1beta1ReplicaSet;
import io.kubernetes.client.models.V1beta1ReplicaSetList;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_APP_TIER_NAME;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_DEF_RS;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_RS;
import static com.appdynamics.monitors.kubernetes.Utilities.*;

public class ReplicaSnapshotRunner extends SnapshotRunnerBase {
    public ReplicaSnapshotRunner(TasksExecutionServiceProvider serviceProvider, Map<String, String> config, CountDownLatch countDownLatch){
        super(serviceProvider, config, countDownLatch);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        AssertUtils.assertNotNull(getConfiguration(), "The job configuration cannot be empty");
        generateReplicasetSnapshot();
    }

    private void generateReplicasetSnapshot(){
        logger.info("Proceeding to ReplicaSet update...");
        Map<String, String> config = (Map<String, String>) getConfiguration().getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName, CONFIG_SCHEMA_NAME_RS, CONFIG_SCHEMA_DEF_RS);

            try {
                ApiClient client = Utilities.initClient(config);

                Configuration.setDefaultApiClient(client);
                ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();

                V1beta1ReplicaSetList rsList =
                        api.listReplicaSetForAllNamespaces(null, null, true, null, null, null, null, null, null);

                String payload = createReplicasetPayload(rsList, config).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push ReplicaSet to Events API: {}", payload);
                }
                if(!payload.equals("[]")){
                    RestClient.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }
                //build and update metrics
                List<Metric> metricList = Utilities.getMetricsFromSummary(summaryMap, config);
                logger.info("About to send {} replica set metrics", metricList.size());
                UploadMetricsTask metricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadRSMetricsTask", metricsTask);

            } catch (IOException e) {
                logger.error("Failed to push ReplicaSet data", e);
                e.printStackTrace();
            } catch (Exception e) {
                logger.error("Failed to push ReplicaSet data", e);
                e.printStackTrace();
            }
        }
    }

     ArrayNode createReplicasetPayload(V1beta1ReplicaSetList rsList, Map<String, String> config) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        SummaryObj summary = initRSSummaryObject(config, ALL);
        summaryMap.put(ALL, summary);

        for (V1beta1ReplicaSet deployItem : rsList.getItems()) {
            ObjectNode deployObject = mapper.createObjectNode();

            String namespace = deployItem.getMetadata().getNamespace();

            SummaryObj summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null) {
                summaryNamespace = initRSSummaryObject(config, namespace);
                summaryMap.put(namespace, summaryNamespace);
            }

            incrementField(summary, "ReplicaSets");
            incrementField(summaryNamespace, "ReplicaSets");

            String clusterName = Utilities.ensureClusterName(config, deployItem.getMetadata().getClusterName());

            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getUid(), "object_uid");
            deployObject = checkAddObject(deployObject, clusterName, "clusterName");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getName(), "name");
            deployObject = checkAddObject(deployObject, namespace, "namespace");

            deployObject = checkAddInt(deployObject, deployItem.getSpec().getMinReadySeconds(), "minReadySecs");

            int replicas = deployItem.getSpec().getReplicas();
            deployObject = checkAddInt(deployObject, deployItem.getSpec().getReplicas(), "replicas");

            incrementField(summary, "RsReplicas", replicas);
            incrementField(summaryNamespace, "RsReplicas", replicas);


            deployObject = checkAddInt(deployObject, deployItem.getStatus().getAvailableReplicas(), "rsReplicasAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getFullyLabeledReplicas(), "replicasLabeled");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getReadyReplicas(), "replicasReady");

            if (deployItem.getStatus().getAvailableReplicas() != null) {
                incrementField(summary, "RsReplicasAvailable", deployItem.getStatus().getAvailableReplicas());
                incrementField(summaryNamespace, "RsReplicasAvailable", deployItem.getStatus().getFullyLabeledReplicas());
            }


            arrayNode.add(deployObject);
        }

        return arrayNode;
    }

    public  static SummaryObj initRSSummaryObject(Map<String, String> config, String namespace){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("namespace", namespace);
        summary.put("ReplicaSets", 0);
        summary.put("RsReplicas", 0);
        summary.put("RsReplicasAvailable", 0);



        ArrayList<AppDMetricObj> metricsList = initMetrics(config, namespace);

        String path = Utilities.getMetricsPath(config, namespace, ALL);

        return new SummaryObj(summary, metricsList, path);
    }

    public static ArrayList<AppDMetricObj> initMetrics(Map<String, String> config, String namespace) {
        if (Utilities.ClusterName == null || Utilities.ClusterName.isEmpty()) {
            return new ArrayList<AppDMetricObj>();
        }
        String clusterName = Utilities.ClusterName;
        String parentSchema = config.get(CONFIG_SCHEMA_NAME_RS);
        String rootPath = String.format("Application Infrastructure Performance|%s|Custom Metrics|Cluster Stats|", config.get(CONFIG_APP_TIER_NAME));
        ArrayList<AppDMetricObj> metricsList = new ArrayList<AppDMetricObj>();

        String filter = "";
        if(!namespace.equals(ALL)){
            filter = String.format("and object_namespace = \"%s\"", namespace);
        }

        metricsList.add(new AppDMetricObj("ReplicaSets", parentSchema, CONFIG_SCHEMA_DEF_RS,
                String.format("select * from %s where clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("RsReplicas", parentSchema, CONFIG_SCHEMA_DEF_RS,
                String.format("select * from %s where replicas > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath));

        metricsList.add(new AppDMetricObj("RsReplicasAvailable", parentSchema, CONFIG_SCHEMA_DEF_RS,
                String.format("select * from %s where rsReplicasAvailable > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath));

        return metricsList;
    }
}
