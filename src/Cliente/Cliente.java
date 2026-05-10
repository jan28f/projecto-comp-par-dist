package Cliente;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;

import Comun.DM.AccionGrupo;
import Comun.DM.Mensaje;
import Comun.DM.SolicitudGrupo;
import Comun.Publiaciones.Interaccion;
import Comun.Publiaciones.Publicacion;
import Comun.Sesion.RespuestaInicio;
import Comun.Sesion.SolicitudInicio;

public class Cliente {
    private static final String ipServidor = "26.246.127.231";
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
            System.out.println("Comandos:\n" +
                                "   Subir publicacion:                 pub <ruta> <texto>\n" +
                                "   Dar me gusta a publicacion actual: like\n" +
                                "   Comentar en publicacion actua:     com <texto>\n" +
                                "   Ver publicacion previa/siguiente:  next/prev\n" +
                                "   Mensajes:                          chat\n" +
                                "   Salir:                             !salir");
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
            System.out.println("Comandos:\n" +
                               "    Mensaje privado:                   dm <usuario> <mensaje>\n" +
                               "    Mensaje grupo:                     gdm <grupo> <mensaje>\n" +
                               "    Crear grupo:                       creargrupo <nombre_grupo> <integrante1,integrante2>\n" +
                               "    Aceptar invitacion a grupo:        aceptar <nombre_grupo>\n" +
                               "    Rechazar invitacion a grupo:       rechazar <nombre_grupo>\n" +
                               "    Abandonar grupo:                   salirgrupo <nombre_grupo>\n" +
                               "    Regresar al feed de publicaciones: feed\n" +
                               "    Finalizar sesion:                  !salir");
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
                                salida.writeObject(new Mensaje(nombreUsuario, dest, false, cont));
                                salida.flush();
                                historialChat.add("[Tu -> " + dest + "]: " + cont);
                            } else {
                                ultimoAviso = "Formato incorrecto. Uso: dm <destinatario> <mensaje>";
                            }
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("creargrupo ")) {
                            String[] partes = entradaUsuario.split(" ", 3);
                            if (partes.length == 3) {
                                String nombreGrupo = partes[1];
                                String[] arrayIntegrantes = partes[2].split(",");
                                ArrayList<String> integrantes = new ArrayList<>();
                                for (String i : arrayIntegrantes) {
                                    integrantes.add(i.trim());
                                }
                                salida.writeObject(new SolicitudGrupo(nombreGrupo, integrantes));
                                salida.flush();
                                ultimoAviso = "Creando grupo...";
                            } else {
                                ultimoAviso = "Formato incorrecto. Uso: creargrupo <nombre> <usr1,usr2>";
                            }
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("gdm")) {
                            String[] partes = entradaUsuario.split(" ", 3);
                            if (partes.length == 3) {
                                String nombreGrupo = partes[1];
                                String cont = partes[2];
                                salida.writeObject(new Mensaje(nombreUsuario, nombreGrupo, true, cont));
                                salida.flush();
                                historialChat.add("[Tu -> Grupo " + nombreGrupo + "]: " + cont);
                            } else {
                                ultimoAviso = "Formato incorrecto. Uso: gdm <grupo> <mensaje>";
                            }
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("aceptar ")) {
                            String nombreGrupo = entradaUsuario.substring(8).trim();
                            salida.writeObject(new AccionGrupo("aceptar", nombreGrupo));
                            salida.flush();
                            ultimoAviso = "Has aceptado la invitacion al grupo " + nombreGrupo;
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("rechazar ")) {
                            String nombreGrupo = entradaUsuario.substring(9).trim();
                            salida.writeObject(new AccionGrupo("rechazar", nombreGrupo));
                            salida.flush();
                            ultimoAviso = "Has rechazado la invitacion al grupo " + nombreGrupo;
                            repintarInterfaz();
                        }
                        else if (entradaUsuario.toLowerCase().startsWith("salirgrupo ")) {
                            String nombreGrupo = entradaUsuario.substring(11).trim();
                            salida.writeObject(new AccionGrupo("salir", nombreGrupo));
                            salida.flush();
                            ultimoAviso = "Has salido del grupo " + nombreGrupo;
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