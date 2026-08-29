package com.kologeski.interviewassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TechnicalInterviewAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(TechnicalInterviewAssistantApplication.class, args);
	}

}
