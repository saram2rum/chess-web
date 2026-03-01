package chess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChessSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChessSpringApplication.class, args);
	}

}
