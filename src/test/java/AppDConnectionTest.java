import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.kubernetes.AppDRestAuth;
import com.appdynamics.monitors.kubernetes.RestClient;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Map;


public class AppDConnectionTest {
    @Test
    public void testRestAPICookie() {
        try {
            File conFile = new File("src/test/resources/conf/config.yml");

            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            AppDRestAuth authObj = RestClient.getAuthToken(config);
            Assert.assertTrue(authObj.getToken().length() > 0);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }
}