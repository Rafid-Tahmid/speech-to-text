package com.rafid.speechtotext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RestClientConfiguration {

	/*
	 * Makes RestClient.Builder available for constructor injection into services
	 * that make outbound HTTP requests.
	 */
	@Bean
	public RestClient.Builder restClientBuilder() {
		/*
		 * URLConnection uses the JVM proxy properties supplied by TITAN. Timeouts
		 * prevent failed external requests from blocking indefinitely.
		 */
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(8));

		return RestClient.builder().requestFactory(requestFactory);
	}

}