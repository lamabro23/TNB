package software.tnb.knative.resource;

import static org.junit.jupiter.api.Assertions.fail;

import software.tnb.common.deployment.OpenshiftDeployable;
import software.tnb.common.deployment.WithOperatorHub;
import software.tnb.common.openshift.OpenshiftClient;
import software.tnb.common.utils.ResourceParsers;
import software.tnb.common.utils.WaitUtils;
import software.tnb.knative.service.Knative;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.service.AutoService;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;

@AutoService(Knative.class)
public class OpenshiftKnative extends Knative implements OpenshiftDeployable, WithOperatorHub {
    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftKnative.class);
    private static final String CRD_GROUP = "operator.knative.dev";
    private static final String CRD_VERSION = "v1alpha1";

    private static final CustomResourceDefinitionContext EVENTING_CTX = new CustomResourceDefinitionContext.Builder()
        .withName("KnativeEventing")
        .withGroup(CRD_GROUP)
        .withVersion(CRD_VERSION)
        .withPlural("knativeeventings")
        .withScope("Namespaced")
        .build();

    private static final CustomResourceDefinitionContext SERVING_CTX = new CustomResourceDefinitionContext.Builder()
        .withName("KnativeServing")
        .withGroup(CRD_GROUP)
        .withVersion(CRD_VERSION)
        .withPlural("knativeservings")
        .withScope("Namespaced")
        .build();

    private static final String EVENTING_NAMESPACE = "knative-eventing";
    private static final String EVENTING_CR_NAME = "knative-eventing";

    private static final String SERVING_NAMESPACE = "knative-serving";
    private static final String SERVING_CR_NAME = "knative-serving";

    @Override
    public void undeploy() {
        // don't undeploy knative in any case - it may affect other tests running on the same cluster
    }

    @Override
    public void openResources() {
        // no-op
    }

    @Override
    public void closeResources() {
        if (validation != null) {
            validation.deleteCreatedResources();
        }
    }

    @Override
    public void create() {
        LOG.debug("Creating serverless operator");

        OpenshiftClient.get().createNamespace(targetNamespace());
        // Create subscription for serverless operator
        createSubscription();

        // The serverless operator also creates eventing and serving namespaces if they are not present
        WaitUtils.waitFor(() -> OpenshiftClient.get().namespaces().withName(EVENTING_NAMESPACE).get() != null,
            "Waiting until the eventing namespace is created");
        WaitUtils.waitFor(() -> OpenshiftClient.get().namespaces().withName(SERVING_NAMESPACE).get() != null,
            "Waiting until the serving namespace is created");

        // Create eventing and serving custom resource
        try {
            RetryPolicy<Void> retryPolicy = RetryPolicy.<Void>builder()
                .handle(KubernetesClientTimeoutException.class)
                .withDelay(Duration.ofSeconds(5))
                .withMaxRetries(3)
                .build();
            Failsafe.with(retryPolicy).run(() -> OpenshiftClient.get().customResource(EVENTING_CTX).inNamespace(EVENTING_NAMESPACE)
                .createOrReplace(createCr("KnativeEventing", EVENTING_CR_NAME, EVENTING_NAMESPACE)));
            Failsafe.with(retryPolicy).run(() -> OpenshiftClient.get().customResource(SERVING_CTX).inNamespace(SERVING_NAMESPACE)
                .createOrReplace(createCr("KnativeServing", SERVING_CR_NAME, SERVING_NAMESPACE)));
        } catch (FailsafeException e) {
            fail("Unable to create custom resources: ", e.getCause());
        }
    }

    @Override
    public boolean isReady() {
        return OpenshiftClient.get().apps().deployments().inNamespace(targetNamespace()).list().getItems().stream()
            .allMatch(d -> d.getSpec().getReplicas() == 0 || d.getSpec().getReplicas().equals(d.getStatus().getAvailableReplicas()))
            && OpenshiftClient.get().apps().deployments().inNamespace(EVENTING_NAMESPACE).list().getItems().stream()
            .allMatch(d -> d.getSpec().getReplicas() == 0 || d.getSpec().getReplicas().equals(d.getStatus().getAvailableReplicas()))
            && OpenshiftClient.get().apps().deployments().inNamespace(SERVING_NAMESPACE).list().getItems().stream()
            .allMatch(d -> d.getSpec().getReplicas() == 0 || d.getSpec().getReplicas().equals(d.getStatus().getAvailableReplicas()));
    }

    @Override
    public boolean isDeployed() {
        List<Pod> pods = OpenshiftClient.get().pods().inNamespace(targetNamespace()).withLabel("name", "knative-operator").list().getItems();
        return pods.size() == 1 && ResourceParsers.isPodReady(pods.get(0))
            && ((List) OpenshiftClient.get().customResource(EVENTING_CTX).list(EVENTING_NAMESPACE).get("items")).size() == 1
            && ((List) OpenshiftClient.get().customResource(SERVING_CTX).list(SERVING_NAMESPACE).get("items")).size() == 1;
    }

    @Override
    public Predicate<Pod> podSelector() {
        // not applicable for knative
        return null;
    }

    @Override
    public void restart() {
        // not applicable for knative
    }

    private Map<String, Object> createCr(String kind, String name, String namespace) {
        // Too short to bother with CR classes
        Map<String, Object> cr = new HashMap<>();
        cr.put("apiVersion", CRD_GROUP + "/" + CRD_VERSION);
        cr.put("kind", kind);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("namespace", namespace);
        cr.put("metadata", metadata);
        return cr;
    }

    @Override
    public String targetNamespace() {
        return "openshift-serverless";
    }

    @Override
    public String operatorName() {
        return "serverless-operator";
    }

    @Override
    public boolean clusterWide() {
        return true;
    }
}
