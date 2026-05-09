package Cliente;

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
                    Cliente.historialChat.add("[" + msj.getRemitente() + "]: " + msj.getContenido());
                    Cliente.repintarInterfaz();
                }
                else if (obj instanceof Publicacion) {
                    Publicacion pubRecibida = (Publicacion) obj;

                    // Lógica de descarga silenciosa
                    try {
                        Path ruta = Path.of(pubRecibida.getAutor() + "_" + pubRecibida.getNombreArchivo());
                        if (!Files.exists(ruta)) Files.write(ruta, pubRecibida.getArchivo());
                    } catch (Exception ignored) {}

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
                    }

                    Cliente.repintarInterfaz();
                }
            }
        } catch (Exception e) {
            System.out.println("\n[!] Conexion perdida con el servidor.");
        }
    }
}