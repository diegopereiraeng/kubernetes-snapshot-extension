import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.kubernetes.Dashboard.ADQLSearchGenerator;
import com.appdynamics.monitors.kubernetes.Models.AdqlSearchObj;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.SnapshotTasks.PodSnapshotRunner;
import com.appdynamics.monitors.kubernetes.Utilities;
import org.junit.Assert;
import org.junit.Test;

import javax.rmi.CORBA.Util;
import java.io.File;
import java.util.Map;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_DEF_POD;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_POD;
import static com.appdynamics.monitors.kubernetes.Utilities.ALL;

public class ADQLSearchTest {

    @Test
    public void testCreteSearch() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            Utilities.ClusterName = Utilities.getClusterApplicationName(config);
            ADQLSearchGenerator.loadAllSearches(config);

            PodSnapshotRunner podSnapshotRunner = new PodSnapshotRunner();

            AppDMetricObj metricObj = podSnapshotRunner.initMetrics(config, ALL, ALL).get(0);
            AdqlSearchObj obj = ADQLSearchGenerator.getSearchForMetric(config, metricObj);
            Assert.assertTrue(obj != null);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }
}
