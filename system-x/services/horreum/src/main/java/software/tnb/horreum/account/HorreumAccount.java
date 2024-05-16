package software.tnb.horreum.account;

import software.tnb.common.account.Account;
import software.tnb.common.account.WithId;

import org.slf4j.LoggerFactory;

import java.util.Map;

public class HorreumAccount implements Account, WithId {

    private Map<String, HorreumDataSetAccount> tests;

    @Override
    public String credentialsId() {
        return "horreum";
    }

    private HorreumDataSetAccount getTest(String testName) {
        HorreumDataSetAccount account = tests.get(testName);
        LoggerFactory.getLogger(HorreumAccount.class).warn("About to print tests:");
        for (Map.Entry<String, HorreumDataSetAccount> entry : tests.entrySet()) {
            if (entry.getValue() == null) {
                LoggerFactory.getLogger(HorreumAccount.class).info(entry.getKey() + " -> null");
            } else {
                LoggerFactory.getLogger(HorreumAccount.class)
                        .info(entry.getKey() + " -> " + entry.getValue().token() + " -> "
                                + entry.getValue().username() + " -> " + entry.getValue().password() + " -> "
                                + entry.getValue().testName());
            }
        }
        if (account == null) {
            throw new IllegalArgumentException("Unknown test " + testName);
        }
        return account;
    }

    public String token(String testName) {
        return getTest(testName).token();
    }

    public String username(String testName) {
        return getTest(testName).username();
    }

    public String password(String testName) {
        return getTest(testName).password();
    }

    public String test(String testName) {
        HorreumDataSetAccount account = getTest(testName);
        return account.testName() == null ? testName : account.testName();
    }

    public void setTests(Map<String, HorreumDataSetAccount> tests) {
        this.tests = tests;
    }

    static class HorreumDataSetAccount {

        private String token;
        private String testName;
        private String username;
        private String password;

        public String token() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String username() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String password() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String testName() {
            return testName;
        }

        public void setTestName(String testName) {
            this.testName = testName;
        }
    }
}
