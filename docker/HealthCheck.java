import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dependency-free container health probe. The runtime image is a JRE (no curl/wget), so the
 * Dockerfile HEALTHCHECK and the compose healthcheck invoke this compiled class instead.
 *
 * <p>Exits 0 only when the actuator endpoint returns HTTP 200 with a body containing
 * "status":"UP"; any other outcome exits non-zero so Docker marks the container unhealthy.
 */
public final class HealthCheck {

  private HealthCheck() {}

  public static void main(String[] args) {
    String url = args.length > 0 ? args[0] : "http://localhost:8080/actuator/health";
    try {
      HttpClient client =
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      boolean up = response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
      System.exit(up ? 0 : 1);
    } catch (Exception e) {
      System.exit(1);
    }
  }
}
