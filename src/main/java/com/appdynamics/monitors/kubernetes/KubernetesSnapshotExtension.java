package com.appdynamics.monitors.kubernetes;

import com.appdynamics.extensions.StringUtils;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.util.MetricWriteHelper;
import com.appdynamics.extensions.util.MetricWriteHelperFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.Config;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class KubernetesSnapshotExtension extends AManagedMonitor {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesSnapshotExtension.class);
    private MonitorConfiguration configuration;

    public KubernetesSnapshotExtension() { logger.info(String.format("Using Kubernetes Snapshot Extension Version [%s]", getImplementationVersion())); }

    private void initialize(Map<String, String> argsMap) {
        MetricWriteHelper metricWriteHelper = MetricWriteHelperFactory.create(this);
        MonitorConfiguration conf = new MonitorConfiguration("Custom Metrics|K8S",
                new TaskRunnable(), metricWriteHelper);
        final String configFilePath = argsMap.get("config-file");
        conf.setConfigYml(configFilePath);
        conf.setMetricWriter(MetricWriteHelperFactory.create(this));
        conf.checkIfInitialized(MonitorConfiguration.ConfItem.CONFIG_YML,
                MonitorConfiguration.ConfItem.EXECUTOR_SERVICE,
                MonitorConfiguration.ConfItem.METRIC_PREFIX,
                MonitorConfiguration.ConfItem.METRIC_WRITE_HELPER);
        this.configuration = conf;
    }

    @Override
    public TaskOutput execute(Map<String, String> map, TaskExecutionContext taskExecutionContext) throws TaskExecutionException {
        try{
            if(map != null){
                if (logger.isDebugEnabled()) {logger.debug("The raw arguments are {}", map);}
                initialize(map);
                configuration.executeTask();
                return new TaskOutput("Finished executing Kubernetes Snapshot Extension");
            }
        }
        catch(Exception e) {
            logger.error("Failed to execute the Kubernetes Snapshot Extension task", e);
        }
        throw new TaskExecutionException("Kubernetes Snapshot Extension task completed with failures.");
    }

    private class TaskRunnable implements Runnable {
        @SuppressWarnings("unchecked")
        public void run() {
            Utilities.clearSummary();

            Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
            if (config != null) {
                //get the POD data
                if (shouldRunJob(config, "includePodSnapshot")) {
                    generatePodSnapshot();
                }

                //now let's get Endpoints
                if (shouldRunJob(config, "includeEndpointSnapshot")) {
                    generateEndPointSnapshot();
                }

                //Deployments
                if (shouldRunJob(config, "includeDeploySnapshot")) {
                    generateDeploySnapshot();
                }

                //Daemonsets
                if (shouldRunJob(config, "includeDaemonSnapshot")) {
                    generateDaemonsetSnapshot();
                }

                //Replicasets
                if (shouldRunJob(config, "includeRSSnapshot")) {
                    generateReplicasetSnapshot();
                }

                //Pod Security
                if (shouldRunJob(config, "includePodSecuritySnapshot")){
                    generatePodSecuritySnapshot();
                }


                //Summary
                if (shouldRunJob(config, "includeSummary")) {
                    sendSummaryData();
                }
            }
        }
    }

    private static boolean shouldRunJob(Map<String, String> config, String jobtype){
        return config.get(jobtype) != null && config.get(jobtype).equals("true");
    }

    private static boolean shouldLogPayloads(Map<String, String> config){
        return config.get("logPayloads") != null && config.get("logPayloads").equals("true");
    }

    private URL ensureSchema(Map<String, String> config, String apiKey, String accountName, String schemaName, String schemaDefinition){
        URL publishUrl = Utilities.getUrl(config.get("eventsUrl") + "/events/publish/" + config.get(schemaName));
        URL schemaUrl = Utilities.getUrl(config.get("eventsUrl") + "/events/schema/" + config.get(schemaName));
        String requestBody = config.get(schemaDefinition);
//        ObjectNode existingSchema = null;
//        try {
//            existingSchema = (ObjectNode) new ObjectMapper().readTree(requestBody);
//        }
//        catch (IOException ioEX){
//            logger.error("Unable to determine the latest Pod schema", ioEX);
//        }

        JsonNode serverSchema = EventsRestOperation.doRequest(schemaUrl, accountName, apiKey, "", "GET");
        if(serverSchema == null){
            logger.info("Schema Url {} does not exists", schemaUrl);
            EventsRestOperation.doRequest(schemaUrl, accountName, apiKey, requestBody, "POST");
            logger.info("Schema Url {} created", schemaUrl);
        }
        else {
            logger.info("Schema exists");
//            if (existingSchema != null) {
//                logger.info("Existing schema is not empty");
//                ArrayNode updated = Utilities.checkSchemaForUpdates(serverSchema, existingSchema);
//                if (updated != null) {
//                    //update schema changes
//                    logger.info("Schema changed, updating", schemaUrl);
//                    if (shouldLogPayloads(config)) {
//                        logger.info("New schema fields: {}", updated.toString());
//                    }
//                    EventsRestOperation.doRequest(schemaUrl, accountName, apiKey, updated.toString(), "PATCH");
//                }
//                else {
//                    logger.info("Nothing to update");
//                }
//            }
        }
        return publishUrl;
    }

    private void generatePodSnapshot(){
        logger.info("Proceeding to POD update...");
        Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName,"podsSchemaName", "podsSchemaDefinition");

            ApiClient client;
            try {
                client = Config.fromConfig(config.get("kubeClientConfig"));
                Configuration.setDefaultApiClient(client);
                CoreV1Api api = new CoreV1Api();

                V1PodList podList;
                podList = api.listPodForAllNamespaces(null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
                String payload = Utilities.createPodPayload(podList).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push PODs to Events API: {}", payload);
                }
                if(!payload.equals("[]")){
                    EventsRestOperation.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }
            } catch (IOException e) {
                logger.error("Failed to push POD data", e);
                e.printStackTrace();
            } catch (ApiException e) {
                logger.error("Failed to push POD data", e);
                e.printStackTrace();
            }
        }
    }

    private void generateEndPointSnapshot(){
        logger.info("Proceeding to End Points update...");
        Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName,"endpointSchemaName", "endpointSchemaDefinition");

            ApiClient client;
            try {
                client = Config.fromConfig(config.get("kubeClientConfig"));
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
                String payload = Utilities.createEndpointSnapshot(epList).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push PODs to Events API: {}", payload);
                }
                if(!payload.equals("[]")) {
                    EventsRestOperation.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }
            } catch (IOException e) {
                logger.error("Failed to push POD data", e);
                e.printStackTrace();
            } catch (ApiException e) {
                logger.error("Failed to push POD data", e);
                e.printStackTrace();
            }
        }
    }

    private void generateDeploySnapshot(){
        logger.info("Proceeding to Deployment update...");
        Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName,"deploySchemaName", "deploySchemaDefinition");

            ApiClient client;
            try {
                client = Config.fromConfig(config.get("kubeClientConfig"));
                Configuration.setDefaultApiClient(client);
                ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();

                ExtensionsV1beta1DeploymentList deployList =
                        api.listDeploymentForAllNamespaces(null, null, true, null, null, null, null, null, null);

                String payload = Utilities.createDeployPayload(deployList).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push Deployments to Events API: {}", payload);
                }
                if(!payload.equals("[]")){
                    EventsRestOperation.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }
            } catch (IOException e) {
                logger.error("Failed to push Deployments data", e);
                e.printStackTrace();
            } catch (ApiException e) {
                logger.error("Failed to push Deployments data", e);
                e.printStackTrace();
            }
        }
    }

    private void generateDaemonsetSnapshot(){
        logger.info("Proceeding to Daemonsets update...");
        Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName,"daemonSchemaName", "daemonSchemaDefinition");

            ApiClient client;
            try {
                client = Config.fromConfig(config.get("kubeClientConfig"));
                Configuration.setDefaultApiClient(client);
                ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();

                V1beta1DaemonSetList dsList =
                        api.listDaemonSetForAllNamespaces(null, null, true, null, null, null, null, null, null);

                String payload = Utilities.createDaemonsetPayload(dsList).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push Daemonsets to Events API: {}", payload);
                }
                if(!payload.equals("[]")){
                    EventsRestOperation.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }
            } catch (IOException e) {
                logger.error("Failed to push Daemonsets data", e);
                e.printStackTrace();
            } catch (ApiException e) {
                logger.error("Failed to push Daemonsets data", e);
                e.printStackTrace();
            }
        }
    }

    private void generateReplicasetSnapshot(){
        logger.info("Proceeding to Replicaset update...");
        Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName, "rsSchemaName", "rsSchemaDefinition");

            ApiClient client;
            try {
                client = Config.fromConfig(config.get("kubeClientConfig"));
                Configuration.setDefaultApiClient(client);
                ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();

                V1beta1ReplicaSetList rsList =
                        api.listReplicaSetForAllNamespaces(null, null, true, null, null, null, null, null, null);

                String payload = Utilities.createReplicasetPayload(rsList).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push Replicaset to Events API: {}", payload);
                }
                if(!payload.equals("[]")){
                    EventsRestOperation.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }
            } catch (IOException e) {
                logger.error("Failed to push Replicaset data", e);
                e.printStackTrace();
            } catch (ApiException e) {
                logger.error("Failed to push Replicaset data", e);
                e.printStackTrace();
            }
        }
    }


    private void generatePodSecuritySnapshot(){
        logger.info("Proceeding to Pod Security update...");
        Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName, "podsSecuritySchemaName", "podSecuritySchemaDefinition");

            ApiClient client;
            try {
                client = Config.fromConfig(config.get("kubeClientConfig"));
                Configuration.setDefaultApiClient(client);
                ExtensionsV1beta1Api api = new ExtensionsV1beta1Api();

                V1beta1PodSecurityPolicyList secList =
                        api.listPodSecurityPolicy(null,
                                null,
                                null,
                                true,
                                null,
                                null,
                                null,
                                null,
                                null);

                if (secList.getItems() != null) {
                    logger.info("Policy list size = {}", secList.getItems().size());
                }
                String payload = Utilities.createPodSecurityPayload(secList).toString();
                if (shouldLogPayloads(config)) {
                    logger.info("About to push Pod Security to Events API: {}", payload);
                }
                if(!payload.equals("[]")){
                    EventsRestOperation.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }
            } catch (IOException e) {
                logger.error("Failed to push Pod Security data", e);
                e.printStackTrace();
            } catch (ApiException e) {
                logger.error("Failed to push Pod Security data", e);
                e.printStackTrace();
            }
        }
    }



    private void sendSummaryData(){
        logger.info("Proceeding to summary...");
        Map<String, String> config = (Map<String, String>) configuration.getConfigYml();
        if (config != null) {
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = ensureSchema(config, apiKey, accountName, "summarySchemaName", "summarySchemaDefinition");

            String payload = Utilities.getSummaryData().toString();

            if(!payload.equals("[]")){
                if(shouldLogPayloads(config)) {
                    logger.info("About to push summary data to Events API: {}", payload);
                }
                EventsRestOperation.doRequest(publishUrl, accountName, apiKey, payload, "POST");
            }
            logger.info("Summary data sent successfully");
        }
    }


    private static String getImplementationVersion() { return KubernetesSnapshotExtension.class.getPackage().getImplementationTitle(); }
}
