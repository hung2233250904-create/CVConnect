package com.cvconnect.config;

import com.cvconnect.dto.Response;
import com.cvconnect.dto.VerifyResponse;
import com.cvconnect.service.AuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class Filter implements GlobalFilter {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private final String[] PUBLIC_ENDPOINTS = {
            "/v3/api-docs", "/swagger-ui/", "/swagger-ui.html", "/swagger-ui/index.html",
            // user service
            "/user/auth/", "/user/oauth2/authorization/google", "/user/login/oauth2/code", "/user/org-member/reply-invite-join-org",

            // notify service


            // core service
                "/core/industry/public", "/core/job-ad/outside", "/core/org/outside", "/core/attach-file/download"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean isPublicEndpoint = Arrays.stream(PUBLIC_ENDPOINTS).anyMatch(
                endpoint -> exchange.getRequest().getURI().getPath().contains(endpoint)
        );
        if (isPublicEndpoint){
            return chain.filter(exchange);
        }

        List<String> authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isEmpty()) {
            return this.unauthenticated(exchange.getResponse(), null, HttpStatus.UNAUTHORIZED, 401);
        }

        String token = authHeader.get(0).replace("Bearer ", "");
        return authService.verify(token).flatMap(verifyResponse -> {
            VerifyResponse data = verifyResponse.getData();
            if (data.getIsValid()){
                return chain.filter(exchange); // dung map thi tra ve Mono<Mono<Void>>
            } else {
                return this.unauthenticated(exchange.getResponse(), data.getMessage(), data.getStatus(), data.getCode());
            }
        }).onErrorResume(throwable ->
                this.unauthenticated(exchange.getResponse(), null, HttpStatus.INTERNAL_SERVER_ERROR, 500)
        );
    }

    Mono<Void> unauthenticated(ServerHttpResponse response, String message, HttpStatus status, Integer code) {
        try {
            Response<Object> apiResponse = Response.builder()
                    .message(message)
                    .code(code)
                    .build();
            String body = objectMapper.writeValueAsString(apiResponse);
            response.setStatusCode(status);
            response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            return response.writeWith(
                    Mono.just(response.bufferFactory().wrap(body.getBytes()))
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
