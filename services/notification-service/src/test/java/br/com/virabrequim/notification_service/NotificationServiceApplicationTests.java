package br.com.virabrequim.notification_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// token vazio garante que o teste nunca ligue o bot real, mesmo com TELEGRAM_BOT_TOKEN carregado no terminal
@SpringBootTest(properties = "telegram.bot.token=")
class NotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
