import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;

public class AttendanceServer {
    private static final String DATA_FILE = "data.txt";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/students", new StudentHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server running at http://localhost:8000/");
    }

    static class StaticHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            File file = new File("public" + path);

            if (!file.exists()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] response = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().add("Content-Type", getMime(path));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        private String getMime(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".css")) return "text/css";
            return "text/plain";
        }
    }

    static class StudentHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String body = new String(exchange.getRequestBody().readAllBytes()).trim();

            switch (method) {
                case "GET":
                    send(exchange, 200, loadData());
                    break;
                case "POST":
                    addStudent(body);
                    send(exchange, 200, "Student added.");
                    break;
                case "PUT":
                    updateAttendance(body);
                    send(exchange, 200, "Updated.");
                    break;
                case "DELETE":
                    deleteStudent(body);
                    send(exchange, 200, "Deleted.");
                    break;
                default:
                    send(exchange, 405, "Method Not Allowed");
            }
        }

        private void send(HttpExchange exchange, int code, String response) throws IOException {
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private String loadData() throws IOException {
            File file = new File(DATA_FILE);
            if (!file.exists()) return "[]";
            List<String> lines = Files.readAllLines(file.toPath());
            return "[" + String.join(",", lines) + "]";
        }

        private void addStudent(String body) throws IOException {
            File file = new File(DATA_FILE);
            if (!file.exists()) file.createNewFile();

            String[] parts = body.split(":");
            String id = parts[0];
            String name = parts[1];

            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                if (line.contains("\"id\":\"" + id + "\"")) return;
            }

            String entry = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"presents\":0,\"absents\":0}", id, name);
            lines.add(entry);
            Files.write(file.toPath(), lines);
        }

        private void updateAttendance(String body) throws IOException {
            String[] parts = body.split(":");
            String id = parts[0];
            String type = parts[1];

            File file = new File(DATA_FILE);
            List<String> lines = Files.readAllLines(file.toPath());
            List<String> updated = new ArrayList<>();

            for (String line : lines) {
                if (line.contains("\"id\":\"" + id + "\"")) {
                    int p = Integer.parseInt(line.replaceAll(".*\"presents\":(\\d+).*", "$1"));
                    int a = Integer.parseInt(line.replaceAll(".*\"absents\":(\\d+).*", "$1"));
                    if (type.equals("present")) p++;
                    if (type.equals("absent")) a++;
                    String name = line.replaceAll(".*\"name\":\"([^\"]+)\".*", "$1");
                    line = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"presents\":%d,\"absents\":%d}", id, name, p, a);
                }
                updated.add(line);
            }

            Files.write(file.toPath(), updated);
        }

        private void deleteStudent(String id) throws IOException {
            File file = new File(DATA_FILE);
            List<String> lines = Files.readAllLines(file.toPath());
            lines.removeIf(line -> line.contains("\"id\":\"" + id + "\""));
            Files.write(file.toPath(), lines);
        }
    }
}
