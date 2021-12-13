# Log4Shell Exploit Test

The goal of this project is to demonstrate the log4j cve-2021-44228 exploit vulnerability in a spring-boot setup, and to show how to fix it.

This project contains three submodules. One of these has vulnerable code, the other two are patched.

## How to use this project

Run `./mvnw clean test` in the root of the project in order to run the tests in both modules.
In `log4shell-example-unpatched`, you will see a lot of exceptions (the test will still pass, because this is expected), because it doesn't get the correct response from the server it tries to connect to.

## When am I vulnerable?

Your application is vulnerable if you have overriden the default logger, so that it uses the log4j2 implementation and you have not overridden the version of log4j2 that is used. The pom will look something like this:

```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
        <exclusions>
            <exclusion>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-logging</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-log4j2</artifactId>
    </dependency>
```

You can see that your log invocations are now vulnerable by running the test in `log4shell-example-unpatched`. This test succeeds when the log invocations are vulnerable.

### Using the spring integration test to see if your own application is vulnerable

#### 1. Add the `Log4ShellTest` to your project

Add the `Log4ShellTest` from `log4shell-example-patched-version` to your project:

```java
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@Import(Log4ShellTest.Log4ShellConfig.class)
public class Log4ShellTest {

    @Autowired
    private List<Log4ShellService> servicesToTest;

    @Test
    public void testVulnerabilityPatched() throws Exception {

        CountDownLatch waitLatch = new CountDownLatch(1);
        AtomicInteger connectionAttemptCounter = new AtomicInteger();
        Thread listener = new Thread(() -> {
            try {
                ServerSocket socket = new ServerSocket(22345);
                while(true) {
                    waitLatch.countDown();
                    Socket connection = socket.accept();
                    connectionAttemptCounter.getAndIncrement();
                    connection.close();
                }
            }
            catch(IOException ex) {
                throw new IllegalStateException(ex);
            }
        });
        listener.start();
        waitLatch.await();

        servicesToTest.forEach(service -> service.testLog("${jndi:ldap://127.0.0.1:22345}"));

        Assertions.assertEquals(0, connectionAttemptCounter.get());
        // If you're not using lombok, change the 6 to 2
        Assertions.assertEquals(6, servicesToTest.size());

        listener.interrupt();

    }

    @Configuration
    @ComponentScan
    public static class Log4ShellConfig {

    }

    public interface Log4ShellService {
        void testLog(String arg);
    }

    @Component
    public static class Service1 implements Log4ShellService {

        private static final Logger logger = LogManager.getLogger("Test");

        @Override
        public void testLog(String arg) {
            logger.info("Test: " + arg);
        }
    }

    @Component
    public static class Service2 implements Log4ShellService {

        private static final Logger logger = LogManager.getLogger("Test");

        @Override
        public void testLog(String arg) {
            logger.info("Test: {}", arg);
        }
    }

    // Remove this class if you're not using lombok
    @Component
    @Slf4j
    public static class Service3 implements Log4ShellService {

        @Override
        public void testLog(String arg) {
            log.info("Test: {}", arg);
        }
    }

    // Remove this class if you're not using lombok
    @Component
    @Slf4j
    public static class Service4 implements Log4ShellService {

        @Override
        public void testLog(String arg) {
            log.info("Test: " + arg);
        }
    }

    // Remove this class if you're not using lombok
    @Component
    @Log4j2
    public static class Service5 implements Log4ShellService {

        @Override
        public void testLog(String arg) {
            log.info("Test: {}", arg);
        }
    }

    // Remove this class if you're not using lombok
    @Component
    @Log4j2
    public static class Service6 implements Log4ShellService {

        @Override
        public void testLog(String arg) {
            log.info("Test: " + arg);
        }
    }

}

```

#### 2. Verify that this test is failing

Run the test, and see that it fails. If it doesn't fail, you should see the following log lines and you are not vulnerable:

```
2021-12-13 22:05:01.197  INFO 21216 --- [           main] Test                                     : Test: ${jndi:ldap://127.0.0.1:22345}
2021-12-13 22:05:01.198  INFO 21216 --- [           main] Test                                     : Test: ${jndi:ldap://127.0.0.1:22345}
2021-12-13 22:05:01.199  INFO 21216 --- [           main] n.c.l.Log4ShellTest$Service3             : Test: ${jndi:ldap://127.0.0.1:22345}
2021-12-13 22:05:01.199  INFO 21216 --- [           main] n.c.l.Log4ShellTest$Service4             : Test: ${jndi:ldap://127.0.0.1:22345}
2021-12-13 22:05:01.199  INFO 21216 --- [           main] n.c.l.L.Service5                         : Test: ${jndi:ldap://127.0.0.1:22345}
2021-12-13 22:05:01.199  INFO 21216 --- [           main] n.c.l.L.Service6                         : Test: ${jndi:ldap://127.0.0.1:22345}
```

