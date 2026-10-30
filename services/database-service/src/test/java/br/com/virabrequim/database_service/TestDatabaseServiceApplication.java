package br.com.virabrequim.database_service;

import org.springframework.boot.SpringApplication;

public class TestDatabaseServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(DatabaseServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
