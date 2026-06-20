package Cliente;

import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import Comun.DM.InvitacionGrupo;
import Comun.DM.Mensaje;
import Comun.Publiaciones.Publicacion;

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
                    if (msj.getEsGrupo()) {
                        Cliente.historialChat.add("[Grupo " + msj.getDestinatario() + "] " + msj.getRemitente() + ": " + msj.getContenido());
                    }
                    else {
                        Cliente.historialChat.add("[DM de " + msj.getRemitente() + "]: " + msj.getContenido());
                    }
                    Cliente.repintarInterfaz();
                }
                else if (obj instanceof InvitacionGrupo) {
                    InvitacionGrupo inv = (InvitacionGrupo) obj;
                    Cliente.historialChat.add("[SISTEMA]: " + inv.getInvitadoPor() + " te ha invitado al grupo '" + inv.getNombreGrupo() + "'.");
                    Cliente.repintarInterfaz();
                }
                else if (obj instanceof Publicacion) {
                    Publicacion pubRecibida = (Publicacion) obj;

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
                    Cliente.publicacionesFeed.sort(Comparator.comparing(Publicacion::getLamport).thenComparing(Publicacion::getIdNodoOrigen));
                    Cliente.repintarInterfaz();
                }
            }
        } catch (Exception e) {
            System.out.println("\n[!] Conexion perdida con el servidor.");
        }
    }
}