If the test does fail, you should see a stack trace that says that a connection is closed:

```
2021-12-13 22:06:50,635 main WARN Error looking up JNDI resource [ldap://127.0.0.1:22345]. javax.naming.CommunicationException: anonymous bind failed: 127.0.0.1:22345 [Root exception is java.net.SocketException: Socket closed]
	at java.naming/com.sun.jndi.ldap.LdapClient.authenticate(LdapClient.java:198)
	at java.naming/com.sun.jndi.ldap.LdapCtx.connect(LdapCtx.java:2895)
	at java.naming/com.sun.jndi.ldap.LdapCtx.<init>(LdapCtx.java:348)
	at java.naming/com.sun.jndi.url.ldap.ldapURLContextFactory.getUsingURLIgnoreRootDN(ldapURLContextFactory.java:60)
	at java.naming/com.sun.jndi.url.ldap.ldapURLContext.getRootURLContext(ldapURLContext.java:61)
	at java.naming/com.sun.jndi.toolkit.url.GenericURLContext.lookup(GenericURLContext.java:204)
	at java.naming/com.sun.jndi.url.ldap.ldapURLContext.lookup(ldapURLContext.java:94)
	at java.naming/javax.naming.InitialContext.lookup(InitialContext.java:409)
	at org.apache.logging.log4j.core.net.JndiManager.lookup(JndiManager.java:172)
	at org.apache.logging.log4j.core.lookup.JndiLookup.lookup(JndiLookup.java:56)
	at org.apache.logging.log4j.core.lookup.Interpolator.lookup(Interpolator.java:221)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.resolveVariable(StrSubstitutor.java:1110)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.substitute(StrSubstitutor.java:1033)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.substitute(StrSubstitutor.java:912)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.replace(StrSubstitutor.java:467)
	at org.apache.logging.log4j.core.pattern.MessagePatternConverter.format(MessagePatternConverter.java:132)
	at org.apache.logging.log4j.core.pattern.PatternFormatter.format(PatternFormatter.java:38)
	at org.apache.logging.log4j.core.layout.PatternLayout$PatternSerializer.toSerializable(PatternLayout.java:344)
	at org.apache.logging.log4j.core.layout.PatternLayout.toText(PatternLayout.java:244)
	at org.apache.logging.log4j.core.layout.PatternLayout.encode(PatternLayout.java:229)
	at org.apache.logging.log4j.core.layout.PatternLayout.encode(PatternLayout.java:59)
	at org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender.directEncodeEvent(AbstractOutputStreamAppender.java:197)
	at org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender.tryAppend(AbstractOutputStreamAppender.java:190)
	at org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender.append(AbstractOutputStreamAppender.java:181)
	at org.apache.logging.log4j.core.config.AppenderControl.tryCallAppender(AppenderControl.java:156)
	at org.apache.logging.log4j.core.config.AppenderControl.callAppender0(AppenderControl.java:129)
	at org.apache.logging.log4j.core.config.AppenderControl.callAppenderPreventRecursion(AppenderControl.java:120)
	at org.apache.logging.log4j.core.config.AppenderControl.callAppender(AppenderControl.java:84)
	at org.apache.logging.log4j.core.config.LoggerConfig.callAppenders(LoggerConfig.java:540)
	at org.apache.logging.log4j.core.config.LoggerConfig.processLogEvent(LoggerConfig.java:498)
	at org.apache.logging.log4j.core.config.LoggerConfig.log(LoggerConfig.java:481)
	at org.apache.logging.log4j.core.config.LoggerConfig.log(LoggerConfig.java:456)
	at org.apache.logging.log4j.core.config.AwaitCompletionReliabilityStrategy.log(AwaitCompletionReliabilityStrategy.java:82)
	at org.apache.logging.log4j.core.Logger.log(Logger.java:161)
	at org.apache.logging.log4j.spi.AbstractLogger.tryLogMessage(AbstractLogger.java:2205)
	at org.apache.logging.log4j.spi.AbstractLogger.logMessageTrackRecursion(AbstractLogger.java:2159)
	at org.apache.logging.log4j.spi.AbstractLogger.logMessageSafely(AbstractLogger.java:2142)
	at org.apache.logging.log4j.spi.AbstractLogger.logMessage(AbstractLogger.java:2017)
	at org.apache.logging.log4j.spi.AbstractLogger.logIfEnabled(AbstractLogger.java:1983)
	at org.apache.logging.log4j.spi.AbstractLogger.info(AbstractLogger.java:1320)
	at nl.chilit.log4shellexampleunpatched.Log4ShellTest$Service1.testLog(Log4ShellTest.java:78)
	at nl.chilit.log4shellexampleunpatched.Log4ShellTest.lambda$testVulnerabilityNotPatched$1(Log4ShellTest.java:52)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	at nl.chilit.log4shellexampleunpatched.Log4ShellTest.testVulnerabilityNotPatched(Log4ShellTest.java:52)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:78)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:567)
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:725)
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:60)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:131)
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:149)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:140)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:84)
	at org.junit.jupiter.engine.execution.ExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(ExecutableInvoker.java:115)
	at org.junit.jupiter.engine.execution.ExecutableInvoker.lambda$invoke$0(ExecutableInvoker.java:105)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:106)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:64)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:45)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:37)
	at org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:104)
	at org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:98)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$7(TestMethodTestDescriptor.java:214)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:210)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:135)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:66)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:151)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:141)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:139)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:138)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:95)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:155)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:141)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:139)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:138)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:95)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:155)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:141)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:139)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:138)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:95)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:35)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:54)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:107)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:88)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.lambda$execute$0(EngineExecutionOrchestrator.java:54)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.withInterceptedStreams(EngineExecutionOrchestrator.java:67)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:52)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:114)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:86)
	at org.junit.platform.launcher.core.DefaultLauncherSession$DelegatingLauncher.execute(DefaultLauncherSession.java:86)
	at org.junit.platform.launcher.core.SessionPerRequestLauncher.execute(SessionPerRequestLauncher.java:53)
	at com.intellij.junit5.JUnit5IdeaTestRunner.startRunnerWithArgs(JUnit5IdeaTestRunner.java:71)
	at com.intellij.rt.junit.IdeaTestRunner$Repeater.startRunnerWithArgs(IdeaTestRunner.java:33)
	at com.intellij.rt.junit.JUnitStarter.prepareStreamsAndStart(JUnitStarter.java:235)
	at com.intellij.rt.junit.JUnitStarter.main(JUnitStarter.java:54)
Caused by: java.net.SocketException: Socket closed
	at java.base/sun.nio.ch.NioSocketImpl.ensureOpenAndConnected(NioSocketImpl.java:165)
	at java.base/sun.nio.ch.NioSocketImpl.beginWrite(NioSocketImpl.java:366)
	at java.base/sun.nio.ch.NioSocketImpl.implWrite(NioSocketImpl.java:411)
	at java.base/sun.nio.ch.NioSocketImpl.write(NioSocketImpl.java:440)
	at java.base/sun.nio.ch.NioSocketImpl$2.write(NioSocketImpl.java:826)
	at java.base/java.net.Socket$SocketOutputStream.write(Socket.java:1045)
	at java.base/java.io.BufferedOutputStream.flushBuffer(BufferedOutputStream.java:81)
	at java.base/java.io.BufferedOutputStream.flush(BufferedOutputStream.java:142)
	at java.naming/com.sun.jndi.ldap.Connection.writeRequest(Connection.java:414)
	at java.naming/com.sun.jndi.ldap.Connection.writeRequest(Connection.java:387)
	at java.naming/com.sun.jndi.ldap.LdapClient.ldapBind(LdapClient.java:359)
	at java.naming/com.sun.jndi.ldap.LdapClient.authenticate(LdapClient.java:192)
	... 110 more
```

