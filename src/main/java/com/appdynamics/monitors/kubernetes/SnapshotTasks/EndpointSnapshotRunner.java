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
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1EndpointAddress;
import io.kubernetes.client.models.V1EndpointSubset;
import io.kubernetes.client.models.V1Endpoints;
import io.kubernetes.client.models.V1EndpointsList;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_RECS_BATCH_SIZE;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_DEF_EP;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_EP;
import static com.appdynamics.monitors.kubernetes.Utilities.*;

public class EndpointSnapshotRunner extends SnapshotRunnerBase {

    public EndpointSnapshotRunner(){

    }

    public EndpointSnapshotRunner(TasksExecutionServiceProvider serviceProvider, Map<String, String> config, CountDownLatch countDownLatch){
        super(serviceProvider, config, countDownLatch);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        AssertUtils.assertNotNull(getConfiguration(), "The job configuration cannot be empty");
        generateEndPointSnapshot();
    }


    private void generateEndPointSnapshot(){
        logger.info("Proceeding to End Points update...");
        Map<String, String> config = (Map<String, String>) getConfiguration().getConfigYml();
        if (config != null) {
            String apiKey = Utilities.getEventsAPIKey(config);
            String accountName = Utilities.getGlobalAccountName(config);
            URL publishUrl = ensureSchema(config, apiKey, accountName,CONFIG_SCHEMA_NAME_EP, CONFIG_SCHEMA_DEF_EP);

            try {
                ApiClient client = Utilities.initClient(config);

                Configuration.setDefaultApiClient(client);
                CoreV1Api api = new CoreV1Api();

                V1EndpointsList epList;
                epList = api.listEndpointsForAllNamespaces(null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
                createEndpointPayload(epList, config, publishUrl, accountName, apiKey);


                List<Metric> metricList = getMetricsFromSummary(getSummaryMap(), config);
                logger.info("About to send {} endpoints metrics", metricList.size());
                UploadMetricsTask podMetricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadEPMetricsTask", podMetricsTask);
            } catch (IOException e) {
                countDownLatch.countDown();
                logger.error("Failed to push End Points data", e);
            } catch (Exception e) {
                countDownLatch.countDown();
                logger.error("Failed to push End Points data", e);
            }
        }
    }

     ArrayNode createEndpointPayload(V1EndpointsList epList, Map<String, String> config, URL publishUrl, String accountName, String apiKey) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        long batchSize = Long.parseLong(config.get(CONFIG_RECS_BATCH_SIZE));

        for (V1Endpoints ep : epList.getItems()) {
            ObjectNode objectNode = mapper.createObjectNode();
            objectNode = checkAddObject(objectNode, ep.getMetadata().getName(), "name");

            String namespace = ep.getMetadata().getNamespace();
            objectNode = checkAddObject(objectNode, namespace, "namespace");

            String clusterName = Utilities.ensureClusterName(config, ep.getMetadata().getClusterName());

            SummaryObj summary = getSummaryMap().get(ALL);
            if (summary == null) {
                summary = initEPSummaryObject(config, ALL);
                getSummaryMap().put(ALL, summary);
            }

            SummaryObj summaryNamespace = getSummaryMap().get(namespace);
            if (Utilities.shouldCollectMetricsForNamespace(getConfiguration(), namespace)) {
                if (summaryNamespace == null) {
                    summaryNamespace = initEPSummaryObject(config, namespace);
                    getSummaryMap().put(namespace, summaryNamespace);
                }
            }

            incrementField(summary, "Endpoints");
            incrementField(summaryNamespace, "Endpoints");



            objectNode = checkAddObject(objectNode, ep.getMetadata().getUid(), "object_uid");
            objectNode = checkAddObject(objectNode, clusterName, "clusterName");
            objectNode = checkAddObject(objectNode, ep.getMetadata().getCreationTimestamp(), "creationTimestamp");
            objectNode = checkAddObject(objectNode, ep.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            int ups = 0;
            int downs = 0;
            String downContext = "";
            if (ep.getSubsets() != null) {

                for (V1EndpointSubset subset : ep.getSubsets()) {
                    if (subset.getAddresses() != null) {
                        ups += subset.getAddresses().size();
                    }

                    if (subset.getNotReadyAddresses() != null) {
                        downs += subset.getNotReadyAddresses().size();
                        for (V1EndpointAddress address : subset.getNotReadyAddresses()) {
                            String obj = address.getTargetRef() != null ? address.getTargetRef().getName() : "";
                            downContext += String.format("%s, %s", obj, address.getIp());
                        }

                    }
                }
            }

            objectNode = checkAddInt(objectNode, ups, "ip_up");
            objectNode = checkAddInt(objectNode, downs, "ip_down");
            objectNode = checkAddObject(objectNode, downContext, "downContext");

            if (ups > 0){
                incrementField(summary, "HealthyEndpoints");
                incrementField(summaryNamespace, "HealthyEndpoints");
            }

            if (downs > 0){
                incrementField(summary, "UnhealthyEndpoints");
                incrementField(summaryNamespace, "UnhealthyEndpoints");
            }

            if(ups == 0 && downs == 0){
                incrementField(summary, "OrphanEndpoints");
                incrementField(summaryNamespace, "OrphanEndpoints");
            }

            arrayNode.add(objectNode);

            if (arrayNode.size() >= batchSize){
                logger.info("Sending batch of {} Endpoint records", arrayNode.size());
                String payload = arrayNode.toString();
                arrayNode = arrayNode.removeAll();
                if(!payload.equals("[]")){
                    UploadEventsTask uploadEventsTask = new UploadEventsTask(getTaskName(), config, publishUrl, accountName, apiKey, payload);
                    getConfiguration().getExecutorService().execute("UploadEndpointData", uploadEventsTask);
                }
            }
        }

         if (arrayNode.size() > 0){
             logger.info("Sending last batch of {} Endpoint records", arrayNode.size());
             String payload = arrayNode.toString();
             arrayNode = arrayNode.removeAll();
             if(!payload.equals("[]")){
                 UploadEventsTask uploadEventsTask = new UploadEventsTask(getTaskName(), config, publishUrl, accountName, apiKey, payload);
                 getConfiguration().getExecutorService().execute("UploadEndpointData", uploadEventsTask);
             }
         }

        return arrayNode;
    }

    protected SummaryObj initDefaultSummaryObject(Map<String, String> config){
        return initEPSummaryObject(config, ALL);
    }

    public  static SummaryObj initEPSummaryObject(Map<String, String> config, String namespace){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("namespace", namespace);
        summary.put("Endpoints", 0);
        summary.put("HealthyEndpoints", 0);
        summary.put("UnhealthyEndpoints", 0);
        summary.put("OrphanEndpoints", 0);


        ArrayList<AppDMetricObj> metricsList = initMetrics(config, namespace);

        String path = Utilities.getMetricsPath(config, namespace, ALL);

        return new SummaryObj(summary, metricsList, path);
    }

    public static ArrayList<AppDMetricObj> initMetrics(Map<String, String> config, String namespace) {
        if (Utilities.ClusterName == null || Utilities.ClusterName.isEmpty()) {
            return new ArrayList<AppDMetricObj>();
        }
        String clusterName = Utilities.ClusterName;
        String parentSchema = config.get(CONFIG_SCHEMA_NAME_EP);
        String rootPath = String.format("Application Infrastructure Performance|%s|Custom Metrics|Cluster Stats|", Utilities.getClusterTierName(config));
        ArrayList<AppDMetricObj> metricsList = new ArrayList<AppDMetricObj>();
        String filter = "";
        if(!namespace.equals(ALL)){
            filter = String.format("and namespace = \"%s\"", namespace);
        }


        if(namespace.equals(ALL)) {
            metricsList.add(new AppDMetricObj("Endpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                    String.format("select * from %s where clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));

            metricsList.add(new AppDMetricObj("HealthyEndpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                    String.format("select * from %s where ip_up > 0 and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));

            metricsList.add(new AppDMetricObj("UnhealthyEndpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                    String.format("select * from %s where ip_down > 0 and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));

            metricsList.add(new AppDMetricObj("OrphanEndpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                    String.format("select * from %s where ip_down = 0 and ip_up = 0 and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));
        }
        return metricsList;
    }

}
