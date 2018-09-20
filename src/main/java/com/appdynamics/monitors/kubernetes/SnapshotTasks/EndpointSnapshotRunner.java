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
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1EndpointAddress;
import io.kubernetes.client.models.V1EndpointSubset;
import io.kubernetes.client.models.V1Endpoints;
import io.kubernetes.client.models.V1EndpointsList;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_APP_TIER_NAME;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_DEF_EP;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_EP;
import static com.appdynamics.monitors.kubernetes.Utilities.*;

public class EndpointSnapshotRunner extends SnapshotRunnerBase {
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
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
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
                String payload = createEndpointPayload(epList, config).toString();

                logger.debug("About to push Endpoints to Events API: {}", payload);


                if(!payload.equals("[]")) {
                    RestClient.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }

                List<Metric> metricList = Utilities.getMetricsFromSummary(summaryMap, config);
                logger.info("About to send {} endpoints metrics", metricList.size());
                UploadMetricsTask podMetricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadEPMetricsTask", podMetricsTask);
            } catch (IOException e) {
                logger.error("Failed to push End Points data", e);
                e.printStackTrace();
            } catch (Exception e) {
                logger.error("Failed to push End Points data", e);
                e.printStackTrace();
            }
        }
    }

     ArrayNode createEndpointPayload(V1EndpointsList epList, Map<String, String> config) {
        Long loadTime = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
         SummaryObj summary = initEPSummaryObject(config, ALL);
         summaryMap.put(ALL, summary);

        for (V1Endpoints ep : epList.getItems()) {
            ObjectNode objectNode = mapper.createObjectNode();
            objectNode = checkAddObject(objectNode, ep.getMetadata().getName(), "name");

            String namespace = ep.getMetadata().getNamespace();
            objectNode = checkAddObject(objectNode, namespace, "namespace");

            SummaryObj summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null){
                summaryNamespace = initEPSummaryObject(config, namespace);
                summaryMap.put(namespace, summaryNamespace);
            }

            incrementField(summary, "Endpoints");
            incrementField(summaryNamespace, "Endpoints");

            String clusterName = Utilities.ensureClusterName(config, ep.getMetadata().getClusterName());

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
        }

        return arrayNode;
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
        String rootPath = String.format("Application Infrastructure Performance|%s|Custom Metrics|Cluster Stats|", config.get(CONFIG_APP_TIER_NAME));
        ArrayList<AppDMetricObj> metricsList = new ArrayList<AppDMetricObj>();
        String filter = "";
        if(!namespace.equals(ALL)){
            filter = String.format("and object_namespace = \"%s\"", namespace);
        }


        metricsList.add(new AppDMetricObj("Endpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                String.format("select * from %s where clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));

        metricsList.add(new AppDMetricObj("HealthyEndpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                String.format("select * from %s where ip_up > 0 and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));

        metricsList.add(new AppDMetricObj("UnhealthyEndpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                String.format("select * from %s where ip_down > 0 and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));

        metricsList.add(new AppDMetricObj("OrphanEndpoints", parentSchema, CONFIG_SCHEMA_DEF_EP,
                String.format("select * from %s where ip_down = 0 and ip_up = 0 and clusterName = \"%s\" %s ORDER BY creationTimestamp DESC", parentSchema, clusterName, filter), rootPath, namespace, ALL));


        return metricsList;
    }

}
