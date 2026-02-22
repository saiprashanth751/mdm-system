package com.moveinsync.mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MdmSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(MdmSystemApplication.class, args);
	}

}
