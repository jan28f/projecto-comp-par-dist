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
    public static String nombreUsuario = "";
    public static ArrayList<Publicacion> publicacionesFeed = new ArrayList<>();
    public static int indicePublicacionActual = 0;
    public static ArrayList<String> historialChat = new ArrayList<>();
    private static String ultimoAviso = "";

    public static synchronized void limpiarConsola() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }

    public static synchronized void repintarInterfaz() {
        limpiarConsola();
        System.out.println("==========================================================");
        System.out.println(" USUARIO: " + nombreUsuario + " | MODO: " + modoActual.toUpperCase());
        System.out.println("==========================================================");

        if (modoActual.equals("feed")) {
            System.out.println("Comandos: pub <ruta> <desc> | like | com <texto> | next | prev | chat | !salir");
            if (!ultimoAviso.isEmpty()) {
                System.out.println("\n[!] " + ultimoAviso);
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
            System.out.println("Comandos: dm <usuario> <mensaje> | feed | !salir");
            if (!ultimoAviso.isEmpty()) {
                System.out.println("\n[!] " + ultimoAviso);
                ultimoAviso = "";
            }

            System.out.println("\n--- CONVERSACIONES ---");
            if (historialChat.isEmpty()) {
                System.out.println("No hay mensajes en esta sesion.");
            } else {
                for (String linea : historialChat) {
                    System.out.println(linea);
                }
            }
            System.out.println("-----------------------");
        }
        System.out.print(nombreUsuario + "> ");
    }

    public static void main(String[] args) {
        Scanner escaneoInicial = new Scanner(System.in);
        System.out.print("Ingrese su nombre de usuario: ");
        nombreUsuario = escaneoInicial.nextLine();

        try {
            Socket socket = new Socket(ipServidor, puerto);
            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

            salida.writeObject(new SolicitudInicio(nombreUsuario, System.currentTimeMillis()));
            RespuestaInicio respuesta = (RespuestaInicio) entrada.readObject();

            if (respuesta.getEstado()) {
                publicacionesFeed = respuesta.getUltimasPublicaciones();
                if (!publicacionesFeed.isEmpty()) {
                    indicePublicacionActual = 0;
                }

                repintarInterfaz();
                (new Thread(new EscuchaCliente(entrada))).start();
                Scanner teclado = new Scanner(System.in);

                while (true) {
                    String entradaUsuario = teclado.nextLine().trim();

                    if (entradaUsuario.equalsIgnoreCase("!salir")) {
                        break;
                    }

                    if (modoActual.equals("feed")) {
                        if (entradaUsuario.equalsIgnoreCase("chat")) {
                            modoActual = "chat";
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.equalsIgnoreCase("next")) {
                            indicePublicacionActual++;
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.equalsIgnoreCase("prev")) {
                            indicePublicacionActual--;
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.equalsIgnoreCase("like")) {
                            if (!publicacionesFeed.isEmpty()) {
                                long id = publicacionesFeed.get(indicePublicacionActual).getIdPublicacion();
                                salida.writeObject(new Interaccion("LIKE", id, nombreUsuario, ""));
                                salida.flush();
                            } else {
                                ultimoAviso = "No hay publicaciones para dar like.";
                                repintarInterfaz();
                            }
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("com ")) {
                            String[] partes = entradaUsuario.split(" ", 2);
                            if (partes.length == 2 && !publicacionesFeed.isEmpty()) {
                                long id = publicacionesFeed.get(indicePublicacionActual).getIdPublicacion();
                                salida.writeObject(new Interaccion("COMENTARIO", id, nombreUsuario, partes[1]));
                                salida.flush();
                            } else {
                                ultimoAviso = "Formato incorrecto o feed vacio. Uso: com <texto>";
                                repintarInterfaz();
                            }
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("pub ")) {
                            String[] partes = entradaUsuario.split(" ", 3);
                            if (partes.length == 3) {
                                String rutaLimpia = partes[1].replace("\"", "");
                                try {
                                    File archivo = new File(rutaLimpia);
                                    if (archivo.exists()) {
                                        byte[] bytes = Files.readAllBytes(archivo.toPath());
                                        salida.writeObject(new Publicacion(nombreUsuario, partes[2], bytes, archivo.getName()));
                                        salida.flush();
                                        ultimoAviso = "Publicacion compartida.";
                                    } else {
                                        ultimoAviso = "Archivo no encontrado en la ruta especificada.";
                                    }
                                } catch (IOException e) {
                                    ultimoAviso = "Error al leer archivo. Revise los permisos.";
                                }
                            } else {
                                ultimoAviso = "Formato incorrecto. Uso: pub <ruta_archivo> <descripcion>";
                            }
                            repintarInterfaz();
                        }
                        else if (!entradaUsuario.isEmpty()) {
                            ultimoAviso = "Comando invalido en FEED.";
                            repintarInterfaz();
                        } else {
                            repintarInterfaz();
                        }
                    }
                    else if (modoActual.equals("chat")) {
                        if (entradaUsuario.equalsIgnoreCase("feed")) {
                            modoActual = "feed";
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("dm ")) {
                            String[] partes = entradaUsuario.split(" ", 3);
                            if (partes.length == 3) {
                                String dest = partes[1];
                                String cont = partes[2];
                                salida.writeObject(new Mensaje(nombreUsuario, dest, cont));
                                salida.flush();
                                historialChat.add("[Tu -> " + dest + "]: " + cont);
                            } else {
                                ultimoAviso = "Formato incorrecto. Uso: dm <destinatario> <mensaje>";
                            }
                            repintarInterfaz();
                        }
                        else if (!entradaUsuario.isEmpty()) {
                            ultimoAviso = "Comando invalido en CHAT.";
                            repintarInterfaz();
                        } else {
                            repintarInterfaz();
                        }
                    }
                }
            }
            socket.close();
        } catch (Exception e) {
            System.out.println("Error en cliente de red.");
        }
    }
}