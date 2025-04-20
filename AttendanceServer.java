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
                    sendResponse(exchange, 200, loadData());
                    break;
                case "POST":
                    addStudent(body);
                    sendResponse(exchange, 200, "Student added.");
                    break;
                case "PUT":
                    updateAttendance(body);
                    sendResponse(exchange, 200, "Attendance updated.");
                    break;
                case "DELETE":
                    deleteStudent(body);
                    sendResponse(exchange, 200, "Student deleted.");
                    break;
                default:
                    sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(status, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

        private String loadData() throws IOException {
            File file = new File(DATA_FILE);
            if (!file.exists()) return "[]";
            List<String> lines = Files.readAllLines(file.toPath());
            return "[" + String.join(",", lines) + "]";
        }

        private void addStudent(String name) throws IOException {
            File file = new File(DATA_FILE);
            if (!file.exists()) file.createNewFile();

            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                if (line.contains("\"name\":\"" + name + "\"")) return;
            }

            String entry = String.format("{\"name\":\"%s\",\"presents\":0,\"absents\":0}", name);
            lines.add(entry);
            Files.write(file.toPath(), lines);
        }

        private void updateAttendance(String body) throws IOException {
            String[] parts = body.split(":");
            String name = parts[0];
            String type = parts[1]; // "present" or "absent"

            File file = new File(DATA_FILE);
            List<String> lines = Files.readAllLines(file.toPath());
            List<String> updated = new ArrayList<>();

            for (String line : lines) {
                if (line.contains("\"name\":\"" + name + "\"")) {
                    int present = Integer.parseInt(line.replaceAll(".*\"presents\":(\\d+).*", "$1"));
                    int absent = Integer.parseInt(line.replaceAll(".*\"absents\":(\\d+).*", "$1"));
                    if (type.equals("present")) present++;
                    if (type.equals("absent")) absent++;
                    line = String.format("{\"name\":\"%s\",\"presents\":%d,\"absents\":%d}", name, present, absent);
                }
                updated.add(line);
            }

            Files.write(file.toPath(), updated);
        }

        private void deleteStudent(String name) throws IOException {
            File file = new File(DATA_FILE);
            List<String> lines = Files.readAllLines(file.toPath());
            lines.removeIf(line -> line.contains("\"name\":\"" + name + "\""));
            Files.write(file.toPath(), lines);
        }
    }
}
