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
import io.kubernetes.client.models.V1beta1DaemonSet;
import io.kubernetes.client.models.V1beta1DaemonSetList;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.appdynamics.monitors.kubernetes.Constants.*;
import static com.appdynamics.monitors.kubernetes.Utilities.*;

public class DaemonSnapshotRunner extends SnapshotRunnerBase{

    public DaemonSnapshotRunner(TasksExecutionServiceProvider serviceProvider, Map<String, String> config, CountDownLatch countDownLatch){
        super(serviceProvider, config, countDownLatch);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        AssertUtils.assertNotNull(getConfiguration(), "The job configuration cannot be empty");
        generateDaemonsetSnapshot();
    }

    private void generateDaemonsetSnapshot(){
        logger.info("Proceeding to Daemonsets update...");
        Map<String, String> config = (Map<String, String>) getConfiguration().getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName,CONFIG_SCHEMA_NAME_DAEMON, CONFIG_SCHEMA_DEF_DAEMON);

            try {
                ApiClient client = Utilities.initClient(config);
                Configuration.setDefaultApiClient(client);
                ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();

                V1beta1DaemonSetList dsList =
                        api.listDaemonSetForAllNamespaces(null, null, true, null, null, null, null, null, null);

                String payload = createDaemonsetPayload(dsList, config).toString();

                logger.debug("About to push Daemonsets to Events API: {}", payload);

                if(!payload.equals("[]")){
                    RestClient.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }

                List<Metric> metricList = Utilities.getMetricsFromSummary(summaryMap, config);
                logger.info("About to send {} Daemonset metrics", metricList.size());
                UploadMetricsTask podMetricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadDaemonMetricsTask", podMetricsTask);

            } catch (IOException e) {
                logger.error("Failed to push Daemonsets data", e);
                e.printStackTrace();
            } catch (Exception e) {
                logger.error("Failed to push Daemonsets data", e);
                e.printStackTrace();
            }
        }
    }

    private ArrayNode createDaemonsetPayload(V1beta1DaemonSetList dsList, Map<String, String> config){
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        SummaryObj summary = initDaemonSummaryObject(config, ALL);
        summaryMap.put(ALL, summary);
        for(V1beta1DaemonSet deployItem : dsList.getItems()) {
            ObjectNode deployObject = mapper.createObjectNode();

            String namespace = deployItem.getMetadata().getNamespace();

            SummaryObj summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null){
                summaryNamespace = initDaemonSummaryObject(config, namespace);
                summaryMap.put(namespace, summaryNamespace);
            }

            incrementField(summary, "DaemonSets");
            incrementField(summaryNamespace, "DaemonSets");

            String clusterName = Utilities.ensureClusterName(config, deployItem.getMetadata().getClusterName());

            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getUid(), "object_uid");
            deployObject = checkAddObject(deployObject, clusterName, "clusterName");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getName(), "name");
            deployObject = checkAddObject(deployObject, namespace, "namespace");

            deployObject = checkAddInt(deployObject, deployItem.getSpec().getMinReadySeconds(), "minReadySecs");


            deployObject = checkAddInt(deployObject, deployItem.getSpec().getRevisionHistoryLimit(), "revisionHistoryLimits");




            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberAvailable(), "replicasAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberUnavailable(), "replicasUnAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getCollisionCount(), "collisionCount");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberReady(), "replicasReady");

            deployObject = checkAddInt(deployObject, deployItem.getStatus().getCurrentNumberScheduled(), "numberScheduled");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getDesiredNumberScheduled(), "desiredNumber");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberMisscheduled(), "missScheduled");

            deployObject = checkAddInt(deployObject, deployItem.getStatus().getUpdatedNumberScheduled(), "updatedNumberScheduled");

            if (deployItem.getStatus().getNumberAvailable() != null) {
                incrementField(summary, "DaemonReplicasAvailable", deployItem.getStatus().getNumberAvailable());
                incrementField(summaryNamespace, "DaemonReplicasAvailable", deployItem.getStatus().getNumberAvailable());
            }

            if (deployItem.getStatus().getNumberUnavailable() != null) {
                incrementField(summary, "DaemonReplicasUnAvailable", deployItem.getStatus().getNumberUnavailable());
                incrementField(summaryNamespace, "DaemonReplicasUnAvailable", deployItem.getStatus().getNumberUnavailable());
            }

            if (deployItem.getStatus().getCollisionCount() != null) {
                incrementField(summary, "DaemonCollisionCount", deployItem.getStatus().getCollisionCount());
                incrementField(summaryNamespace, "DaemonCollisionCount", deployItem.getStatus().getCollisionCount());
            }

            if (deployItem.getStatus().getNumberReady() != null) {
                incrementField(summary, "DaemonReplicasReady", deployItem.getStatus().getNumberReady());
                incrementField(summaryNamespace, "DaemonReplicasReady", deployItem.getStatus().getNumberReady());
            }

            if (deployItem.getStatus().getCurrentNumberScheduled() != null) {
                incrementField(summary, "DaemonNumberScheduled", deployItem.getStatus().getCurrentNumberScheduled());
                incrementField(summaryNamespace, "DaemonNumberScheduled", deployItem.getStatus().getCurrentNumberScheduled());
            }


            if (deployItem.getStatus().getNumberMisscheduled() != null) {
                incrementField(summary, "DaemonMissScheduled", deployItem.getStatus().getNumberMisscheduled());
                incrementField(summaryNamespace, "DaemonMissScheduled", deployItem.getStatus().getNumberMisscheduled());
            }

            arrayNode.add(deployObject);
        }


        return arrayNode;
    }

    public  static SummaryObj initDaemonSummaryObject(Map<String, String> config, String namespace){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("namespace", namespace);

        summary.put("DaemonSets", 0);
        summary.put("DaemonReplicasAvailable", 0);
        summary.put("DaemonReplicasUnAvailable", 0);
        summary.put("DaemonMissScheduled", 0);
        summary.put("DaemonCollisionCount", 0);


        ArrayList<AppDMetricObj> metricsList = initMetrics(config, namespace);

        String path = Utilities.getMetricsPath(config, namespace, ALL);

        return new SummaryObj(summary, metricsList, path);
    }

    public static ArrayList<AppDMetricObj> initMetrics(Map<String, String> config, String namespace) {
        if (Utilities.ClusterName == null || Utilities.ClusterName.isEmpty()) {
            return new ArrayList<AppDMetricObj>();
        }
        String clusterName = Utilities.ClusterName;
        String parentSchema = config.get(CONFIG_SCHEMA_NAME_DAEMON);
        String rootPath = String.format("Application Infrastructure Performance|%s|Custom Metrics|Cluster Stats|", config.get(CONFIG_APP_TIER_NAME));
        ArrayList<AppDMetricObj> metricsList = new ArrayList<AppDMetricObj>();

        String filter = "";
        if(!namespace.equals(ALL)){
            filter = String.format("and object_namespace = \"%s\"", namespace);
        }

        metricsList.add(new AppDMetricObj("DaemonSets", parentSchema, CONFIG_SCHEMA_DEF_DAEMON,
                String.format("select * from %s where clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));

        metricsList.add(new AppDMetricObj("DaemonReplicasAvailable", parentSchema, CONFIG_SCHEMA_DEF_DAEMON,
                String.format("select * from %s where replicasAvailable > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));

        metricsList.add(new AppDMetricObj("DaemonReplicasUnAvailable", parentSchema, CONFIG_SCHEMA_DEF_DAEMON,
                String.format("select * from %s where replicasUnAvailable > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));

        metricsList.add(new AppDMetricObj("DaemonMissScheduled", parentSchema, CONFIG_SCHEMA_DEF_DAEMON,
                String.format("select * from %s where missScheduled > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));

        metricsList.add(new AppDMetricObj("DaemonCollisionCount", parentSchema, CONFIG_SCHEMA_DEF_DAEMON,
                String.format("select * from %s where collisionCount > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));


        return metricsList;
    }
}
