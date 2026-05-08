package Cliente;

import java.io.IOException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import Comun.PeticionConexion;
import Comun.RespuestaConexion;

public class Cliente {
    private static String ip_servidor = "localhost";
    private static int puerto = 12345;

    public static void main(String[] args) throws IOException {
        String usuario = "Suisei";

        try {
            System.out.println("Conectando al servidor...");
            Socket socket = new Socket(ip_servidor, puerto);
            // NO voltear, se bloquea
            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            salida.writeObject(new PeticionConexion(usuario, System.currentTimeMillis()));
            RespuestaConexion respuestaConexion = (RespuestaConexion) entrada.readObject();

            
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
