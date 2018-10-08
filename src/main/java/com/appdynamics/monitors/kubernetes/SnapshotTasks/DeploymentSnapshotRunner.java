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
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.ExtensionsV1beta1DeploymentList;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_RECS_BATCH_SIZE;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_DEF_DEPLOY;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_DEPLOY;
import static com.appdynamics.monitors.kubernetes.Utilities.*;

public class DeploymentSnapshotRunner extends SnapshotRunnerBase {

    public DeploymentSnapshotRunner(){

    }


    public DeploymentSnapshotRunner(TasksExecutionServiceProvider serviceProvider, Map<String, String> config, CountDownLatch countDownLatch){
        super(serviceProvider, config, countDownLatch);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        AssertUtils.assertNotNull(getConfiguration(), "The job configuration cannot be empty");
        generateDeploySnapshot();
    }

    private void generateDeploySnapshot(){
        logger.info("Proceeding to Deployment update...");
        Map<String, String> config = (Map<String, String>) getConfiguration().getConfigYml();
        if (config != null) {
            String apiKey = Utilities.getEventsAPIKey(config);
            String accountName = Utilities.getGlobalAccountName(config);
            URL publishUrl = Utilities.ensureSchema(config, apiKey, accountName,CONFIG_SCHEMA_NAME_DEPLOY, CONFIG_SCHEMA_DEF_DEPLOY);

            try {
                ApiClient client = Utilities.initClient(config);

                Configuration.setDefaultApiClient(client);
                ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();

                ExtensionsV1beta1DeploymentList deployList =
                        api.listDeploymentForAllNamespaces(null, null, true, null, null, null, null, null, null);

                createDeployPayload(deployList, config, publishUrl, accountName, apiKey);

                //build and update metrics
                List<Metric> metricList = getMetricsFromSummary(getSummaryMap(), config);
                logger.info("About to send {} deployment metrics", metricList.size());
                UploadMetricsTask metricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadDeployMetricsTask", metricsTask);
            } catch (IOException e) {
                countDownLatch.countDown();
                logger.error("Failed to push Deployments data", e);
            } catch (Exception e) {
                countDownLatch.countDown();
                logger.error("Failed to push Deployments data", e);
            }
        }
    }


    private ArrayNode createDeployPayload(ExtensionsV1beta1DeploymentList deployList, Map<String, String> config, URL publishUrl, String accountName, String apiKey){
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        long batchSize = Long.parseLong(config.get(CONFIG_RECS_BATCH_SIZE));

        for(ExtensionsV1beta1Deployment deployItem : deployList.getItems()) {
            ObjectNode deployObject = mapper.createObjectNode();

            String namespace = deployItem.getMetadata().getNamespace();
            String clusterName = Utilities.ensureClusterName(config, deployItem.getMetadata().getClusterName());

            SummaryObj summary = getSummaryMap().get(ALL);
            if (summary == null) {
                summary = initDeploySummaryObject(config, ALL);
                getSummaryMap().put(ALL, summary);
            }

            SummaryObj summaryNamespace = getSummaryMap().get(namespace);
            if (summaryNamespace == null){
                summaryNamespace = initDeploySummaryObject(config, namespace);
                getSummaryMap().put(namespace, summaryNamespace);
            }

            incrementField(summary, "Deployments");
            incrementField(summaryNamespace, "Deployments");

            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getUid(), "object_uid");
            deployObject = checkAddObject(deployObject, clusterName, "clusterName");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getName(), "name");
            deployObject = checkAddObject(deployObject, namespace, "namespace");

            if (deployItem.getMetadata().getLabels() != null) {
                String labels = "";
                Iterator it = deployItem.getMetadata().getLabels().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry)it.next();
                    labels += String.format("%s:%s;", pair.getKey(), pair.getValue());
                    it.remove();
                }
                deployObject = checkAddObject(deployObject, labels, "labels");
            }

            if (deployItem.getMetadata().getAnnotations() != null){
                String annotations = "";
                Iterator it = deployItem.getMetadata().getLabels().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry)it.next();
                    annotations += String.format("%s:%s;", pair.getKey(), pair.getValue());
                    it.remove();
                }
                deployObject = checkAddObject(deployObject, annotations, "annotations");
            }


            deployObject = checkAddInt(deployObject, deployItem.getSpec().getMinReadySeconds(), "minReadySecs");
            deployObject = checkAddInt(deployObject, deployItem.getSpec().getProgressDeadlineSeconds(), "progressDeadlineSecs");

            int replicas = deployItem.getSpec().getReplicas();
            deployObject = checkAddInt(deployObject, deployItem.getSpec().getReplicas(), "replicas");

