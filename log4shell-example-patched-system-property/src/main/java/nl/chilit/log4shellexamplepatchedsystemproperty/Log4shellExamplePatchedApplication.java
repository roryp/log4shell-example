package nl.chilit.log4shellexamplepatchedsystemproperty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Log4shellExamplePatchedApplication {

	public static void main(String[] args) {
		// The line below fixes the problem for log4j 2.10 and higher. Removing this does not break
		// the test, because the test does not use this main method to spin up the spring context
		System.setProperty("log4j2.formatMsgNoLookups", "true");
		SpringApplication.run(Log4shellExamplePatchedApplication.class, args);
	}

}
