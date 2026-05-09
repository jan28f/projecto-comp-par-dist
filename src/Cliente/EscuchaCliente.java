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
                    Cliente.buzonMensajes.add(msj);
                    Cliente.repintarInterfaz();
                }
                else if (obj instanceof Publicacion) {
                    Publicacion pubRecibida = (Publicacion) obj;

                    try {
                        String nombreDescarga = pubRecibida.getAutor() + "_" + pubRecibida.getNombreArchivo();
                        Path rutaArchivo = Path.of(nombreDescarga);
                        if (!Files.exists(rutaArchivo)) {
                            Files.write(rutaArchivo, pubRecibida.getArchivo());
                        }
                    } catch (IOException e) {
                        System.out.println("Error procesando archivo adjunto");
                    }

                    boolean actualizada = false;
                    for (int i = 0; i < Cliente.publicacionesFeed.size(); i++) {
                        if (Cliente.publicacionesFeed.get(i).getIdPublicacion() == pubRecibida.getIdPublicacion()) {
                            Cliente.publicacionesFeed.set(i, pubRecibida);
                            actualizada = true;
                            break;
                        }
                    }

                    if (!actualizada) {
                        Cliente.publicacionesFeed.add(pubRecibida);
                        Cliente.indicePublicacionActual = Cliente.publicacionesFeed.size() - 1;
                    }

                    Cliente.repintarInterfaz();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Conexion terminada con el servidor.");
        }
    }
}