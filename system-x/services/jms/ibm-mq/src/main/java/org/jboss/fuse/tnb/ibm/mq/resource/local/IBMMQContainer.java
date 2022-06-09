package org.jboss.fuse.tnb.ibm.mq.resource.local;

import org.jboss.fuse.tnb.common.utils.IOUtils;
import org.jboss.fuse.tnb.ibm.mq.service.IBMMQ;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class IBMMQContainer extends GenericContainer<IBMMQContainer> {
    private static final Path mqscCommandFilePath = Paths.get("target/" + IBMMQ.MQSC_COMMAND_FILE_NAME);

    public IBMMQContainer(String image, Map<String, String> env, String mqsc) {
        super(image);
        createLocalMqscCommandFile(mqsc);
        withExposedPorts(IBMMQ.DEFAULT_PORT);
        withFileSystemBind(mqscCommandFilePath.toAbsolutePath().toString(), IBMMQ.MQSC_COMMAND_FILES_LOCATION + "/" + IBMMQ.MQSC_COMMAND_FILE_NAME);
        withEnv(env);
        waitingFor(Wait.forLogMessage(".*Started web server.*", 1));
    }

    private void createLocalMqscCommandFile(String content) {
        IOUtils.writeFile(mqscCommandFilePath, content);
    }

    public int getPort() {
        return getMappedPort(IBMMQ.DEFAULT_PORT);
    }
}
