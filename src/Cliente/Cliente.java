package Cliente;

import java.io.IOException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Scanner;

import Comun.Mensaje;
import Comun.SolicitudInicio;
import Comun.RespuestaInicio;

public class Cliente {
    private static final String ip_servidor = "localhost";
    private static final int puerto = 12345;

    public static void main(String[] args) throws IOException {
        String usuario = "Suisei";

        try {
            System.out.println("Conectando al servidor...");
            Socket socket = new Socket(ip_servidor, puerto);
            // NO voltear, se bloquea
            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            salida.writeObject(new SolicitudInicio(usuario, System.currentTimeMillis()));
            RespuestaInicio respuesta = (RespuestaInicio) entrada.readObject();

            if (respuesta.getEstado()) {
                System.out.println("Exito: " + respuesta.getMensaje());
                (new Thread(new EscuchaCliente(entrada))).start();
                Scanner teclado = new Scanner(System.in);

                System.out.println("Comandos: ");
                System.out.println("    -Mensaje: dm <destinatario> <mensaje> o '!salir'");
                while (true) {
                    System.out.print("> ");
                    String comando = teclado.nextLine();

                    if (comando.equalsIgnoreCase("!salir")) {
                        break;
                    }

                    if (comando.toLowerCase().startsWith("dm")) {
                        String[] partes = comando.split(" ", 3);

                        if (partes.length == 3) {
                            String destinatario = partes[1];
                            String texto =  partes[2];
                            Mensaje nuevoMensaje = new Mensaje(usuario, destinatario, texto);
                            salida.writeObject(nuevoMensaje);
                        }
                        else {
                            System.out.println("Formato incorrecto: dm <destinatario> <mensaje>");
                        }
                    }
                }
            }
            else {
                System.out.println("Error: " + respuesta.getMensaje());
            }
        }
        catch (UnknownHostException e) {
            System.out.println("Error: No se pudo encontrar el servidor");
        }
        catch (ConnectException e) {
            System.out.println("Error: El servidor rechazo la conexion");
        }
        catch (SocketTimeoutException e) {
            System.out.println("Error: Se agoto el tiempo de espera para conectar");
        }
        catch (IOException e) {
            System.out.println("Error: No se pudo iniciar el socket");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
