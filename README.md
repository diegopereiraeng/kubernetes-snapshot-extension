# AppDynamics Kubernetes Snapshot Extension

This extension works only with the standalone machine agent.

## Use Case

Monitors state of the Kubernetes or Openshift clusters and records attributes of resources like pods, endpoints, daemonset, replica sets and deployments.
The data is received via Kubernetes API and is pushed to the AppDynamics Analytics Events API for reporting.

## Prerequisites

 * This extension requires the Java Machine Agent
 * The AppDynamics platform needs the Events Service set up
 * You will need one or more Transaction Analytics/APM Peak licenses to consume the data

## Installation

Either [Download the Extension from the latest Github release](https://github.com/sashaPM/kubernetes-snapshot-extension/releases/download/0.1/KubernetesSnapshotExtension-0.1.zip) or Build from Source.

1. Deploy the `KubernetesSnapshotExtension-<VERSION>.zip` file into the `<machine agent home>/monitors` directory.

  `> unzip KubernetesSnapshotExtension-<VERSION>.zip -d <machine agent home>/monitors/`

2. Set up `config.yml`.
  ```
    # Path to your kubectl Client configuration. A typical location is "$HOME/.kube/config", but it may differ on your machine
    kubeClientConfig:

    # Events API Key obtained from AppDynamics --> Analytics --> Configuration API Keys --> Add
    # The API Key you create needs to be able to Manage and Publish Custom Analytics Events
    eventsApiKey: ""

    # Global Account Name obtained from
    # AppDynamics --> Settings --> License --> Accounts --> Global Account Name
    accountName: ""
  ```
  Optional settings:

  ```
  # Name od the Pod attribute schema
  podsSchemaName: "k8s_pod_snapshots"

  # Name od the Pod Security  schema
  podsSecuritySchemaName: "openshift_pod_sec_snapshots"

  # Name od the Deployment attribute schema
  deploySchemaName: "openshift_deploy_snapshots"

  # Name od the Daemon Set attribute schema
  daemonSchemaName: "openshift_daemon_snapshots"


  # Name od the Replica Set attribute schema
  rsSchemaName: "openshift_rs_snapshots"


  # Name od the EndPoint attribute schema
  endpointSchemaName: "k8s_endpoint_snapshots"


  # Name od the Summary Data schema
  summarySchemaName: "openshift_cluster_summary"

  # Flag indicating whether to collect Pod attributes
  includePodSnapshot: "true"

  # Flag indicating whether to collect Pod Security attributes
  includePodSecuritySnapshot: "true"

  # Flag indicating whether to collect Deployment attributes
  includeDeploySnapshot: "true"

  # Flag indicating whether to collect Daemon Set attributes
  includeDaemonSnapshot: "true"

  # Flag indicating whether to collect Endpoint attributes
  includeEndpointSnapshot: "true"


  # Flag indicating whether to collect Replica Set attributes
  includeRSSnapshot: "true"


  # Flag indicating whether to collect Summary attributes
  includeSummary: "true"


  # Flag indicating whether to log payload being sent to AppD API endpoint
  logPayloads: "true"

  ```

3. Restart the Machine Agent.

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
<td class='confluenceTd'> src/main/resources/config </td>
<td class='confluenceTd'> Contains monitor.xml and config.yml</td>
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
