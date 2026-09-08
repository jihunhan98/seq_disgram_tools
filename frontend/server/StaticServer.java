import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal static file server for the frontend.
 *
 * The frontend is plain HTML/CSS/JS with no build step, so all it needs is
 * something to hand the files to the browser on port 5001. This uses only the
 * JDK, so it runs straight from source:
 *
 *     java frontend/server/StaticServer.java [port] [root-directory]
 */
public class StaticServer {

    private static final int DEFAULT_PORT = 5001;

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("mjs", "application/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("map", "application/json; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"));

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path root = (args.length > 1 ? Path.of(args[1]) : defaultRoot())
                .toAbsolutePath().normalize();

        if (!Files.isDirectory(root)) {
            System.err.println("Frontend directory not found: " + root);
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handle(exchange, root));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.printf("Frontend served from %s%n", root);
        System.out.printf("Listening on http://localhost:%d%n", port);
    }

    /** When run as `java frontend/server/StaticServer.java`, serve ../ of this file. */
    private static Path defaultRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path candidate : new Path[] { cwd, cwd.resolve("frontend"), cwd.getParent() }) {
            if (candidate != null && Files.isRegularFile(candidate.resolve("index.html"))) {
                return candidate;
            }
        }
        return cwd;
    }

    private static void handle(HttpExchange exchange, Path root) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes());
                return;
            }

            String rawPath = exchange.getRequestURI().getPath();
            if (rawPath.isEmpty() || "/".equals(rawPath)) {
                rawPath = "/index.html";
            }

            Path target = root.resolve(rawPath.substring(1)).normalize();

            // Refuse anything that escapes the served directory.
            if (!target.startsWith(root)) {
                respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden".getBytes());
                return;
            }
            if (Files.isDirectory(target)) {
                target = target.resolve("index.html");
            }
            if (!Files.isRegularFile(target)) {
                respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes());
                return;
            }

            // The vendored library is immutable; app files must not be cached
            // so an edit shows up on a plain reload.
            String cacheControl = target.startsWith(root.resolve("vendor"))
                    ? "public, max-age=86400"
                    : "no-cache";
            exchange.getResponseHeaders().set("Cache-Control", cacheControl);

            byte[] body = Files.readAllBytes(target);
            respond(exchange, 200, contentType(target), body);
        } catch (IOException | RuntimeException e) {
            System.err.println("Failed to serve " + exchange.getRequestURI() + ": " + e);
            respond(exchange, 500, "text/plain; charset=utf-8", "Internal Server Error".getBytes());
        }
    }

    private static String contentType(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);

        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
