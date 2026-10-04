package com.menusolomon.place.config;

import com.menusolomon.place.client.KakaoPlaceSearchClient;
import com.menusolomon.place.client.PlaceSearchClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoClientConfiguration {
    @Bean
    public PlaceSearchClient placeSearchClient(@Value("${kakao.rest-api-key}") String key) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder().baseUrl("https://dapi.kakao.com")
                .requestFactory(factory).build();
        return new KakaoPlaceSearchClient(restClient, key);
    }
}
