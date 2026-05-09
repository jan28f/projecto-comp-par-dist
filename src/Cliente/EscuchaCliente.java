package Cliente;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import Comun.Mensaje;
import Comun.Publicacion;

public class EscuchaCliente implements Runnable {
    private final ObjectInputStream entrada;

    public EscuchaCliente(ObjectInputStream entrada) {
        this.entrada = entrada;
    }

    public void run() {
        try {
            while (true) {
                Object obj = entrada.readObject();

                if (obj instanceof Mensaje) {
                    Mensaje msj = (Mensaje) obj;
                    System.out.println("\n[DM de " + msj.getRemitente() + "]: " + msj.getContenido());
                    System.out.print("> ");
                }
                else if (obj instanceof Publicacion) {
                    Publicacion pub = (Publicacion) obj;
                    System.out.println("[Nueva publicacion de " + pub.getAutor() + "]:");
                    System.out.println("----------------------------------------------");
                    try {
                        String nombreDescarga = pub.getAutor() + "_" + pub.getNombreArchivo();
                        Files.write(Path.of(nombreDescarga), pub.getArchivo());
                    }
                    catch (Exception e) {
                        System.out.println("Error: No se pudo obtener el contenido adjunto");
                    }
                    System.out.println("Descripcion: " +  pub.getDescripcion());
                    System.out.println("Fecha: " +  pub.getFechaPublicacion());
                    System.out.println("----------------------------------------------");
                    System.out.print("> ");
                }
            }
        } catch (IOException e) {
            System.out.println("Error: " + e);
        } catch (ClassNotFoundException e) {
            System.out.println("Error: Clase del objeto desconocido");
        }
    }
}
