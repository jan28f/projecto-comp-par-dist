package Cliente;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Scanner;
import Comun.Mensaje;
import Comun.Publicacion;
import Comun.SolicitudInicio;
import Comun.RespuestaInicio;

public class Cliente {
    private static final String ip_servidor = "localhost";
    private static final int puerto = 12345;

    public static void main(String[] args) {
        String usuario = "AAA"; //Despues crear funcion de registro/login 

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
                System.out.println("\n--- FEED ACTUAL ---");
                ArrayList<Publicacion> historial = respuesta.getUltimasPublicaciones();
                if (historial.isEmpty()) {
                    System.out.println("No hay publicaciones aun. ¡Se el primero!");
                } else {
                    for (Publicacion pub : historial) {
                        System.out.println("[Nueva publicacion de " + pub.getAutor() + "]:");
                        System.out.println("----------------------------------------------");
                        try {
                            String nombreDescarga = pub.getAutor() + "_" + pub.getNombreArchivo();
                            Path rutaArchivo = Path.of(nombreDescarga);
                            Files.write(Path.of(nombreDescarga), pub.getArchivo());
                            String rutaClickeable = rutaArchivo.toAbsolutePath().toUri().toString();
                            System.out.println("Archivo guardado en: " + rutaClickeable);
                        }
                        catch (Exception e) {
                            System.out.println("Error: No se pudo obtener el contenido adjunto");
                        }
                        System.out.println("Descripcion: " +  pub.getDescripcion());
                        System.out.println("Fecha: " +  pub.getFechaFormateada());
                        System.out.println("----------------------------------------------");
                    }
                }

                (new Thread(new EscuchaCliente(entrada))).start();
                Scanner teclado = new Scanner(System.in);

                System.out.println("Comandos: ");
                System.out.println("    -Mensaje: dm <destinatario> <mensaje>");
                System.out.println("    -Publicar: pub <ruta_archivo> <descripcion>");
                System.out.println("    -Salir: !salir");
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

                    if (comando.toLowerCase().startsWith("pub")) {
                        String[] partes = comando.split(" ", 3);
                        if (partes.length == 3) {
                            String rutaArchivo = partes[1];
                            String descripcion = partes[2];

                            try {
                                File archivo = new File(rutaArchivo);
                                if (archivo.exists() && !archivo.isDirectory()) {
                                    byte[] bytesArchivo = Files.readAllBytes(Path.of(rutaArchivo));
                                    Publicacion nuevaPublicacion = new Publicacion(usuario, descripcion, bytesArchivo, archivo.getName());
                                    salida.writeObject(nuevaPublicacion);
                                    salida.flush();
                                }
                                else {
                                    System.out.println("Error: El archivo no existe o es un directorio");
                                }
                            }
                            catch (IOException e) {
                                System.out.println("Error No se pudo leer el archivo");
                            }
                        }
                        else {
                            System.out.println("Formato incorrecto: pub <ruta_archivo> <descripcion>");
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
