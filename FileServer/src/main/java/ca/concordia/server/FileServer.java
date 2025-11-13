package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        FileSystemManager fsManager = new FileSystemManager(fileSystemName,
                10*128 );
        this.fsManager = fsManager;
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");
                        String command = parts[0].toUpperCase();

                        try {
                            switch (command) {
                                case "CREATE":
                                    if (parts.length != 2) {
                                        writer.println("ERROR: CREATE command requires exactly 1 argument.");
                                        break;
                                    }
                                    fsManager.createFile(parts[1]);
                                    writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                    break;

                                case "READ":
                                    if (parts.length != 2) {
                                        writer.println("ERROR: READ command requires exactly 1 argument.");
                                        break;
                                    }
                                    byte[] data = fsManager.readFile(parts[1]);
                                    String content = new String(data);
                                    writer.println("SUCCESS: " + content);
                                    break;

                                case "WRITE":
                                    if (parts.length < 3) {
                                        writer.println("ERROR: WRITE command requires at least 2 arguments.");
                                        break;
                                    }
                                    String writecontent = "";

                                    for (int i = 2; i < parts.length; i++){
                                        writecontent += parts[i];
                                        if(i < parts.length -1){
                                            writecontent += " ";
                                        }
                                    }
                                    byte[] writedata = writecontent.getBytes();
                                    fsManager.writeFile(parts[1], writedata);
                                    writer.println("SUCCESS: File '" + parts[1] + "' written.");
                                    break;

                                case "LIST":
                                    if (parts.length != 1) {
                                        writer.println("ERROR: LIST command does not take any arguments.");
                                        break;
                                    }
                                    String filenames = fsManager.listFiles();
                                    writer.println("SUCCESS: " + filenames);
                                    break;

                                case "DELETE":
                                    if (parts.length != 2) {
                                        writer.println("ERROR: DELETE command requires exactly 1 argument.");
                                        break;
                                    }
                                    fsManager.deleteFile(parts[1]);
                                    writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                    break;

                                case "QUIT":
                                    writer.println("SUCCESS: Disconnecting.");
                                    return;

                                default:
                                    writer.println("ERROR: Unknown command.");
                                    break;
                            }
                        } catch (IllegalArgumentException e) {
                            writer.println("ERROR: " + e.getMessage());
                        } catch (Exception e) {
                            writer.println("ERROR: An unexpected error occurred: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error handling client: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not start server on port " + port);
            e.printStackTrace();
        }
    }

}
