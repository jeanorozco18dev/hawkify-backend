package com.hawkify;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HawkifyBackendApplication {

	public static void main(String[] args) {
		// Las fechas de reserva son dias calendario en Colombia, sin importar donde corra el servidor.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
		SpringApplication.run(HawkifyBackendApplication.class, args);
	}

}
