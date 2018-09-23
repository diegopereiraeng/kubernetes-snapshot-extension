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

The extension automatically creates the dashboard below.

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

## Installation

Either [Download the Extension from the latest Github release](https://github.com/sashaPM/kubernetes-snapshot-extension/releases/download/v.0.61/KubernetesSnapshotExtension-0.61.zip) or Build from Source.

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
  Note that the node name parameter *-Dappdynamics.agent.nodeName* must be defined for the machine agent startup, as shown
  in the [example start-up script](#restart-the-machine-agent) below.



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

  # Dashboard name suffix
  dashboardNameSuffix: "SUMMARY"

  # Time in seconds between the checks if the default dashboard needs to be recreated
  dashboardCheckInterval: "600"


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
3. Deploy the new version of the extension as described in the [Installation](#Installation) section.

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

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:help@appdynamics.com).
