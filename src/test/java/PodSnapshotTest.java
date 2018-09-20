import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.appdynamics.monitors.kubernetes.SnapshotTasks.PodSnapshotRunner;
import com.appdynamics.monitors.kubernetes.Utilities;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.appdynamics.monitors.kubernetes.Utilities.ALL;

public class PodSnapshotTest {
    @Test
    public void buildMetricsStash() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            Utilities.ClusterName = Utilities.getClusterApplicationName(config);
            PodSnapshotRunner podSnapshotRunner = new PodSnapshotRunner();
            podSnapshotRunner.setTaskName("POD_TASK");

            SummaryObj summary = podSnapshotRunner.initPodSummaryObject(config, ALL, ALL);
            podSnapshotRunner.getSummaryMap().put(ALL, summary);
            podSnapshotRunner.serializeMetrics("src/test/resources");
            Assert.assertTrue(true);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void readMetricsStash() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            Utilities.ClusterName = Utilities.getClusterApplicationName(config);
            PodSnapshotRunner podSnapshotRunner = new PodSnapshotRunner();
            podSnapshotRunner.setTaskName("POD_TASK");

            List<AppDMetricObj> metricsList=  podSnapshotRunner.deserializeMetrics("src/test/resources");
            Assert.assertTrue(metricsList.size() > 0);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }
}
