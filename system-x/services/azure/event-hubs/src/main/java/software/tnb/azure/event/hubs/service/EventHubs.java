package software.tnb.azure.event.hubs.service;

import software.tnb.azure.event.hubs.account.AzureEventHubsAccount;
import software.tnb.azure.event.hubs.client.EventHubClients;
import software.tnb.azure.event.hubs.validation.EventHubsValidation;
import software.tnb.azure.storage.blob.service.StorageBlob;
import software.tnb.common.service.Service;
import software.tnb.common.service.ServiceFactory;

import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.auto.service.AutoService;

import java.util.Date;

@AutoService(EventHubs.class)
public class EventHubs extends Service<AzureEventHubsAccount, EventHubClients, EventHubsValidation> {
    private final StorageBlob storageBlob = ServiceFactory.create(StorageBlob.class);
    private String blobContainer;

    public String getBlobContainer() {
        return blobContainer;
    }

    public StorageBlob getBlobService() {
        return storageBlob;
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        storageBlob.validation().deleteBlobContainer(blobContainer);
        storageBlob.afterAll(extensionContext);

        if (client != null) {
            client.close();
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        storageBlob.beforeAll(extensionContext);
        blobContainer = "event-hub-blob-" + new Date().getTime();
        storageBlob.validation().createBlobContainer(blobContainer);

        client = new EventHubClients(account());

        validation = new EventHubsValidation(client());
    }
}
