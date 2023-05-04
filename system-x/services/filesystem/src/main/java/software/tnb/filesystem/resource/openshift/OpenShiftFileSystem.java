package software.tnb.filesystem.resource.openshift;

import static org.awaitility.Awaitility.await;

import software.tnb.common.openshift.OpenshiftClient;
import software.tnb.common.utils.ResourceFunctions;
import software.tnb.filesystem.service.FileSystem;

import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.auto.service.AutoService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;

@AutoService(FileSystem.class)
public class OpenShiftFileSystem extends FileSystem {
    private static final String NAMESPACE = OpenshiftClient.get().getNamespace();
    private String podLabelValue;

    @Override
    public void setAppName(String app) {
        this.podLabelValue = app;
    }

    @Override
    public String getFileContent(Path path) {
        final String podLabelKey = "deploymentconfig";
        podIsReady(podLabelKey, podLabelValue);
        final String podName = getPodName(podLabelKey, podLabelValue);
        final Pod pod = OpenshiftClient.get().getPod(podName);
        final String integrationContainer = OpenshiftClient.get().getIntegrationContainer(pod);

        try (InputStream is = OpenshiftClient.get().pods()
            .inNamespace(NAMESPACE)
            .withName(podName)
            .inContainer(integrationContainer).file(path.toString()).read()) {
            return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
    }

    private String getPodName(String key, String value) {
        return OpenshiftClient.get().getLabeledPods(key, value).get(0).getMetadata().getName();
    }

    private void podIsReady(String key, String value) {
        try {
            // FIXME verify
            await()
                .pollInterval(10, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.MINUTES)
                .until(() -> OpenshiftClient.get().getLabeledPods(key, value), ResourceFunctions::isSinglePodReady);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
