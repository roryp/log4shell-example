package nl.chilit.log4shellexamplepatchedsystemproperty;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class Log4JMitigationExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        System.setProperty("log4j2.formatMsgNoLookups", "true");
    }
}
