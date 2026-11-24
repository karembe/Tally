package dev.tally;

import org.springframework.boot.SpringApplication;

public class TestTallyApplication {

	public static void main(String[] args) {
		SpringApplication.from(TallyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