#### 3. Patch your application

Patch your application in one of the ways described below, and rerun the test. The test should now be green.

## Mitigating the vulnerability

You can mitigate the vulnerability in a few ways, as demonstrated in `log4shell-example-patched-system-property` and `log4shell-example-patched-version`.

### Mitigation through upgrading the version (best way)

If your application setup allows you to, the best way to get rid of the vulnerability is to upgrade the version of log4j to 2.15 minimum.
You can do this by setting `log4j2.version` to `2.15.0`:

```xml
    <properties>
        <log4j2.version>2.15.0</log4j2.version>
    </properties>
```

This solution only works *when you are using the spring-boot-parent artifact somewhere in your chain as a parent*. If you are using `dependencyManagement` to manage your spring-boot dependencies, this solution is not going to work for you:

```xml
    <!-- if you are using dependency management like this, upping the version by setting log4j2.version is NOT going to work -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>2.6.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

In that case, you will have to override the versions for each part of log4j, and that gets messy really quickly.

### Mitigation through setting a system property

NOTE: This fix ONLY works for log4j versions >= 2.10

You can also mitigate the problem by setting the system property `log4j2.formatMsgNoLookups` to `true`. PLEASE NOTE: you CANNOT set this in your `application.properties` or `application.yml`.
You have to:
 - set it either on the command line (by adding `-Dlog4j2.formatMsgNoLookups=true` to your java command to start the service)
 - or as an environment variable (by setting `LOG4J_FORMAT_MSG_NO_LOOKUPS` to `true`) in the environment the application is running in
 - or, as demonstrated in `log4shell-example-patched-system-property`, by setting a system property even before spring boot has started (before calling `SpringApplication.run(...)`)

The last solution is implemented both in `Log4JMitigationExtension` and `Log4JMitigationExtension`. Since the integration test does not use the main method
from `Log4JMitigationExtension`, an extension is used in order to set the property in time for log4j to pick it up.
