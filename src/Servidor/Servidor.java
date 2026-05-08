package Servidor;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
    private static final int puerto = 12345;

    public static void main(String[] args) throws IOException {
        System.out.println("Iniciando servidor");

        try {
            ServerSocket servidor = new ServerSocket(puerto);
            System.out.println("Servidor iniciado, escuchando desde el puerto " + puerto);

            while (true) {
                Socket cliente = servidor.accept();

                (new Thread(new ManejoCliente(cliente))).start();
            }
        }
        catch (BindException e) {
            System.out.println("Error: Puerto ya en uso");
        }
        catch (IllegalArgumentException e) {
            System.out.println("Error: Puerto fuera de rango valido");
        }
        catch (IOException e) {
            System.out.println("Error al iniciar el servidor");
        }
    }
}