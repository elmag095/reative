package com.reative.conenrtoller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController()
@RequestMapping("/pictures")
public class PicturesController {
    @GetMapping(value = "/{solo}/largest", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<byte[]> largest(@PathVariable int solo) {
        String url = String.format("https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/photos?sol=%d&api_key=DEMO_KEY", solo);
        return WebClient.create(url)
            .get()
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(jsonNode -> jsonNode.get("photos"))
            .flatMapMany(Flux::fromIterable)
            .map(jsonNode -> jsonNode.get("img_src"))
            .map(JsonNode::asText)
            .flatMap(pictureUrl -> WebClient.create(pictureUrl)
                .head()
                .exchangeToMono(ClientResponse::toBodilessEntity)
                .map(HttpEntity::getHeaders)
                .map(HttpHeaders::getLocation)
                .map(URI::toString)
                .flatMap(redirectUrl -> WebClient.create(redirectUrl)
                    .head()
                    .exchangeToMono(ClientResponse::toBodilessEntity)
                    .map(HttpEntity::getHeaders)
                    .map(HttpHeaders::getContentLength)
                    .map(size -> new Picture(redirectUrl, size))
                )
            )
            .reduce((objectMono, objectMono2) -> objectMono.size > objectMono2.size ? objectMono : objectMono2)
            .map(Picture::url)
            .flatMap(maxSizeUrl -> WebClient.create(maxSizeUrl)
                .mutate()
                .codecs(conf -> conf.defaultCodecs().maxInMemorySize(10_000_000))
                .build()
                .get()
                .exchangeToMono(resp -> resp.bodyToMono(byte[].class)));
    }

    record Picture(String url, long size) {
    }

    ;
}
