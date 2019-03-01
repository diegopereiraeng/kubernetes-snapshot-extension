# AppDynamics Kubernetes Snapshot Extension

## Use Case

The extension monitors events and the state of Kubernetes or OpenShift clusters, records attributes of resources: pods, endpoints, daemon sets, replica sets, deployments and nodes.
The data is received via Kubernetes API at a configurable interval and is sent to the AppDynamics Analytics Events API. Metrics are automatically created
and stored under a desired Application Tier. Metrics can be viewed in the Metrics Browser under Application -> Metric Browser -> Application Infrastructure Performance
-> Tier Name -> Custom Metrics -> Cluster Stats.

![Sample Dashboard](https://github.com/sashaPM/kubernetes-snapshot-extension/blob/master/metrics.png)
The extension aggregates metrics at the cluster level with further categorization by node and namespace.

If you want to monitor events only, use [Kubernetes Events Extension](https://github.com/Appdynamics/kubernetes-events-extension).
If you are interested in automated metric collection for events and other cluster resources, this extensions is self-sufficient. The event collection is enabled by default.

The extension automatically creates the dashboard below. The first attempt to create the dashboard is made
after the number of seconds defined in the *dashboardCheckInterval* setting has passed since the first run of the extension.

![The Default Dashboard](https://github.com/sashaPM/kubernetes-snapshot-extension/blob/master/dashboard.png)

Other specialized dashboards with the metrics collected can be built in AppDynamics -> Dashboards & Reports.

For each exposed metric, a named ADQL query is created by the extension along with the dashboard. Double-clicking on a dashboard widget will open the corresponding query.
These automatically created queries can also be accessed under Analytics -> Searches. The search names are built in the following format:
```
<Cluster Name. Metric Name>
```


## Prerequisites

 * This extension requires the Java Machine Agent.
 * The AppDynamics platform needs the Events Service set up.
 * REST API credentials. The account can be created under Administration -> Users. The user account must have rights to create dashboards and saved searches.
 * You will need one or more Transaction Analytics/APM Peak licenses to consume the raw data. Viewing metrics and the dashboard does not require PEAK licenses.
 * The number of collected metrics depends on the size of the cluster in terms of namespaces/projects and nodes.
 It may be necessary to increase the threshold for metric ingestion in the machine agent configuration:
 ```
 -Dappdynamics.agent.maxMetrics=2000
 ```
 The current metric collection rate is:
   * Cluster-specific: 52
   * Node-specific: 15
   * Namespace-specific: 39

 By default, the extension will collect the cluster-level metrics only. To monitor specific nodes or namespaces add the names of the desired nodes or namespaces
 to the `node` and `namespace` arrays of the config.yml respectively. See specific instruction below in the *Optional settings* section.
 If you request metric collection for some or all nodes or namespaces, make sure to adjust the maxMetric parameter accordingly.

## Installation

Either [Download the Extension from the latest Github release](https://github.com/Appdynamics/kubernetes-snapshot-extension/releases/download/0.85/KubernetesSnapshotExtension-0.85.zip) or Build from Source.

1. Deploy the `KubernetesSnapshotExtension-<VERSION>.zip` file into the `<machine agent home>/monitors` directory.

  `> unzip KubernetesSnapshotExtension-<VERSION>.zip -d <machine agent home>/monitors/`

2. Set up `config.yml`.

**Required settings**

  ```
    # Path to your kubectl client configuration. A typical location is "$HOME/.kube/config",
    # but it may differ on your machine
    kubeClientConfig:

    # Name of the application. It can be an existing application or a new application.
    # All collected metrics will be associated with it
    # The extension will first look for APPLICATION_NAME environmental variable.
    # The application name in the machine agent configuration must match this value
    appName: "Cluster-01"

    # Name of the tier where metrics will be stored. The tier will be associated
    # with the application configured earlier
    # The extension will first look for TIER_NAME environmental variable.
    # The tier name in the machine agent configuration must match this value.
    appTierName: "ClusterAgent"

    # Events API Key obtained from AppDynamics --> Analytics --> Configuration API Keys --> Add
    # The API Key you create needs to be able to Manage and Publish Custom Analytics Events
    # EVENT_ACCESS_KEY environmental variable can be used to populate the field
    eventsApiKey: ""

    # Global Account Name obtained from
    # AppDynamics --> Settings --> License --> Accounts --> Global Account Name
    # **GLOBAL_ACCOUNT_NAME** environmental variable can be used to populate the field
    accountName: ""

    # REST API credentials. The account must have rights to login, create dashboards and saved searches
    # REST_API_CREDENTIALS environmental variable can be used to populate the field
    controllerAPIUser: ""

    # Controller URL to access REST API
    controllerUrl: "http://example.appdynamics.com/controller/"

    # Absolute path to the json template for the default dashboard
    dashboardTemplatePath: "<full path>/monitors/KubernetesSnapshotExtension/templates/k8s_dashboard_template.json"

  ```
  **Note:** node name parameters *-Dappdynamics.agent.tierName* and *-Dappdynamics.agent.nodeName* must be defined for the machine agent startup, as shown
  in the [example start-up script](#restart-the-machine-agent) below.

  When running MA with this extension in Kubernetes, do not deploy as Daemon Set. Make it a 1 replica Deployment.
  Set *apiMode* config variable to *cluster*



  Optional settings:


  ```
  # List of resources that will be monitored. Comment out individual items to exclude
  #from monitoring
  entities:
  - type: "pod"
  - type: "node"
  - type: "deployment"
  - type: "daemon"
  - type: "replica"
  - type: "event"
  - type: "endpoint"

  # Proxy host. Env Var: APPD_PROXY_HOST
  proxyHost: ""

  # Proxy port. Env Var: APPD_PROXY_PORT
  proxyPort: ""
  # Proxy user name. Env Var: APPD_PROXY_USER
  proxyUser: ""

  # Proxy password. Env Var: APPD_PROXY_PASS
  proxyPass: ""

  # list of nodes to collect metrics for. If all nodes need to be monitored, set name to "all"
  nodes:
  #- name:

  # list of namespaces to collect metrics for. If all namespaces need to be monitored, set name to "all"
  namespaces:
  #- name:

  # Dashboard name suffix
  dashboardNameSuffix: "SUMMARY"

  # Time in seconds between the checks if the default dashboard needs to be recreated
  dashboardCheckInterval: "600"

  # Number of records in a batch posted to AppD events API
  batchSize: "100"


  # Events Service Endpoint. These Default settings are for SaaS Users. Change if you are on Premise
  eventsUrl: "https://analytics.api.appdynamics.com"

  # Name of the Pod  schema
  podsSchemaName: "k8s_pod_snapshots"

  # Name of the Node schema
  podsSecuritySchemaName: "k8_node_snapshots"

  # Name of the Deployment  schema
  deploySchemaName: "k8s_deploy_snapshots"

  # Name of the Daemon Set  schema
  daemonSchemaName: "k8s_daemon_snapshots"


  # Name of the Replica Set  schema
  rsSchemaName: "k8s_rs_snapshots"


  # Name of the EndPoint  schema
  endpointSchemaName: "k8s_endpoint_snapshots"

  # Name of the Events  schema
   eventsSchemaName: "k8s_events"


  ```

3. Configure frequency of updates in monitor.xml:

```
    <execution-style>periodic</execution-style>
    <execution-frequency-in-seconds>200</execution-frequency-in-seconds>
```

4. Restart the Machine Agent

A sample startup script for the machine agent with an elevated metrics threshold:

```
#!/bin/bash
SVM_PROPERTIES="-Dappdynamics.controller.hostName=${CONTROLLER_HOST}"
SVM_PROPERTIES+=" -Dappdynamics.controller.port=${CONTROLLER_PORT}"
SVM_PROPERTIES+=" -Dappdynamics.agent.applicationName=${APPLICATION_NAME}" # must match the appName configuration value
SVM_PROPERTIES+=" -Dappdynamics.agent.tierName=${TIER_NAME}"  # must must match the appTierName configuration value
SVM_PROPERTIES+=" -Dappdynamics.agent.nodeName=${TIER_NAME}_node1"  # must be defined for the metrics to be accepted
SVM_PROPERTIES+=" -Dappdynamics.agent.accountName=${ACCOUNT_NAME}"
SVM_PROPERTIES+=" -Dappdynamics.agent.accountAccessKey=${ACCOUNT_ACCESS_KEY}"
SVM_PROPERTIES+=" -Dappdynamics.agent.uniqueHostId=Master-${APPLICATION_NAME}"
SVM_PROPERTIES+=" -Dappdynamics.controller.ssl.enabled=true"
SVM_PROPERTIES+=" -Dappdynamics.sim.enabled=true"
SVM_PROPERTIES+=" -Dappdynamics.agent.maxMetrics=2000"
#SVM_PROPERTIES+=" -Dmetric.http.listener=true"
#SVM_PROPERTIES+=" -Dmetric.http.listener.host=0.0.0.0"

./bin/machine-agent ${SVM_PROPERTIES} -d -p ./pid.txt

```

## Upgrade
1. Stop the machine agent
2. Delete the existing default dashboard and the automatically generated searches in Analytics. They will be re-created by the extension.
3. Deploy the new version of the extension as described in the [Installation](#installation) section.

## Build from Source

1. Clone this repository
2. Run `mvn -DskipTests clean install`
3. The `KubernetesSnapshotExtension-<VERSION>.zip` file can be found in the `target` directory

## Directory Structure

<table><tbody>
<tr>
<th align = 'left'> Directory/File </th>
<th align = 'left'> Description </th>
</tr>
<tr>
<td class='confluenceTd'> src/main/resources/conf </td>
<td class='confluenceTd'> Contains monitor.xml and config.yml</td>
</tr>
<tr>
<td class='confluenceTd'> src/main/resources/templates </td>
<td class='confluenceTd'> Contains the default dashboard template k8s_dashboard_template.json</td>
</tr>
<tr>
<td class='confluenceTd'> src/main/java </td>
<td class='confluenceTd'> Contains source code for the Kubernetes Snapshot extension </td>
</tr>
<tr>
<td class='confluenceTd'> src/test/java </td>
<td class='confluenceTd'> Contains test code for the Kubernetes Snapshot extension </td>
</tr>
<tr>
<td class='confluenceTd'> target </td>
<td class='confluenceTd'> Only obtained when using maven. Run 'maven clean install' to get the distributable .zip file. </td>
</tr>
<tr>
<td class='confluenceTd'> pom.xml </td>
<td class='confluenceTd'> maven build script to package the project (required only if changing Java code) </td>
</tr>
</tbody>
</table>

## Deploying to a Kubernetes cluster

The extension bundled with the machine agent can run in the cluster as a deployment.
* Download the desired version of the machine agent from [The official downloads site](https://download.appdynamics.com)
* Unzip the contents of the archive in the deployment/artifacts folder so that the artifacts folder becomes the machine agent root directory
* [Download the Extension from the latest Github release](https://github.com/Appdynamics/kubernetes-snapshot-extension/releases/download/0.85/KubernetesSnapshotExtension-0.85.zip)
* Unzip its contents into the artifacts/monitors folder.

The resulting directory structure will look as follows
artifacts
```
  ...
  machineagent.jar
  ...
  monitors
     KubernetesSnapshotExtension
        config.yml
        monitor.yml
        templates
          k8s_dashboard_template.json
```

* Navigate to deployment directory and run the command below to create the image. Provide the desired image name and tag
```
   docker build -t <image-name> .
```
* Create a secret for the necessary access keys.
  * Obtain controller access key License - Account - Access key
  * Obtain events API key AppDynamics --> Analytics --> Configuration API Keys --> Add

The API Key needs to be able to Manage and Publish Custom Analytics Events
![New Event Key](https://github.com/Appdynamics/kubernetes-snapshot-extension/blob/master/assets/events_key.png?raw=true)

  * Create a new user for rest API access
Administration - Users - New
When creating the account for rest API access, you can setup a role with the following permissions and assign the user to it

![New Role](https://github.com/Appdynamics/kubernetes-snapshot-extension/blob/master/assets/role.png?raw=true)

![App permissions](https://github.com/Appdynamics/kubernetes-snapshot-extension/blob/master/assets/role-app.png?raw=true)

![Dashboard permissions](https://github.com/Appdynamics/kubernetes-snapshot-extension/blob/master/assets/role-dashboards.png?raw=true)

![Events permissions](https://github.com/Appdynamics/kubernetes-snapshot-extension/blob/master/assets/role-events.png?raw=true)

![Searches permissions](https://github.com/Appdynamics/kubernetes-snapshot-extension/blob/master/assets/role_searches.png?raw=true)

![Assign role](https://github.com/Appdynamics/kubernetes-snapshot-extension/blob/master/assets/role-assign.png?raw=true)

  * Run the following command to create a secret
```
oc create secret generic appd-secret --from-literal=ACCOUNT_ACCESS_KEY=<controller access key> --from-literal=EVENT_ACCESS_KEY=<event api key> --from-literal=REST_API_CREDENTIALS=<username@accountname:password>
```
* Open ma-config.yaml and update the settings with the information specific to your controller and save
* Open ma-sa-rbac.yaml and update ClusterRoleBinding subject namespace with the namespace where k8s-monitor-sa account was created
* Open machine-agent.yaml and update the image reference
* Run the deployment
```
  kubectl create -f specs/
```



## Contributing

Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/sashaPM/kubernetes-snapshot-extension).

## Troubleshooting

1. Verify Machine Agent Data: Please start the Machine Agent without the extension and make sure that it reports data. Verify that the machine agent status is UP and it is reporting Hardware Metrics.
2. config.yml: Validate the file [here](http://www.yamllint.com/)
3. Check Logs: There could be some obvious errors in the machine agent logs. Please take a look.
4. `The config cannot be null` error.
   This usually happens when on a windows machine in monitor.xml you give config.yaml file path with linux file path separator */*. Use Windows file path separator *\* e.g. *monitors\Monitor\config.yaml*. For Windows, please specify
   the complete path
5. Collect Debug Logs: Edit the file, *<MachineAgent>/conf/logging/log4j.xml* and update the level of the appender *com.appdynamics* and *com.singularity* to debug. Let it run for 5-10 minutes and attach the logs to a support ticket.

## Support

This extension is provided as a beta feature: it is our intention to incorporate this into the AppDynamics Platform
to provide additional integration with our APM and Business iQ products.
AppDynamics reserves the right to change beta features at any time before making them generally available
as well as never making them generally available. Any buying decisions should be made based on features and products
that are currently generally available.

For any questions, please contact [AppDynamics Center of Excellence](mailto:help@appdynamics.com).
