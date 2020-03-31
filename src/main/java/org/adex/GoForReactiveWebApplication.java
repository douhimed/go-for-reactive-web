package org.adex;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class GoForReactiveWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(GoForReactiveWebApplication.class, args);
	}

	@Bean
	public RouterFunction<ServerResponse> routes(RequestsHandler handler) {
		return route().GET("/api-v2/users", handler::handleUsersRequests)
				.GET("/sse-v2/{name}", handler::handleEventRequests).build();
	}

}

@Component
@RequiredArgsConstructor
class RequestsHandler {

	private final EventServiceMessages eventServiceMessages;
	private final UserRepository userRepository;

	public Mono<ServerResponse> handleEventRequests(ServerRequest serverRequest) {
		return ok().contentType(MediaType.TEXT_EVENT_STREAM).body(
				eventServiceMessages.generateMessages(new Request(serverRequest.pathVariable("name"))), Response.class);
	}

	public Mono<ServerResponse> handleUsersRequests(ServerRequest serverRequest) {
		return ok().body(userRepository.findAll(), User.class);
	}

}

@RestController
@RequiredArgsConstructor
class UserRestController {

	private final UserRepository userRepository;
	private final EventServiceMessages eventServiceMessages;

	@GetMapping("/api/users")
	public Publisher<User> getUUSers() {
		return this.userRepository.findAll();
	}

	@GetMapping(value = "/sse/{name}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Publisher<Response> getResponses(@PathVariable String name) {
		return this.eventServiceMessages.generateMessages(new Request(name));
	}

}

@Component
class EventServiceMessages {

	public Flux<Response> generateMessages(Request request) {
		return Flux.fromStream(Stream.generate(() -> "Hello " + request.getName() + " @ " + Instant.now()))
				.map(message -> new Response(message)).delayElements(Duration.ofSeconds(1));
	}

}

@Component
@RequiredArgsConstructor
@Log4j2
class Initializer {

	private final UserRepository userRepository;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		var saved = Flux.just("Ahmed", "John", "Doe", "Salma").map(name -> new User(null, name))
				.flatMap(this.userRepository::save);

		this.userRepository.deleteAll().thenMany(saved).thenMany(this.userRepository.findAll()).subscribe(log::info);
	}

}

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
class User {

	@Id
	private String id;
	private String name;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class Request {
	private String name;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class Response {
	private String message;
}

@Repository
interface UserRepository extends ReactiveCrudRepository<User, String> {

}