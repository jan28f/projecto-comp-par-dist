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
import Comun.Interaccion;

public class Cliente {
    private static final String ipServidor = "localhost";
    private static final int puerto = 12345;

    public static String modoActual = "feed";
    public static ArrayList<Publicacion> publicacionesFeed = new ArrayList<>();
    public static int indicePublicacionActual = 0;
    public static ArrayList<Mensaje> buzonMensajes = new ArrayList<>();
    private static String ultimoAviso = "";

    public static void limpiarConsola() {
        for (int i = 0; i < 50; i++) {
            System.out.println();
        }
    }

    public static void repintarInterfaz() {
        limpiarConsola();

        if (modoActual.equals("feed")) {
            System.out.println("--- MODO FEED ---");
            System.out.println("Comandos: pub <ruta> <desc> | like | com <texto> | next | prev | chat | !salir");

            if (!ultimoAviso.isEmpty()) {
                System.out.println("AVISO: " + ultimoAviso);
                ultimoAviso = "";
            }

            if (publicacionesFeed.isEmpty()) {
                System.out.println("\nNo hay publicaciones en el feed. Usa 'pub' para crear una.");
            } else {
                if (indicePublicacionActual < 0) {
                    indicePublicacionActual = publicacionesFeed.size() - 1;
                }
                if (indicePublicacionActual >= publicacionesFeed.size()) {
                    indicePublicacionActual = 0;
                }
                publicacionesFeed.get(indicePublicacionActual).imprimirConsola();
            }
        } else if (modoActual.equals("chat")) {
            System.out.println("--- MODO CHAT ---");
            System.out.println("Comandos: dm <usuario> <mensaje> | feed | !salir");

            if (!ultimoAviso.isEmpty()) {
                System.out.println("AVISO: " + ultimoAviso);
                ultimoAviso = "";
            }

            System.out.println("\nBandeja de entrada:");
            if (buzonMensajes.isEmpty()) {
                System.out.println("Sin mensajes nuevos.");
            } else {
                for (Mensaje m : buzonMensajes) {
                    System.out.println("[" + m.getRemitente() + "]: " + m.getContenido());
                }
                buzonMensajes.clear();
            }
        }
        System.out.print("> ");
    }

    public static void main(String[] args) {
        Scanner escaneoInicial = new Scanner(System.in);
        System.out.print("Ingrese su nombre de usuario: ");
        String usuario = escaneoInicial.nextLine();

        try {
            System.out.println("Conectando al servidor...");
            Socket socket = new Socket(ipServidor, puerto);

            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            salida.writeObject(new SolicitudInicio(usuario, System.currentTimeMillis()));
            RespuestaInicio respuesta = (RespuestaInicio) entrada.readObject();

            if (respuesta.getEstado()) {
                publicacionesFeed = respuesta.getUltimasPublicaciones();
                if (!publicacionesFeed.isEmpty()) {
                    indicePublicacionActual = (int) (Math.random() * publicacionesFeed.size());
                }

                repintarInterfaz();

                (new Thread(new EscuchaCliente(entrada))).start();
                Scanner teclado = new Scanner(System.in);

                while (true) {
                    String comando = teclado.nextLine().trim();

                    if (comando.equalsIgnoreCase("!salir")) {
                        break;
                    }

                    if (modoActual.equals("feed")) {
                        if (comando.equalsIgnoreCase("chat")) {
                            modoActual = "chat";
                            repintarInterfaz();
                        }
                        else if (comando.equalsIgnoreCase("next")) {
                            indicePublicacionActual++;
                            repintarInterfaz();
                        }
                        else if (comando.equalsIgnoreCase("prev")) {
                            indicePublicacionActual--;
                            repintarInterfaz();
                        }
                        else if (comando.equalsIgnoreCase("like")) {
                            if (!publicacionesFeed.isEmpty()) {
                                long id = publicacionesFeed.get(indicePublicacionActual).getIdPublicacion();
                                salida.writeObject(new Interaccion("LIKE", id, usuario, ""));
                                salida.flush();
                            }
                        }
                        else if (comando.toLowerCase().startsWith("com ")) {
                            String[] partes = comando.split(" ", 2);
                            if (partes.length == 2 && !publicacionesFeed.isEmpty()) {
                                long id = publicacionesFeed.get(indicePublicacionActual).getIdPublicacion();
                                salida.writeObject(new Interaccion("COMENTARIO", id, usuario, partes[1]));
                                salida.flush();
                            }
                        }
                        else if (comando.toLowerCase().startsWith("pub ")) {
                            String[] partes = comando.split(" ", 3);
                            if (partes.length == 3) {
                                String rutaLimpia = partes[1].replace("\"", "");
                                String descripcion = partes[2];
                                try {
                                    File archivo = new File(rutaLimpia);
                                    if (archivo.exists() && !archivo.isDirectory()) {
                                        byte[] bytesArchivo = Files.readAllBytes(Path.of(rutaLimpia));
                                        Publicacion nuevaPublicacion = new Publicacion(usuario, descripcion, bytesArchivo, archivo.getName());
                                        salida.writeObject(nuevaPublicacion);
                                        salida.flush();
                                        ultimoAviso = "Publicacion enviada al servidor.";
                                    } else {
                                        ultimoAviso = "Error: El archivo no existe en la ruta especificada.";
                                    }
                                } catch (IOException e) {
                                    ultimoAviso = "Error: No se pudo leer el archivo.";
                                }
                                repintarInterfaz();
                            } else {
                                ultimoAviso = "Formato incorrecto. Uso: pub <ruta_archivo> <descripcion>";
                                repintarInterfaz();
                            }
                        }
                        else if (!comando.isEmpty()) {
                            ultimoAviso = "Comando invalido en FEED.";
                            repintarInterfaz();
                        }
                    }
                    else if (modoActual.equals("chat")) {
                        if (comando.equalsIgnoreCase("feed")) {
                            modoActual = "feed";
                            repintarInterfaz();
                        }
                        else if (comando.toLowerCase().startsWith("dm ")) {
                            String[] partes = comando.split(" ", 3);
                            if (partes.length == 3) {
                                Mensaje nuevoMensaje = new Mensaje(usuario, partes[1], partes[2]);
                                salida.writeObject(nuevoMensaje);
                                salida.flush();
                                ultimoAviso = "Mensaje enviado a " + partes[1];
                            } else {
                                ultimoAviso = "Formato incorrecto. Uso: dm <destinatario> <mensaje>";
                            }
                            repintarInterfaz();
                        }
                        else if (!comando.isEmpty()) {
                            ultimoAviso = "Comando invalido en CHAT.";
                            repintarInterfaz();
                        }
                    }
                }
            } else {
                System.out.println("Error: " + respuesta.getMensaje());
            }
        } catch (UnknownHostException e) {
            System.out.println("Error: No se pudo encontrar el servidor");
        } catch (ConnectException e) {
            System.out.println("Error: El servidor rechazo la conexion");
        } catch (SocketTimeoutException e) {
            System.out.println("Error: Se agoto el tiempo de espera");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error critico de red o clase desconocida");
        }
    }
}