//            deployObject = checkAddObject(deployObject, deployItem.getSpec().getSelector().getMatchLabels().toString(), "labels");

            incrementField(summary, "DeployReplicas", replicas);
            incrementField(summaryNamespace, "DeployReplicas", replicas);


            deployObject = checkAddInt(deployObject, deployItem.getSpec().getRevisionHistoryLimit(), "revisionHistoryLimits");

            deployObject = checkAddObject(deployObject, deployItem.getSpec().getStrategy().getType(), "strategy");

            if (deployItem.getSpec().getStrategy().getRollingUpdate() != null){
                deployObject = checkAddObject(deployObject, deployItem.getSpec().getStrategy().getRollingUpdate().getMaxSurge(), "maxSurge");
                deployObject = checkAddObject(deployObject, deployItem.getSpec().getStrategy().getRollingUpdate().getMaxUnavailable(), "maxUnavailable");
            }


            deployObject = checkAddInt(deployObject, deployItem.getStatus().getAvailableReplicas(), "replicasAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getUnavailableReplicas(), "replicasUnAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getUpdatedReplicas(), "replicasUpdated");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getCollisionCount(), "collisionCount");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getReadyReplicas(), "replicasReady");


            if (deployItem.getStatus().getUnavailableReplicas() != null) {
                incrementField(summary, "DeployReplicasUnAvailable", deployItem.getStatus().getUnavailableReplicas());
                incrementField(summaryNamespace, "DeployReplicasUnAvailable", deployItem.getStatus().getUnavailableReplicas());
            }

            if (deployItem.getStatus().getCollisionCount() != null) {
                incrementField(summary, "DeployCollisionCount", deployItem.getStatus().getCollisionCount());
                incrementField(summaryNamespace, "DeployCollisionCount", deployItem.getStatus().getCollisionCount());
            }


            arrayNode.add(deployObject);
            if (arrayNode.size() >= batchSize){
                logger.info("Sending batch of {} Deploy records", arrayNode.size());
                String payload = arrayNode.toString();
                arrayNode = arrayNode.removeAll();
                if(!payload.equals("[]")){
                    UploadEventsTask uploadEventsTask = new UploadEventsTask(getTaskName(), publishUrl, accountName, apiKey, payload);
                    getConfiguration().getExecutorService().execute("UploadDeployData", uploadEventsTask);
                }
            }
        }

        if (arrayNode.size() > 0){
            logger.info("Sending last batch of {} Deploy records", arrayNode.size());
            String payload = arrayNode.toString();
            arrayNode = arrayNode.removeAll();
            if(!payload.equals("[]")){
                UploadEventsTask uploadEventsTask = new UploadEventsTask(getTaskName(), publishUrl, accountName, apiKey, payload);
                getConfiguration().getExecutorService().execute("UploadDeployData", uploadEventsTask);
            }
        }


        return arrayNode;
    }

    protected SummaryObj initDefaultSummaryObject(Map<String, String> config){
        return initDeploySummaryObject(config, ALL);
    }

    public  static SummaryObj initDeploySummaryObject(Map<String, String> config, String namespace){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("namespace", namespace);
        summary.put("Deployments", 0);
        summary.put("DeployReplicas", 0);
        summary.put("DeployReplicasUnAvailable", 0);
        summary.put("DeployCollisionCount", 0);



        ArrayList<AppDMetricObj> metricsList = initMetrics(config, namespace);

        String path = Utilities.getMetricsPath(config, namespace, ALL);

        return new SummaryObj(summary, metricsList, path);
    }

    public static ArrayList<AppDMetricObj> initMetrics(Map<String, String> config, String namespace) {
        if (Utilities.ClusterName == null || Utilities.ClusterName.isEmpty()) {
            return new ArrayList<AppDMetricObj>();
        }
        String clusterName = Utilities.ClusterName;
        String parentSchema = config.get(CONFIG_SCHEMA_NAME_DEPLOY);
        String rootPath = String.format("Application Infrastructure Performance|%s|Custom Metrics|Cluster Stats|", Utilities.getClusterTierName(config));
        ArrayList<AppDMetricObj> metricsList = new ArrayList<AppDMetricObj>();
        String filter = "";
        if(!namespace.equals(ALL)){
            filter = String.format("and namespace = \"%s\"", namespace);
        }

        if(namespace.equals(ALL)) {
            metricsList.add(new AppDMetricObj("Deployments", parentSchema, CONFIG_SCHEMA_DEF_DEPLOY,
                    String.format("select * from %s where clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));

            metricsList.add(new AppDMetricObj("DeployReplicas", parentSchema, CONFIG_SCHEMA_DEF_DEPLOY,
                    String.format("select * from %s where replicas > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));

            metricsList.add(new AppDMetricObj("DeployReplicasUnAvailable", parentSchema, CONFIG_SCHEMA_DEF_DEPLOY,
                    String.format("select * from %s where replicasUnAvailable > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));

            metricsList.add(new AppDMetricObj("DeployCollisionCount", parentSchema, CONFIG_SCHEMA_DEF_DEPLOY,
                    String.format("select * from %s where collisionCount > 0 and clusterName = \"%s\" %s", parentSchema, clusterName, filter), rootPath, namespace, ALL));
        }
        return metricsList;
    }

}
