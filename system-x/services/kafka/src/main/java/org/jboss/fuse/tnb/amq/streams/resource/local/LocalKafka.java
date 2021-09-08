package org.jboss.fuse.tnb.amq.streams.resource.local;

import org.jboss.fuse.tnb.amq.streams.service.Kafka;
import org.jboss.fuse.tnb.amq.streams.validation.KafkaValidation;
import org.jboss.fuse.tnb.common.deployment.Deployable;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

import com.google.auto.service.AutoService;

import java.util.Properties;

@AutoService(Kafka.class)
public class LocalKafka extends Kafka implements Deployable {

    private static final Logger LOG = LoggerFactory.getLogger(LocalKafka.class);
    private StrimziContainer strimziContainer;
    private ZookeeperContainer zookeeperContainer;

    @Override
    public String bootstrapServers() {
        return strimziContainer.getContainerIpAddress() + ":" + strimziContainer.getKafkaPort();
    }

    @Override
    public void createTopic(String name, int partitions, int replicas) {
        // no-op
    }

    @Override
    public KafkaValidation validation() {
        if (validation == null) {
            validation = new KafkaValidation(producer, consumer);
        }
        return validation;
    }

    @Override
    public void deploy() {
        Network network = Network.newNetwork();

        LOG.info("Starting Zookeeper container");
        zookeeperContainer = new ZookeeperContainer(zookeeperLocalImage(), network);
        zookeeperContainer.start();
        LOG.info("Zookeeper container started");

        LOG.info("Starting Kafka container");
        strimziContainer = new StrimziContainer(kafkaLocalImage(), network);
        strimziContainer.start();
        LOG.info("Kafka container started");
    }

    @Override
    public void undeploy() {
        if (strimziContainer != null) {
            LOG.info("Stopping Kafka container");
            strimziContainer.stop();
        }

        if (zookeeperContainer != null) {
            LOG.info("Stopping Zookeeper container");
            zookeeperContainer.stop();
        }
    }

    @Override
    public void openResources() {
        Properties props = defaultClientProperties();
        props.setProperty("bootstrap.servers", bootstrapServers());

        producer = new KafkaProducer<>(props);
        consumer = new KafkaConsumer<>(props);
    }

    @Override
    public void closeResources() {
        producer.close();
        consumer.close();
    }
}
